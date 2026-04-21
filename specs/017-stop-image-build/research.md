# Phase 0 Research: Stop Image Build

All questions below resolved — no `NEEDS CLARIFICATION` tokens remain in the plan.

## R1. Race between stop action and pipeline-runner natural completion — Envers-compatible conditional update

**Problem**: The pipeline-runner thread blocks synchronously inside `client.waitJob(...)` in `JobRunner.java`. When the stop action deletes the Job, `waitJob` returns — possibly with a state that the pipeline interprets as a non-terminal observation, possibly with a phase the pipeline interprets as `FAILED`. Without coordination, the pipeline could then call `imageDefinitionService.failBuild(...)` and overwrite `BUILD_STOPPED`.

Additionally, auditing of build lifecycle transitions is driven by Hibernate Envers (`@Audited` on `ImageDefinitionEntity` at `dao/entity/ImageDefinitionEntity.java:33`) plus `AuditRevisionListener.entityChanged()` at `dao/audit/listener/AuditRevisionListener.java:26`, which denormalizes each Hibernate-managed entity change into `AuditActivityEntity`. A JPQL bulk `UPDATE ... WHERE id = :id AND buildStatus = BUILDING` would be atomic but **would bypass Envers entirely** — lifecycle audit entries would silently disappear. This applies to the existing `completeBuildSuccessfully` / `failBuild` methods too, so the naïve "switch to JPQL for race safety" approach would regress 014-auditing.

**Decision**: Keep the existing `findById → setter → saveAndFlush` pattern (Envers-safe) but add **pessimistic row locking** during the conditional transition. Introduce a new repository method `findByIdForUpdate(UUID id)` annotated with `@Lock(LockModeType.PESSIMISTIC_WRITE)`; service methods then execute:

```java
@Transactional
public boolean stopBuild(UUID id) {
    var entity = repository.findByIdForUpdate(id).orElseThrow(NotFound);
    if (entity.getBuildStatus() != PersistenceImageStatus.BUILDING) return false;
    entity.setBuildStatus(PersistenceImageStatus.BUILD_STOPPED);
    repository.saveAndFlush(entity);
    return true;
}
```

Apply the **narrower** pattern to `completeBuildSuccessfully` and `failBuild` — load with `findByIdForUpdate`, short-circuit only when `buildStatus == BUILD_STOPPED` (the one case that must never be overwritten), otherwise set the target status (+ ancillary fields) and `saveAndFlush`. A strict "require BUILDING" guard would break existing test shortcuts that transition directly from `NOT_BUILT` to a terminal state; the narrower "don't overwrite `BUILD_STOPPED`" guard preserves race safety for the one concurrency case we actually introduce with this feature while staying compatible with every existing caller.

Entity-level note: `ImageDefinitionEntity` has no `@Version` column (`dao/entity/ImageDefinitionEntity.java` — only a content `version` string at line 46 for domain versioning). Optimistic locking is therefore unavailable; `PESSIMISTIC_WRITE` is the correct primitive. Adding `@Version` would be a schema-touching change outside this feature's scope.

