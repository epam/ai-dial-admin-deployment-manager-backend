# Phase 1 Data Model: Stop Image Build

## Entities touched

### `ImageDefinitionEntity` (existing)

No structural change. The existing `buildStatus` column now admits one additional value.

| Field | Type | Change |
|---|---|---|
| `buildStatus` | `PersistenceImageStatus` (mapped to `VARCHAR(32)`) | Admits new value `BUILD_STOPPED` |
| `logs` | existing | Preserved through stop (FR-006) — no writes from stop flow |
| `imageName` | existing | Untouched on stop |
| `builtAtMs` | existing | Untouched on stop |

No new columns. No new table. No Flyway migration.

### `ImageStatus` enum (domain) — `model/ImageStatus.java`

```java
@Getter
@RequiredArgsConstructor
public enum ImageStatus {
    NOT_BUILT(false),
    BUILDING(false),
    BUILD_FAILED(true),
    BUILD_SUCCESSFUL(true),
    BUILD_STOPPED(true);        // NEW — terminal, user-initiated interruption

    private final boolean isFinal;
}
```

Insertion position: after `BUILD_SUCCESSFUL` (end of enum). Placing it last avoids shifting any existing `ordinal()` values — relevant for any persistence or comparison code that may assume ordinals are stable (even though the project uses `EnumType.STRING`, conservative placement eliminates risk).

### `PersistenceImageStatus` enum (persistence) — `dao/entity/PersistenceImageStatus.java`

```java
public enum PersistenceImageStatus {
    NOT_BUILT,
    BUILDING,
    BUILD_FAILED,
    BUILD_SUCCESSFUL,
    BUILD_STOPPED               // NEW
}
```

The `PersistenceImageStatusMapper` (or whichever MapStruct mapper bridges `ImageStatus ↔ PersistenceImageStatus`) maps by name — no mapper code change required as long as both enums stay aligned.

## State machine

```
NOT_BUILT
   │
   │ POST /api/v1/images/builds (ImageBuildRunner.buildImage)
   ▼
BUILDING ────────────────────────────────────┐
   │                                         │
   │ pipeline success   │ pipeline failure   │ DELETE /api/v1/images/builds/{id}
   ▼                    ▼                    ▼
BUILD_SUCCESSFUL    BUILD_FAILED         BUILD_STOPPED
   │                    │                    │
   │ POST (rebuild) — rejected for      │ POST (rebuild) — accepted
   │ BUILD_SUCCESSFUL and BUILDING;      │
   │ accepted for BUILD_FAILED and       │
   │ BUILD_STOPPED                       │
   ▼                                        ▼
BUILDING (new pipeline attempt) ... BUILDING (new pipeline attempt) ...
```

Transitions into terminal states are **conditional**: all three (`BUILD_SUCCESSFUL`, `BUILD_FAILED`, `BUILD_STOPPED`) MUST only apply if the row is currently `BUILDING`. This is the race-safety decision from Phase 0 R1.

### Transition guards (service-level, Envers-safe)

All three transitions share one pattern: within a `@Transactional` service method, load the entity with `PESSIMISTIC_WRITE`, check the current status is `BUILDING`, apply the setter(s), and `saveAndFlush`. `saveAndFlush` on a managed entity triggers Hibernate Envers and the `AuditRevisionListener` so FR-011 is satisfied automatically. A new repository method `findByIdForUpdate(UUID id)` annotated with `@Lock(LockModeType.PESSIMISTIC_WRITE)` provides the row lock — translated by JPA to `SELECT ... FOR UPDATE` on all three supported vendors.

| Method | Precondition | Action | Return |
|---|---|---|---|
| `ImageDefinitionService.completeBuildSuccessfully(id, imageName, builtAt)` | none (unconditional write after `findByIdForUpdate`) | Set `buildStatus := BUILD_SUCCESSFUL`, `imageName := :imageName`, `builtAtMs := :builtAt`; `saveAndFlush` | void |
| `ImageDefinitionService.failBuild(id, errorLog)` | `buildStatus != BUILD_STOPPED` after `findByIdForUpdate` | Set `buildStatus := BUILD_FAILED`, append `errorLog`; `saveAndFlush` | void; log `DEBUG` and return when pre-check sees `BUILD_STOPPED` (pipeline lost race with stop) |
| `ImageDefinitionService.stopBuild(id)` | `buildStatus == BUILDING` after `findByIdForUpdate` | Set `buildStatus := BUILD_STOPPED`; `saveAndFlush` — **no mutation of logs, imageName, or builtAtMs** (preserves FR-006) | `boolean` — `true` on success, `false` if pre-check saw non-`BUILDING` (caller re-reads and throws `ImageBuildNotInProgressException`) |

