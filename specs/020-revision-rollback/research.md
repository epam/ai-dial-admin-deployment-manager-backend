# Research: Revision Rollback

**Feature**: 020-revision-rollback
**Phase**: 0 (resolve open questions before design)

This document records the decisions taken to resolve open questions surfaced during planning. There are **no remaining `NEEDS CLARIFICATION` markers** — every open question listed below now has a chosen direction. All decisions are anchored in existing code or existing capability specs.

---

## R1. Envers snapshot read API

**Decision**: Use the existing `HistoryService.entitySnapshotAtRevision(revision, id, entityClass)` for per-id reads and `HistoryService.getEntitiesAtRevision(revision, entityClass)` for the global-whitelist collection. No new audit infrastructure is required.

**Rationale**: `HistoryService` already wraps `AuditReaderFactory.get(entityManager)` and throws `EntityNotFoundException` if the snapshot doesn't exist at the supplied revision — exactly the 404 path FR-002 requires. It is already covered by the auditing capability's `_AUD` tables across H2 / PostgreSQL / SQL Server.

**Alternatives considered**:
- *Directly call `AuditReaderFactory` from each resource service.* Rejected — that would duplicate the existing service-layer wrapper and bypass `HistoryService`'s consistent `EntityNotFoundException` error handling.
- *Introduce a `RollbackService` coordinator that fetches snapshots and dispatches per-resource appliers.* Rejected — there's no shared logic worth coordinating, and a coordinator would need to know about every audited entity type (forcing it to grow as new subtypes are added).

---

## R2. Detecting "the resource was deleted at or before the target revision"

**Decision**: Rely on Envers's existing behaviour — `AuditReader.find(entityClass, id, revision)` returns `null` when the entity does not exist at that revision (whether because it was deleted by that revision or because it was created later). `HistoryService.entitySnapshotAtRevision` already converts that into `EntityNotFoundException`, which the existing global exception handler maps to HTTP 404. This is exactly what FR-002(c) requires.

**Rationale**: Avoids re-implementing existence detection. Matches the existing semantics of the `GET /{id}/revision/{revision}` snapshot read endpoints (per the auditing spec scenario "Snapshot before creation → HTTP 404").

**Alternatives considered**:
- *Inspect Envers's revisions-of-entity list to distinguish "deleted by revision R" from "never existed".* Rejected — both cases map to the same response (404) per the spec; distinguishing them would add code with no user-visible benefit.

---

## R3. Global whitelist snapshotting (collection vs. single entity)

**Decision**: The whitelist is persisted as a **single `DomainWhitelistEntity` row** with a JSON `allowed_domains` list column (`@Audited`). The "snapshot of the image-build whitelist at revision R" is therefore the singleton row's `allowedDomains` list as it existed at R, retrieved via `HistoryService.getEntitiesAtRevision(R, DomainWhitelistEntity.class)` (first result) and used as a `List<String>`. Rollback overwrites the current singleton's list with the snapshot's list (full replace, mirroring the existing `POST /api/v1/global-whitelist/image-build` direct-replace endpoint per FR-009).

**Rationale**: Matches the actual persistence model (`DomainWhitelistEntity` in `dao/entity/` carries `@JdbcTypeCode(SqlTypes.JSON) private List<String> allowedDomains` plus a generated UUID id) and avoids inventing a synthetic "whitelist snapshot DTO". The full-replace semantics on apply (not the import-time merge) are explicitly mandated by FR-009.

**Alternatives considered**:
- *Treat the whitelist as a row-per-entry collection.* Rejected — does not match the existing schema. The entity is a singleton with a JSON-list column.
- *Diff-and-patch (compute add/remove deltas against the current list).* Rejected — adds complexity without observable benefit; full-replace is simpler, matches the existing direct-write semantics, and is easier to test.

---

## R4. Detecting "build-affecting field changed" during image-definition rollback

**Decision**: Not needed in the rollback path. The spec's FR-003(c) clarification (Session 2026-05-19) rejects rollback entirely when current `buildStatus` is `BUILD_SUCCESSFUL` or `BUILDING`. From the remaining states (`NOT_BUILT` / `BUILD_FAILED` / `BUILD_STOPPED`), the existing regular-update path always resets `buildStatus` to `NOT_BUILT` on any change (see `ImageDefinitionService` line 127 onward), so rollback does the same: any field change → `buildStatus = NOT_BUILT`, build artifacts cleared. No diff-by-field logic is required.

**Rationale**: Aligns the rollback path with the existing regular-update behaviour from these states ("Any update resets `buildStatus` to `NOT_BUILT`" per the `image-definitions` spec). Avoids reimplementing build-affecting-field detection.

**Alternatives considered**:
- *Preserve build artifacts on rollback when no build-affecting field changes.* Rejected — would require introducing a build-affecting-field comparator on the rollback path while the existing regular-update path does not need one from these states. Premature optimisation for an operator-driven, low-frequency call.

---

## R5. Deployment lifecycle state precondition check

**Decision**: Add a guard at the top of `DeploymentService.rollback(...)` that rejects with `IllegalArgumentException` (mapped to HTTP 400 by the existing handler) when the current deployment's `status` is one of `PENDING`, `RUNNING`, `CRASHED`, `STOPPING`. Allowed states are `NOT_DEPLOYED` and `STOPPED`. The error message instructs the caller to `undeploy` first.

**Rationale**: Direct enforcement of the Q2 clarification (Session 2026-05-18). Uses the same exception-to-HTTP mapping the rest of the controller uses (no new exception type required).