**Rationale**:
- **Audit-safe**: `saveAndFlush` on a managed entity fires Envers and `AuditRevisionListener.entityChanged()`, so every terminal transition — including the new `BUILD_STOPPED` — is captured automatically. FR-011 is met without any explicit `auditService.record(...)` calls.
- **Race-safe**: `PESSIMISTIC_WRITE` serializes the stop thread and the pipeline thread on the same `image_definition` row. Whichever acquires the lock first completes its transition; the other, when its lock is granted, re-reads and sees `buildStatus != BUILDING`, and short-circuits. Exactly matches Spec Clarification Q1 (all-or-nothing) and Q2 (stop-vs-completion race) semantics.
- **Deterministic across DB vendors**: `SELECT ... FOR UPDATE` is supported on all three supported vendors (H2, PostgreSQL, SQL Server) — JPA translates `PESSIMISTIC_WRITE` to the vendor-appropriate SQL.
- **Transaction scope stays small**: The transactional block is `load + check + set + flush` — no I/O, no Kubernetes calls. Lock hold is sub-millisecond. The cluster-side delete happens **outside** the transaction (between the orchestrator's pre-check and the `stopBuild` service call), so no transaction-for-the-duration-of-a-K8s-wait antipattern.

**Alternatives considered**:
- **JPQL bulk `UPDATE ... WHERE buildStatus = BUILDING`**: rejected — bypasses Envers; would regress 014-auditing for all three transitions, not just the new one.
- **Add `@Version` to `ImageDefinitionEntity` and use optimistic locking**: rejected for this feature — touching the entity's versioning surface could interact with other in-flight changes to the entity, and is disproportionate for the race-safety need alone. Can be proposed as a separate hardening spec if desired.
- **In-process `ConcurrentMap<UUID, Future<?>>` tracked by `ImageBuildRunner`**: rejected — single-JVM only; the service is operable in horizontally-scaled deployments. DB-level coordination is the natural scope.
- **Fabric8 Job informer listener to detect deletion**: rejected — adds complexity, and the pipeline thread still needs the conditional write guard regardless.

## R1b. Pipeline thread must not write a terminal status after an external Job deletion

**Problem discovered during integration testing**: when the stop action's `JobRunner.deleteJob(groupId)` succeeds, the pipeline-runner thread blocked inside `K8sClient.waitJob(...)` unblocks with a `null` `Job` (Fabric8 `waitUntilCondition` returns `null` once the resource is absent and the predicate returns `true`). The pipeline's outer `try/catch` in `ImageBuildFromGitPipeline` / `ImageWrapperBuildPipeline` / `ImageCopyPipeline` then calls `imageDefinitionService.failBuild(...)` — which under the narrow R1 guard is only short-circuited when the status is already `BUILD_STOPPED`. The stop-service has not yet flipped the status at that point, so the pipeline wins the race and writes `BUILD_FAILED`. Stop's own `stopBuild(...)` then sees `BUILD_FAILED`, its `BUILDING`-conditional update returns 0 rows, and the admin receives `400 "Image build is not in progress (current status: BUILD_FAILED)"` with the stack trace `Cannot invoke Job.getStatus() because job is null` in the server log — even though the stop did everything correctly.

**Decision**: Make the pipeline tell the difference between "my Job ran and failed" and "my Job was externally deleted". Introduce a dedicated exception `JobExternallyDeletedException` in the `kubernetes/` package; throw it from `JobRunner.run` whenever either `waitJob(...)` call returns `null` (the signal Fabric8 uses for deletion under the predicate). Make the `jobIsRunning` and `jobIsFinished` predicates null-safe so the wait terminates cleanly on deletion rather than letting the predicate NPE. Harden `JobPhase.fromJob(Job)` to return `Optional.empty()` on `null` input as defense in depth (the primary fix is in the predicates).

Then each pipeline's top-level `run(UUID)` is changed from:

```java
try { ... } catch (Exception e) { imageDefinitionService.failBuild(...); } finally { ... }
```

to a two-level catch that lets the stop action own the terminal status:

```java
try { ... }
catch (JobExternallyDeletedException e) { /* log; stop action owns the status update */ }
catch (Exception e) { imageDefinitionService.failBuild(...); }
finally { disposableResourceCleaner.cleanTemporaryByGroupId(imageDefinitionId); }
```

**Rationale**:
- Narrow and explicit: the pipeline distinguishes the one scenario where it must **not** write a terminal status, so every other failure still produces `BUILD_FAILED` as today.
- Keeps the R1 guard simple (still "only skip overwrite if `BUILD_STOPPED`") — no need to widen it to "must have been `BUILDING`", which would break pre-existing test shortcuts.
- Avoids an alternative design that flipped the status to `BUILD_STOPPED` **before** `deleteJob` and needed a rollback path when cluster delete or cleanup failed. That design leaves `image_definition.build_status` temporarily inconsistent with cluster reality and requires a new conditional-update method for rollback; the exception-based approach stays consistent with cluster truth at every instant.
- The defensive `finally { cleanTemporaryByGroupId }` in the pipeline still runs on the externally-deleted path. It races harmlessly with the stop service's own `cleanTemporaryByGroupId` (Fabric8 `deleteX` on a missing resource is a no-op; duplicate DB row deletes are absorbed by Spring Data).

**Alternatives considered**:
- Flip status to `BUILD_STOPPED` before `deleteJob`, add a rollback method for the cleanup-failure path — rejected: creates a window where DB status and cluster state are intentionally out of sync, and the rollback path is hard to test.
- Widen the R1 guard so `completeBuildSuccessfully`/`failBuild` short-circuit whenever the current status is not `BUILDING` — rejected: breaks pre-existing tests that transition `NOT_BUILT → BUILD_*` directly; we chose the narrow guard specifically to avoid that blast radius.
- Hold a DB transaction open across the `deleteJob` K8s call so the pipeline's `failBuild` blocks on `PESSIMISTIC_WRITE` until stop commits — rejected: violates constitution §II (Transactional Discipline: keep transactions small) and the R1 design note about sub-millisecond lock hold times.

## R2. Cluster-side cleanup pathway (synchronous delete vs. marking for background cleanup)

**Problem**: `DisposableResourceManager.markResourcesForCleanupByGroupId(...)` flips a lifecycle flag and defers actual deletion to a scheduled cleaner. Clarification Q1 requires the status transition to happen **only after** cluster-side cleanup has succeeded. Relying on the scheduled cleaner would mean status either advances before cleanup (violating Q1) or blocks indefinitely waiting for the next scheduler tick.

**Decision**: Add `JobRunner.deleteJob(String groupId, String namespace)` that:
1. Resolves the target Job via `DisposableResourceManager.getAllByGroupId(groupId)` (at `cleanup/resource/DisposableResourceManager.java:37`), filters the returned `DisposableResource` list for `K8sResourceKind.JOB` + matching namespace, and extracts the Job's name. No Kubernetes label selectors are used — the `groupId → Job name` mapping is already persisted in the disposable-resources table at Job creation time (see `JobRunner.java:68–69`: `disposableResourceManager.saveK8sResources(List.of(job), K8sResourceKind.JOB, groupId, namespace);`).
2. Issues a Fabric8 `deleteJob(namespace, jobName)` call with `PropagationPolicy.FOREGROUND` so the Job's pods are cascaded.
3. Blocks on Fabric8's `waitUntilCondition(...)` / `waitUntilGone(...)` (whichever is canonical for the project's Fabric8 version) until the Job resource is removed from the API server, bounded by the hard-coded `image-build-stop-timeout-sec: 30` value in `application.yml`.
4. Throws a specific exception (e.g., `KubernetesJobDeletionFailedException` or propagates `KubernetesClientException`) if the API returns an error or the timeout elapses with the Job still present.