Why the asymmetry: `stopBuild` only ever advances `BUILDING → BUILD_STOPPED` because the controller guards against every other starting state. `failBuild` short-circuits on `BUILD_STOPPED` so a pipeline failure path triggered by the stop action (e.g. the pipeline's catch-all after `JobExternallyDeletedException`) cannot overwrite the admin-recorded stop with `BUILD_FAILED`. `completeBuildSuccessfully` carries **no guard**: if the pipeline's Job had already succeeded (image physically in the registry) and the pipeline-thread's completion commit lands after stop's, the built artifact is adopted as `BUILD_SUCCESSFUL` rather than orphaned. This matches the Edge Case "Stop requested as the build is completing" in `spec.md` and is a deliberate narrow-race trade-off.

### Cluster-resource cleanup on stop

`ImageBuildStopService.stopBuild` does not rely on the scheduled `DisposableResourceCleaner` tick. Sequence:

1. `JobRunner.deleteJob(id)` — synchronous delete + foreground propagation + wait-until-gone on the backing Job (FR-004).
2. `DisposableResourceCleaner.cleanTemporaryByGroupId(id)` — synchronous cleanup of every `TEMPORARY`-state disposable resource for the groupId: secrets, configmaps, Cilium network policy, the already-deleted Job (no-op), and the MCP-LOCAL intermediate base image if present. Final `STABLE` artifacts are not touched.
3. **Post-condition check**: re-query `DisposableResourceManager.getAllTemporaryByGroupId(...)`; if any rows remain (the cleaner swallows per-resource exceptions), throw `ImageBuildStopFailedException` and leave status `BUILDING` for admin retry.
4. `ImageDefinitionService.stopBuild(id)` — conditional update to `BUILD_STOPPED`.

This matches the pipeline's own `finally { cleanTemporaryByGroupId }` pattern and ensures a rebuild immediately after a successful stop doesn't hit "already exists" errors on `createSecret`/`createConfigMap`/`createCiliumNetworkPolicy`.

Why pessimistic rather than optimistic: `ImageDefinitionEntity` has no `@Version` column (only a domain-meaning `version` string at line 46 of the entity), so `OptimisticLockException` is not available. `PESSIMISTIC_WRITE` serializes the read-modify-write sections of the stop thread and the pipeline thread on the same row; hold time is sub-millisecond because no I/O happens inside the transaction.

Why not JPQL bulk `UPDATE ... WHERE buildStatus = BUILDING`: would be atomic in a single round-trip but bypasses Envers (see research.md R1), causing the 014-auditing integration to silently drop entries for all three lifecycle transitions — a blocking regression.

### Validation rules (tied to FR-002)

At the service-orchestration layer, **before** any cluster-side cleanup, the flow reads the current `buildStatus`:

- `buildStatus == null` (i.e., the image definition has never been built): throw `ImageBuildNotInProgressException` with current status rendered as `NOT_BUILT` (normalize null to `NOT_BUILT` in the error message).
- `buildStatus != BUILDING`: throw `ImageBuildNotInProgressException` carrying the actual current status.
- `buildStatus == BUILDING`: proceed with cluster cleanup.

The exception is mapped to HTTP 400 with message format `"Image build is not in progress (current status: <STATUS>)"` (per Phase 0 R4).

Image-definition-not-found is handled by the existing `ImageDefinitionService.getImageDefinition(id)` lookup which throws a `ResourceNotFoundException` (or equivalent) → 404 — no new error plumbing.

## Effect on existing queries / reads

- `ImageBuildDetailsDto` — emits `status` verbatim from `ImageStatus`. Once the enum gains `BUILD_STOPPED`, the DTO surfaces it automatically via the existing MapStruct mapper. No DTO change.
- `GET /api/v1/images/builds/{id}/status` (SSE) — polling reads `ImageStatus.isFinal`; `BUILD_STOPPED.isFinal = true` → stream closes normally.
- `GET /api/v1/images/builds/{id}/logs` (SSE) — same: terminal-status handling is polymorphic over `isFinal`.

## Effect on auditing

Rely on the existing 014-auditing integration for entity lifecycle. The `image_definition.build_status` column is already covered (assumption from spec Clarification: "Audit logging infrastructure introduced by the recently delivered auditing feature is reused"). The stop transition produces one more value in the same audited column.

If 014-auditing uses explicit event emission rather than Envers / attribute listeners, mirror whichever call `completeBuildSuccessfully` / `failBuild` make — see Phase 0 R3. This is verified in the Phase 2 tasks, not in the data model.