**Alternatives considered**:
- *Reuse `Deployment.isActive()` predicate.* Verified: existing `isActive()` covers `PENDING / RUNNING / CRASHED / STOPPING`, which matches the rejected set. **Adopted** — preference for the predicate over hard-coded state list to reduce drift if states are added later.

---

## R6. Rollback response shape

**Decision**: Return the existing per-resource DTO (`DeploymentDto` / `ImageDefinitionDto` / `DomainWhitelistResponseDto`) as the 200 body. No new "rollback response" DTO. Per FR-014, the caller should see the resulting current state without a second request.

**Rationale**: Matches FR-014 explicitly. Reuses existing MapStruct mappers and OpenAPI schemas; nothing new to document.

**Alternatives considered**:
- *Add a `RollbackResultDto` with a `noOp: boolean` field so clients can detect identical-state rollbacks.* Rejected — the existing per-resource DTO already lets clients diff against their prior state if they care, and identical-state suppression is now scoped to the whitelist only (see R7). Can be added later without breaking the contract.

---

## R7. Identical-state detection (no-op rollback)

**Decision**: Detect identical-state only for the global image-build whitelist, where the comparison is a one-liner against a `List<String>` via `CollectionUtils.isEqualCollection`. For deployments and image definitions, the rollback path always persists — the service does not attempt to suppress a redundant revision. The operator's intent to rollback is recorded as an `Update` audit revision even when no restorable field actually differs.

**Rationale**: An equality pre-check on the full deployment / image-def entity required either a brittle mirror-then-compare trick (so fields the update path doesn't touch wouldn't trip equals) or a non-trivial writable-fields comparison helper. The reviewer pushed back on both: the mirror trick implied that build artifacts belonged to the rolled-back config, and a dedicated comparison helper was bulky for a niche optimization. The cost of an extra audit row on a true no-op is small (and rare in practice), and the resulting code is materially simpler. The whitelist keeps its detection because its scalar-list comparison was already clean.

**Alternatives considered**:
- *Compare via Lombok-generated `equals` after mirroring system-managed fields onto the snapshot.* Rejected — mirror lines (e.g., copying current `buildLogs` onto the snapshot) misled readers into thinking the rolled-back entity carried artifacts that weren't actually written, and the check was buggy in edge cases (snapshot `buildStatus` mismatch tripped equals even when writable fields matched).
- *Add a `hasSameWritableFields(other)` method on each entity and compare in the service.* Rejected — added per-entity API surface and a non-trivial method body for a niche optimization.
- *Exclude system-managed fields from Lombok `equals` via `@EqualsAndHashCode.Exclude`.* Rejected — widens entity equality semantics globally and risks breaking tests that compare full domain objects.
- *Let JPA dirty-checking decide (rely on the fact that calling setters with equal values produces no UPDATE).* Acknowledged but not explicitly pursued — JPA dirty-checking on `@OneToMany` and JSON columns can produce false positives, and the unconditional `setBuildStatus(NOT_BUILT)` in the image-def update mapper would mark the row dirty whenever current `buildStatus` is `BUILD_FAILED` / `BUILD_STOPPED`. In practice an extra audit revision may still be produced; that's the accepted trade-off.

---

## R8. Endpoint URL shape

**Decision**: Use path-parameter form for revision: `POST /api/v1/<resource>/{id}/revision/{revision}/rollback` (and `POST /api/v1/global-whitelist/image-build/revision/{revision}/rollback` for the singleton). Mirrors the existing `GET .../revision/{revision}` snapshot read paths on the same controllers — confirmed by grep on `DeploymentController`, `ImageDefinitionController`, `GlobalDomainWhitelistController`.

**Rationale**: Consistency with the existing read endpoint URL convention; one less surprise for clients.

**Alternatives considered**:
- *Query-parameter form `POST /{id}/rollback?revision=R`.* Rejected — breaks symmetry with the existing GET snapshot endpoints on the same controllers.

---

## R9. Author capture on rollback

**Decision**: No code change needed. The existing audit revision listener (`dao/audit/listener/`) extracts username + email from the Spring Security context for HTTP requests and stamps them on the revision. Rollback is always HTTP-initiated by an authenticated full admin, so the listener captures the caller automatically. FR-012 is satisfied as a side effect of the standard Envers + listener wiring.

**Rationale**: Documented in the auditing capability spec's "Author identity captured from security context" requirement.

**Alternatives considered**: none — already solved by 014-auditing.

---

## R10. Concurrency / optimistic locking

**Decision**: Rely on the resource entity's existing optimistic-locking strategy (or lack thereof) — no new `@Version` columns or `If-Match` header introduced. If a concurrent write commits between the rollback service's load and save, the standard `OptimisticLockException` (where versioned) propagates and the rollback request fails with HTTP 409; otherwise, last-write-wins, and the resulting state is still captured in a clean audit revision. The spec's "standard optimistic-locking and audit semantics apply" edge case anticipates this.

**Rationale**: Adding `If-Match` / `@Version` is a cross-cutting change beyond this feature's scope; the spec explicitly says rollback is operator-driven and low-frequency.

**Alternatives considered**:
- *Require an `If-Match: <revision>` header.* Rejected — beyond scope; spec does not require it.

---

## Phase 0 outcome

All open questions resolved. No remaining `NEEDS CLARIFICATION` markers in `plan.md`. Proceeding to Phase 1.