After `JobRunner.deleteJob` returns successfully, the orchestration layer:
5. Calls `disposableResourceCleaner.cleanTemporaryByGroupId(imageDefinitionId)` — the **same primitive** every pipeline already invokes in its `finally` block (see `ImageBuildFromGitPipeline.java:45`, `ImageWrapperBuildPipeline.java:36`, `ImageCopyPipeline.java:34`). It synchronously deletes all `TEMPORARY`-state disposable resources for the groupId: secrets, configmaps, the Cilium network policy, the (already gone) Job, and the intermediate MCP-LOCAL base image if present. Final `STABLE` artifacts (wrapped images, copied images, MCP-REMOTE finals) are untouched.
6. Queries `disposableResourceManager.getAllTemporaryByGroupId(...)` again as a **post-condition check**: `DisposableResourceCleaner.clean(resource)` swallows per-resource exceptions, so a secret/configmap delete that fails silently would leave the DB row. If any `TEMPORARY` rows remain for the groupId, the stop action fails the whole operation with `ImageBuildStopFailedException` (FR-004 all-or-nothing). The admin retries.
7. Calls `ImageDefinitionService.stopBuild(id)` (conditional update to `BUILD_STOPPED` — R1 pattern).

**Rationale**:
- Satisfies Q1 (no divergence between recorded status and cluster state).
- Reuses the exact cleanup primitive (`cleanTemporaryByGroupId`) that every pipeline already uses in its `finally` block when a build ends. This means "stop mid-pipeline" and "normal pipeline completion" tear down cluster resources via the same code path — minimal risk of divergent behavior.
- **Why `cleanTemporaryByGroupId` and not `cleanAllCleanableByGroupId`**: the latter also cleans `TO_CLEANUP`-state resources and (per `AbstractCleanupStrategy.cleanupResources`) is paired with a preceding `markResourcesForCleanupByGroupId` call that flips STABLE→TO_CLEANUP. Using it would risk deleting final `STABLE` artifacts (wrapped images, image copies, MCP-REMOTE finals) from the registry — artifacts the admin may want preserved for a rebuild diff or a rollback. Scoping cleanup to `TEMPORARY` mirrors the pipeline's own in-flight cleanup and leaves final artifacts alone.
- **Container-registry side effects are benign**: across all three pipelines, only one registry resource is ever registered as `TEMPORARY` — the intermediate MCP-LOCAL base image (`ImageBuildFromGitPipeline.java:62`). It is an intermediate that the wrapper build was about to consume; cleaning it on stop is correct because it would have been cleaned on normal pipeline completion via the same `finally` block. All other `saveContainerRegistryResource` calls use `STABLE` (`ImageCopyStep.java:38`, `WrapperImageBuildStep.java:42`, and `ImageBuildFromGitPipeline.java:60` for MCP-REMOTE).
- The stop timeout is a dedicated property (`image-build-stop-timeout-sec: 30`) distinct from the existing build timeout `image-build-timeout-sec: 300` (at `application.yml:119`) — stop responsiveness is separated from build patience. The value is hard-coded (not env-overridable): `30s` is conservative enough for real-cluster delete latencies (Job + pods with `terminationGracePeriodSeconds`) while still bounded.
- Transitioning to a failure response when the timeout elapses is consistent with Clarification Q1's all-or-nothing rule — the recorded status stays `BUILDING`, and the administrator may retry.

**Alternatives considered**:
- Issue a `delete` and don't wait, relying only on `markResourcesForCleanupByGroupId` — rejected: violates Q1, and relying on the scheduled `DisposableResourceCleaner` tick leaves a window where rebuild attempts collide with lingering secrets/configmaps/CNPs.
- Use `cleanAllCleanableByGroupId` — rejected: also processes `TO_CLEANUP` rows and (when paired with `markResourcesForCleanupByGroupId` as `AbstractCleanupStrategy` does) would purge final `STABLE` artifacts. Out of scope for stop.
- Let `DisposableResourceCleaner.clean(...)` rethrow per-resource failures — rejected: would change shared-cleaner semantics used by scheduled cleanup paths. The post-condition re-query gives the stop service what it needs without touching shared code.
- Use a Fabric8 informer to confirm deletion — rejected: synchronous wait is simpler and the expected deletion time is short.

## R3. Audit event for the stop action — automatic via Envers

**Problem**: FR-011 requires the stop action to be recorded in the audit log — acting administrator, target image definition, timestamp — consistent with other build lifecycle events introduced in the 014-auditing feature.

**Decision**: Rely on the existing Envers-based auditing. Confirmed mechanism:
- `ImageDefinitionEntity` carries `@Audited` (`dao/entity/ImageDefinitionEntity.java:33`).
- `AuditRevisionListener` implements `EntityTrackingRevisionListener` (`dao/audit/listener/AuditRevisionListener.java:26`).
- `entityChanged()` at `AuditRevisionListener.java:69–108` denormalizes each entity change into an `AuditActivityEntity` row, populating acting principal (from `SecurityContextHolder`), timestamp (the revision's timestamp), and target entity identifier.

Because the R1 decision keeps the `findById → setter → saveAndFlush` pattern, the `BUILD_STOPPED` transition triggers Envers and `AuditRevisionListener.entityChanged()` automatically — FR-011 is satisfied with **zero explicit audit code** in `ImageBuildStopService`. A functional test should nevertheless assert that the `AuditActivityEntity` row appears for a stop (to guard against a regression in which someone switches the repository method to a non-Envers-firing path in the future).

**Rationale**: Clarification Q5 pinned the audit entry to who/when/target only — exactly what Envers + `AuditRevisionListener` already deliver for `BUILD_SUCCESSFUL` / `BUILD_FAILED` transitions today.

**Alternatives considered**:
- Introduce a dedicated `StopBuildAuditEventPublisher` — rejected: over-engineered; reuse what 014 ships.
- Add an explicit `AuditService.record(...)` call in `ImageBuildStopService` — rejected: would double-audit the transition (one record via the explicit call, another via Envers).

## R4. Error shape for FR-002 rejections (including race with natural completion)

**Problem**: Spec Clarification Q2 requires the error payload to include the build's current recorded status when a stop is rejected. The existing `ErrorView` (from `web/handler/DefaultExceptionHandler.java`) has `path, method, status, error, message, traceparent` only — no structured field for the current build status.

**Decision**: Do **not** modify `ErrorView`. Instead, format the current recorded status into the `message` field as a machine-parseable suffix. Example:

```
{
  "path": "/api/v1/images/builds/{id}",
  "method": "DELETE",
  "status": 400,
  "error": "Bad Request",
  "message": "Image build is not in progress (current status: BUILD_SUCCESSFUL)",
  "traceparent": "00-<trace-id>-<span-id>-01"
}
```

The exact token `"(current status: <STATUS>)"` is part of the contract and is exercised in the contract tests — the UI can regex this out without waiting for a broader `ErrorView` redesign.

**Rationale**:
- Minimally invasive — no `ErrorView` schema change, no coordination with other features.
- Human-readable for log inspection while still machine-parseable for the UI.
- If a richer error shape is added later as a cross-cutting concern, the same data can be moved into a structured field without breaking callers that tolerate extra fields.

**Alternatives considered**:
- Extend `ErrorView` with an optional `details: Map<String, Object>` — rejected for this feature: the constitution treats `ErrorView` as the canonical error shape; a change here should be its own constitution-touching spec.
- Put the current status into an HTTP response header (e.g., `X-Current-Build-Status`) — rejected: inconsistent with how the project represents error context elsewhere.

## R5. Naming for the stop service, exception, and audit terminology

**Decision**:
- Service: **`ImageBuildStopService`** (`service/ImageBuildStopService.java`) — mirrors the `ImageBuildRunner` / `ImageBuildLogsService` naming pattern. Not folded into `ImageBuildRunner` to keep trigger and stop responsibilities separate (small interface surface each, easier unit-testing).
- Exception: **`ImageBuildNotInProgressException extends RuntimeException`** (`exception/ImageBuildNotInProgressException.java`). Accepts the `imageDefinitionId` and the current `ImageStatus` as constructor args; `getMessage()` formats the canonical FR-002 message with the suffix defined in R4.
- Audit terminology: `BUILD_STOPPED` (matching Clarification Q3) is used in the status field; audit event type name follows whatever 014-auditing uses for `BUILD_SUCCESSFUL` / `BUILD_FAILED` (verified in Phase 2 tasks).

**Rationale**: Consistency with existing naming conventions (Constitution §Naming Conventions) and with the canonical status name pinned in Clarification Q3.

**Alternatives considered**:
- Add `stopImage(UUID)` to `ImageBuildRunner` — rejected: conflates trigger orchestration with interruption orchestration; makes `ImageBuildRunner` larger and harder to test in isolation.
- Name the exception `BuildNotInProgressException` (no "Image" prefix) — rejected: other feature areas may want the same concept for deployment builds; explicit `Image` prefix avoids naming collision.

## R6. Rebuilding after a stopped build (FR-007) — verified

**Decision**: No special-casing required. The existing `buildImage` guard in `ImageBuildRunner.java:46–48` reads verbatim:

```java
if (imageDefinition.getBuildStatus() == ImageStatus.BUILD_SUCCESSFUL
        || imageDefinition.getBuildStatus() == ImageStatus.BUILDING) {
    throw new IllegalArgumentException(
        "Image '%s' is already built or build process is running".formatted(imageDefinitionId));
}
```

The guard is a **strict allow-list against two states only** (`BUILD_SUCCESSFUL`, `BUILDING`). `BUILD_STOPPED` falls through identically to `BUILD_FAILED` and `NOT_BUILT`, so `buildImage` accepts the retrigger automatically — no code change needed in `ImageBuildRunner` to satisfy FR-007.

**Rationale**: Confirmed by direct code read; no behavior inference involved.

**Alternatives considered**:
- Allow retrigger only from `BUILD_STOPPED` if cluster-side cleanup is confirmed clean — rejected: over-engineered; the R2 decision guarantees the cluster state is consistent before `BUILD_STOPPED` is recorded.

## R7. SSE stream closure on `BUILD_STOPPED`

**Decision**: `ImageStatus.BUILD_STOPPED` is declared with `isFinal = true`. `ImageBuildLogsService.java` already emits the final status event and calls `emitter.complete()` when `buildStatus.isFinal()` returns true (lines 145–148). No changes to `ImageBuildLogsService` are required.

**Rationale**: Confirmed directly in the existing SSE polling loop — it reads the status enum's `isFinal` flag and closes. This is the "just works" case called out in R2 of the explore pass.

**Alternatives considered**:
- None needed — the SSE service is already terminal-state-agnostic by design.

## R9. Stop timeout — hard-coded YAML property

**Decision**: Add `image-build-stop-timeout-sec: 30` to `application.yml` (adjacent to the existing `image-build-timeout-sec: 300` at `application.yml:119`) and inject it into `JobRunner` via `@Value("${app.image-build-stop-timeout-sec}")`.

- The value is hard-coded in `application.yml` — no `${...}` env-var indirection.
- No corresponding environment variable is exposed.
- `docs/configuration.md` carries no entry for this property (nothing is operator-tunable).

The property is read by `JobRunner.deleteJob(...)` (R2) to bound the wait for the Job to disappear from the API server. It does **not** bound any other operation.

**Rationale**:
- Distinct timeout from `image-build-timeout-sec` (the existing build-running timeout, `300`) — build patience and stop responsiveness are different concerns even though both are fixed.
- `30s` is conservative enough for real-cluster cascade-delete latencies (Jobs with pods that have `terminationGracePeriodSeconds`) while still giving administrators a bounded experience.
- Keeping it as a YAML property (rather than a magic number in code) leaves one place to edit if the value ever needs to change.

**Alternatives considered**:
- Reuse the existing `image-build-timeout-sec` — rejected: `300s` is far too patient for a user-initiated stop; administrators expect fast feedback.
- Expose as an env-var override — rejected: no need for per-operator tuning; hard-coded keeps configuration surface small.

## R8. Multi-vendor DB impact

**Decision**: No Flyway migration is required. `image_definition.build_status` is `VARCHAR(32)` in all three vendor migration directories (H2, POSTGRES, MS_SQL_SERVER) with no `CHECK` constraint, no native enum, and comfortable room for the `BUILD_STOPPED` value (12 chars). Adding the value to `ImageStatus` + `PersistenceImageStatus` (mapped 1:1 by name) is sufficient.

`./gradlew generateDbSchema` is **not** required for this feature because no migration is added. The Claude Code hook at `.claude/hooks/generate-db-schema.sh` will not fire (no migration file touched).

**Rationale**: Confirmed in the research pass — the column is string-based, not enum-typed.

**Alternatives considered**:
- Add a `CHECK` constraint on `build_status` to enforce the enum values at DB level — rejected for this feature: it would be a cross-cutting schema change unrelated to the stop feature and adds rollback complexity. Can be raised as a follow-up data-integrity spec if desired.
