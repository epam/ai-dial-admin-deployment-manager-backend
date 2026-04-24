---

description: "Task list for feature 017-stop-image-build"

---

# Tasks: Stop Image Build

**Input**: Design documents from `/specs/017-stop-image-build/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/stop-image-build.md

**Tests**: Test tasks are **included** because the project's constitution mandates functional tests across H2 / PostgreSQL / SQL Server and unit tests for every new service/controller. Tests live in `src/test/java/...`.

**Organization**: Tasks are grouped by the three user stories defined in spec.md so each story can be implemented and validated independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Paths are absolute from the repo root; all Java files live under `src/main/java/com/epam/aidial/deployment/manager/...` and test files under `src/test/java/com/epam/aidial/deployment/manager/...`.

## Path Conventions

Single Spring Boot module. Source under `src/main/java/`, tests under `src/test/java/`. Layering `web → service → dao / kubernetes` (see constitution §Strict Layered Architecture).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: No new project infrastructure is needed. One informational task to locate the existing image-build `@ConfigurationProperties` class and confirm the scope of the Envers-based audit integration (already identified in research.md R3 as `@Audited` on `ImageDefinitionEntity` + `AuditRevisionListener.entityChanged()`).

- [X] T001 Locate the existing `@ConfigurationProperties` class backing the `image-build-timeout-sec`, `image-build-logs-size-limit`, and similar entries under the `app.*` group in `src/main/resources/application.yml` (line ~117 onward). Record its fully qualified class name — subsequent tasks (T007) add a new field to this class. Also skim `docs/configuration.md` to see the existing formatting convention for image-build properties so T008 matches style.

**Finding**: There is **no** central `@ConfigurationProperties` class for the `app.image-build-*` keys. The existing convention injects each key directly via `@Value("${app.image-build-timeout-sec}")` into fields of the components that need it (see `BaseImageBuildStep.java:24`, `ImageAnalysisStep.java:22`, `ImageCopyStep.java:23`, `WrapperImageBuildStep.java:25`). T007 therefore adds the new YAML key and injects it via `@Value` where needed (T009's `JobRunner.deleteJob`) — no new properties class is created. This matches the existing codebase convention exactly and does not violate constitution §Configuration property defaults (that rule targets `@ConfigurationProperties` fields specifically).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared primitives every user story depends on — the new enum value, the custom exception, the exception-to-HTTP mapping, the new stop-timeout config property (with its docs entry), and the synchronous K8s Job delete in the `kubernetes/` layer.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 [P] Add `BUILD_STOPPED(true)` as the final value in `ImageStatus` enum at `src/main/java/com/epam/aidial/deployment/manager/model/ImageStatus.java`. Insert after `BUILD_SUCCESSFUL(true),`.
- [X] T003 [P] Add `BUILD_STOPPED` as the final value in `PersistenceImageStatus` enum at `src/main/java/com/epam/aidial/deployment/manager/dao/entity/PersistenceImageStatus.java`. Insert after `BUILD_SUCCESSFUL`. Confirm the existing `PersistenceImageStatusMapper` (MapStruct) maps by name and therefore needs no changes.
- [X] T004 [P] Create `ImageBuildNotInProgressException` (extends `RuntimeException`) at `src/main/java/com/epam/aidial/deployment/manager/exception/ImageBuildNotInProgressException.java` (existing custom-exception package — confirmed to contain `ImageInUseException`, `ValidationException`, `EntityAlreadyExistsException`, etc.). Constructor takes `UUID imageDefinitionId` and `ImageStatus currentStatus` (nullable — render as `NOT_BUILT` when null). `getMessage()` formats exactly: `Image build is not in progress (current status: <STATUS>)`.
- [X] T005 [P] Create `ImageBuildStopFailedException` (extends `RuntimeException`) at `src/main/java/com/epam/aidial/deployment/manager/exception/ImageBuildStopFailedException.java` for FR-004 cluster-deletion failures. Constructor takes `UUID imageDefinitionId` and a `Throwable cause`; `getMessage()` formats: `Image build could not be stopped: <cause.getMessage()>; build remains in BUILDING and may be retried`.
- [X] T006 Add `@ExceptionHandler(ImageBuildNotInProgressException.class)` **and** `@ExceptionHandler(ImageBuildStopFailedException.class)` to `DefaultExceptionHandler` at `src/main/java/com/epam/aidial/deployment/manager/web/handler/DefaultExceptionHandler.java`. Both return HTTP 400 with an `ErrorView` whose `message` is `ex.getMessage()` (preserving the `(current status: <STATUS>)` suffix from T004 and the "could not be stopped" wording from T005). Call `logUncaught(ex)` to match the logging pattern used by the other handlers in this class.
- [X] T007 [P] Add the YAML entry `image-build-stop-timeout-sec: 30` to `src/main/resources/application.yml` adjacent to the existing `image-build-timeout-sec: 300` line (around line 119), under the same `app:` parent. The value is hard-coded — no env-var indirection, no `docs/configuration.md` entry. `JobRunner.deleteJob` (T009) injects via `@Value("${app.image-build-stop-timeout-sec}")` matching the existing convention for `image-build-timeout-sec`.
- [X] T008 [P] *(Intentionally empty — no documentation update needed; the stop timeout is hard-coded in `application.yml` and is not an operator-configurable env var.)*
- [X] T009 Add `deleteJob(String groupId, String namespace)` to `JobRunner` at `src/main/java/com/epam/aidial/deployment/manager/kubernetes/JobRunner.java`. Implementation steps: (1) Resolve the Job by calling `disposableResourceManager.getAllByGroupId(groupId)`, filtering the resulting `List<DisposableResource>` for `K8sResourceKind.JOB` and matching namespace, and extracting the Job's name from its `K8sResourceReference`. If no Job is found, log at `DEBUG` (cluster-side resource already gone — see spec Edge Cases) and return successfully. (2) Invoke Fabric8's delete for that Job with `PropagationPolicy.FOREGROUND` so pods are cascaded. (3) Block on Fabric8's `waitUntilCondition(...)` / `waitUntilGone(...)` until the Job resource is absent from the API server, bounded by the `image-build-stop-timeout-sec` property from T007. (4) On API error or timeout, throw a new `KubernetesJobDeletionFailedException` in the same `kubernetes/` package (or propagate `KubernetesClientException`) so the orchestrator layer (T018) can wrap it in `ImageBuildStopFailedException`. Do NOT use custom polling loops anywhere; Fabric8's `waitUntilCondition` is canonical per constitution §Key Patterns.

**Checkpoint**: Foundation ready — user stories can now begin.

---

## Phase 3: User Story 1 - Stop a running image build (Priority: P1) 🎯 MVP

**Goal**: An admin can issue `DELETE /api/v1/images/builds/{imageDefinitionId}` on a build whose status is `BUILDING`, and the system (a) deletes the backing Kubernetes Job synchronously, (b) advances the recorded status to `BUILD_STOPPED` only after cluster-side cleanup confirmed success, (c) preserves captured logs, and (d) allows a new build to be triggered for the same image definition. Delivered value: admins can interrupt runaway or mistaken builds and reclaim cluster capacity.

**Independent Test**: Start a build for an image definition using `POST /api/v1/images/builds`. Wait until `buildStatus = BUILDING` is observable. Issue `DELETE /api/v1/images/builds/{id}` with admin credentials. Within the hard-coded 30s stop window, `GET /api/v1/images/builds/{id}/details` returns `status = BUILD_STOPPED`; within 60 seconds, `kubectl get jobs` no longer lists the Job; `POST /api/v1/images/builds` for the same image definition is accepted with HTTP 201.

### Tests for User Story 1 (write first, ensure they fail before implementation)

- [X] T010 [P] [US1] Unit test `ImageBuildStopServiceTest` at `src/test/java/com/epam/aidial/deployment/manager/service/ImageBuildStopServiceTest.java` covering: (a) happy path — `buildStatus = BUILDING` → `JobRunner.deleteJob` called → `DisposableResourceManager.markResourcesForCleanupByGroupId` called → `ImageDefinitionService.stopBuild` called (returns `true`) → no exception; (b) cluster-side deletion failure — when `JobRunner.deleteJob` throws, the service wraps it in `ImageBuildStopFailedException`, `ImageDefinitionService.stopBuild` is NOT called, `disposableResourceManager.markResourcesForCleanupByGroupId` is NOT called. Use `@ExtendWith(MockitoExtension.class)`, mock all collaborators. Follow test-method naming `shouldStopRunningBuild` and `shouldFailStopBuild_whenClusterDeletionFails` per constitution §Testing Conventions.
- [X] T011 [P] [US1] Controller test case `shouldStopBuild_whenBuildingAndAdmin` inside `src/test/java/com/epam/aidial/deployment/manager/web/controller/none/ImageBuildControllerTest.java`. `@WebMvcTest(ImageBuildController.class)`, mock `ImageBuildStopService` and `ImageBuildRunner`. Expect HTTP 204 with empty body; verify `imageBuildStopService.stopBuild(id)` called exactly once.
- [X] T012 [P] [US1] Functional test `StopImageBuildFunctionalTest` extending `H2FunctionalTests` at `src/test/java/com/epam/aidial/deployment/manager/functional/h2/StopImageBuildFunctionalTest.java` with four cases: (a) `shouldStopRunningBuild` — seed an `ImageDefinitionEntity` with `buildStatus = BUILDING`, issue DELETE, assert HTTP 204, assert DB row now has `buildStatus = BUILD_STOPPED`, logs preserved, `imageName`/`builtAtMs` unchanged; (b) `shouldRebuildAfterStop` (FR-007) — after a successful stop, issue `POST /api/v1/images/builds` and assert HTTP 201; (c) `shouldFailStop_whenClusterDeletionFails` — inject a `JobRunner` test double that throws on `deleteJob`, issue DELETE, assert HTTP 400 with message starting `Image build could not be stopped`, assert DB row still `BUILDING`; (d) `shouldRecordAuditActivity_forStop` — after a successful stop, query the `AuditActivityEntity` repository and assert an entry is present attributing the change to the acting principal with the image definition ID as subject (guards against regression of Envers wiring). Use the same auth/test-user helpers as `ImageBuildFunctionalTest` for admin role.

### Implementation for User Story 1

- [X] T013 [US1] Add `findByIdForUpdate(UUID id)` to the Spring Data JPA interface `ImageDefinitionJpaRepository` (the `*JpaRepository` naming — find via `git grep 'interface ImageDefinition.*JpaRepository'`). Annotate with `@Lock(LockModeType.PESSIMISTIC_WRITE)` and `@Query("select e from ImageDefinitionEntity e where e.id = :id")`. Returns `Optional<ImageDefinitionEntity>`. Translates to `SELECT ... FOR UPDATE` on H2, PostgreSQL, and SQL Server.
- [X] T014 [US1] In `src/main/java/com/epam/aidial/deployment/manager/dao/repository/ImageDefinitionRepository.java`, add a new repository-wrapper method `boolean stopBuild(UUID id)` annotated `@Transactional`: call `imageDefinitionJpaRepository.findByIdForUpdate(id)`, if empty throw the existing not-found exception; if present and `buildStatus != BUILDING` return `false` without mutation; otherwise set `buildStatus := BUILD_STOPPED`, call `saveAndFlush`, return `true`. Do NOT mutate `logs`, `imageName`, or `builtAtMs` (FR-006). `saveAndFlush` fires Envers — that's the audit capture.
- [X] T015 [US1] In the same file (`ImageDefinitionRepository.java`), modify the existing `completeBuildSuccessfully(...)` and `failBuild(...)` wrapper methods to use the same `findByIdForUpdate + buildStatus == BUILDING + setter + saveAndFlush` pattern: short-circuit and log at `DEBUG` when the pre-check sees a non-`BUILDING` status (pipeline lost the race with stop, or with a prior terminal transition). This is the Phase 0 R1 race-safety change and is Envers-safe because the entity is loaded, mutated, and flushed via the managed path (JPQL bulk updates would bypass Envers — explicitly rejected in R1).
- [X] T016 [US1] Add `boolean stopBuild(UUID id)` to `src/main/java/com/epam/aidial/deployment/manager/service/ImageDefinitionService.java`. Delegates directly to `imageDefinitionRepository.stopBuild(id)`. The `@Transactional` boundary is on the repository wrapper (T014), not here — this service method is a thin pass-through so `ImageBuildStopService` can mock `ImageDefinitionService` cleanly in unit tests.
- [X] T017 [US1] In `src/main/java/com/epam/aidial/deployment/manager/service/ImageBuildRunner.java`, inspect the pipeline-result call sites for `completeBuildSuccessfully` / `failBuild` (either in `ImageBuildRunner` itself or in each pipeline class under `service/pipeline/`). No code change is required if these already go through `ImageDefinitionService.completeBuildSuccessfully(...)` / `.failBuild(...)` — the T015 change makes them tolerate the race automatically. If any pipeline calls the JPA layer directly, route it through the service method instead. Add one-line `@Slf4j` debug log in the pipeline's success/failure callback that notes "pipeline terminal update skipped (status already advanced)" when the call returns without mutating (optional but aids observability).
- [X] T018 [US1] Create `ImageBuildStopService` at `src/main/java/com/epam/aidial/deployment/manager/service/ImageBuildStopService.java`. Annotate `@Service` and `@LogExecution` (constitution §Observability First). Constructor inject: `ImageDefinitionService`, `JobRunner`, `DisposableResourceManager`. Public method `void stopBuild(UUID imageDefinitionId)`. Flow:
  1. Load the image definition via `imageDefinitionService.getImageDefinition(id)` — natural 404 on unknown (FR-003; propagates the existing not-found exception).
  2. Read its `buildStatus`. If not `BUILDING`, throw `new ImageBuildNotInProgressException(id, currentStatus)`.
  3. Resolve `groupId` and `namespace` from the image definition (same resolution the pipeline used at Job creation — read `JobRunner.java:68–69` and the relevant `JobSpecification` implementations to mirror).
  4. Call `jobRunner.deleteJob(groupId, namespace)`. On exception, wrap in `ImageBuildStopFailedException(id, cause)` and throw (mapped to HTTP 400 by the handler from T006). Do NOT invoke `imageDefinitionService.stopBuild` in this path (FR-004 all-or-nothing).
  5. Call `disposableResourceManager.markResourcesForCleanupByGroupId(groupId)` so adjacent disposable resources (registry artifacts, etc.) are reclaimed on the normal cleaner cadence.
  6. Call `imageDefinitionService.stopBuild(id)`. If it returns `false` (pre-check inside T014 saw a non-`BUILDING` status — pipeline won the race between our step 2 and step 6), re-read the image definition for the fresh status and throw `new ImageBuildNotInProgressException(id, currentStatus)` — the exception message will carry the actual terminal status, per spec Clarification Q2.
- [X] T019 [US1] Add the `DELETE /api/v1/images/builds/{imageDefinitionId}` endpoint to `ImageBuildController` at `src/main/java/com/epam/aidial/deployment/manager/web/controller/ImageBuildController.java`. Signature: `@FullAdminOnly @DeleteMapping("/{imageDefinitionId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void stopBuild(@PathVariable UUID imageDefinitionId)` that calls `imageBuildStopService.stopBuild(imageDefinitionId)`. Add SpringDoc annotations `@Operation(summary = "Stop an in-progress image build")` with `@ApiResponse` entries for 204, 400, 403, 404 (constitution §API Conventions). No request body. Constructor-inject `ImageBuildStopService` alongside the existing collaborators.

**Checkpoint**: User Story 1 is fully functional and testable independently — MVP complete.

---

## Phase 4: User Story 2 - Reject stop requests for builds that are not in progress (Priority: P2)

**Goal**: Any DELETE against a build that is not currently `BUILDING` must be rejected with a clear HTTP 400 containing the current recorded status, without mutating the build record. Covers: never-built, succeeded, failed, already-stopped, and the race-loss case where the pipeline completed before the stop took effect (spec Clarification Q2).

**Independent Test**: For each non-running state (`NOT_BUILT`, `BUILD_SUCCESSFUL`, `BUILD_FAILED`, `BUILD_STOPPED`), issue `DELETE /api/v1/images/builds/{id}` and verify: (a) HTTP 400, (b) response body `message` matches regex `Image build is not in progress \(current status: (NOT_BUILT|BUILD_SUCCESSFUL|BUILD_FAILED|BUILD_STOPPED)\)` with the correct token for each case, (c) the image definition row is byte-for-byte identical before and after.

No implementation tasks — the rejection flow is wired as part of US1 (T014's conditional update, T018's pre-check and race-loss throw). This phase is pure contract verification.

### Tests for User Story 2

- [X] T020 [P] [US2] In `src/test/java/com/epam/aidial/deployment/manager/web/controller/none/ImageBuildControllerTest.java`, add parameterized controller test `shouldFailStopBuild_whenBuildIsNotInProgress` covering `NOT_BUILT`, `BUILD_SUCCESSFUL`, `BUILD_FAILED`, `BUILD_STOPPED`. For each, mock `imageBuildStopService.stopBuild(id)` to throw `ImageBuildNotInProgressException(id, <STATUS>)`; assert HTTP 400, assert response body `message` matches the regex in the acceptance criteria above with the correct `<STATUS>` token.
- [X] T021 [P] [US2] In the same file, add: (a) `shouldFailStopBuild_whenImageDefinitionNotFound` — expect 404; (b) `shouldFailStopBuild_whenCallerIsNotAdmin` — expect 403 (use existing non-admin test auth helper); (c) `shouldFailStopBuild_whenUnauthenticated` — expect 401.
- [X] T022 [P] [US2] In `src/test/java/com/epam/aidial/deployment/manager/functional/h2/StopImageBuildFunctionalTest.java`, add one parameterized functional case per non-running state: seed `ImageDefinitionEntity` with the target status (including populated `logs`, `imageName`, `builtAtMs` where applicable), issue DELETE, assert HTTP 400 with the regex-matching message, then reload the entity from the repository and assert that every field is unchanged from the seeded values.
- [X] T023 [P] [US2] In `src/test/java/com/epam/aidial/deployment/manager/service/ImageBuildStopServiceTest.java`, add `shouldFailStopBuild_whenPipelineWonTheRace` — pre-check reads `BUILDING`, `JobRunner.deleteJob` succeeds, `ImageDefinitionService.stopBuild` returns `false` (simulating the race-lost short-circuit inside T014). Mock a subsequent re-read of the image definition to return `BUILD_SUCCESSFUL`. Assert the service throws `ImageBuildNotInProgressException` carrying `BUILD_SUCCESSFUL`. This exercises spec Clarification Q2 at the service layer.
- [ ] T024 [P] [US2] Add a unit test for `ImageBuildNotInProgressException` itself at `src/test/java/com/epam/aidial/deployment/manager/exception/ImageBuildNotInProgressExceptionTest.java` — verify `getMessage()` format for each status value and the null-status-renders-as-`NOT_BUILT` branch.

**Checkpoint**: User Story 2 verified — the rejection contract is exercised end-to-end, and US1 remains green.

---

## Phase 5: User Story 3 - Observers are notified promptly when a build is stopped (Priority: P3)

**Goal**: SSE subscribers to `/status` and `/logs` must receive a final event reflecting `BUILD_STOPPED` and have their streams closed by the server, within the same configured stop-timeout window. Driven entirely by `ImageStatus.BUILD_STOPPED.isFinal = true` (already set in T002), which the existing `ImageBuildLogsService` polling loop detects.

**Independent Test**: Open an SSE connection to `GET /api/v1/images/builds/{id}/status` and another to `GET /api/v1/images/builds/{id}/logs` while the build is `BUILDING`. Issue `DELETE`. Observe that both streams emit a `status` event carrying `BUILD_STOPPED` and close on their own, with no client-side refresh/reconnect. Logs captured before the stop are present in the log stream output.

No production-code changes required. This phase is verification-only.

### Tests for User Story 3

- [ ] T025 [P] [US3] Add `shouldCloseStatusSseStream_whenBuildIsStopped` to `src/test/java/com/epam/aidial/deployment/manager/functional/h2/StopImageBuildFunctionalTest.java`. Seed `ImageDefinitionEntity` with `buildStatus = BUILDING`. Open an SSE connection to `/api/v1/images/builds/{id}/status` on a background thread (use the same SSE-consuming test helper `ImageBuildLogsService` tests already use — find it via `git grep SseEmitter` in the test tree). Trigger a status flip to `BUILD_STOPPED` via `imageDefinitionService.stopBuild(id)` directly (not via the HTTP endpoint — this isolates SSE behavior). Assert the consumer receives an event containing `BUILD_STOPPED` and the stream closes within the polling-interval + 1s budget.
- [ ] T026 [P] [US3] Add `shouldCloseLogsSseStream_whenBuildIsStopped_andPreserveCapturedLogs` to the same functional test. Seed a `BUILDING` entity with a few log lines. Open the logs SSE. Flip to `BUILD_STOPPED`. Assert the stream emits all pre-stop log lines, then a terminal `status` event with `BUILD_STOPPED`, then closes. Then call `GET /api/v1/images/builds/{id}/details` and assert the logs array still contains the same lines (FR-006).
- [X] T027 [P] [US3] Add a short smoke test named `shouldTreatBuildStoppedAsFinalStatus` that directly verifies `ImageStatus.BUILD_STOPPED.isFinal()` returns `true`. Create `src/test/java/com/epam/aidial/deployment/manager/model/ImageStatusTest.java` if no equivalent test exists. This guards against a regression where someone adds `BUILD_STOPPED(false)` by accident.

**Checkpoint**: All three user stories are independently functional and tested.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Quality gates, cross-vendor verification, and manual validation per quickstart.md.

- [X] T028 [P] Add functional test `StopImageBuildPostgresFunctionalTest` at `src/test/java/com/epam/aidial/deployment/manager/functional/postgres/StopImageBuildPostgresFunctionalTest.java` extending `PostgresFunctionalTests`. Run only the "happy path stop" and "rejection for BUILD_SUCCESSFUL" scenarios from T012/T022 — this is a targeted cross-vendor check that (a) the new `BUILD_STOPPED` varchar value round-trips correctly on PostgreSQL (`VARCHAR(32)`, no CHECK constraint), and (b) `@Lock(LockModeType.PESSIMISTIC_WRITE)` on `findByIdForUpdate` translates to the correct `SELECT ... FOR UPDATE` on PostgreSQL.
- [X] T029 [P] Add functional test `StopImageBuildSqlServerFunctionalTest` at `src/test/java/com/epam/aidial/deployment/manager/functional/sqlserver/StopImageBuildSqlServerFunctionalTest.java` extending `SqlServerFunctionalTests`. Same two-scenario targeted cross-vendor check for SQL Server, with the same pessimistic-lock verification (SQL Server uses `WITH (UPDLOCK, HOLDLOCK)` or equivalent — the test only cares that two concurrent stops don't both see `BUILDING`).
- [X] T030 Run `./gradlew checkstyleMain checkstyleTest` and fix any style violations (180-char lines, no wildcard imports, `CollectionUtils.isNotEmpty` / `StringUtils.isNotBlank` as per constitution §Code Style).
- [X] T031 Run `./gradlew testFast` and confirm all unit + H2 functional tests pass.
- [ ] T032 Run `./gradlew test` and confirm all Postgres and SQL Server functional tests pass (this is the PR-readiness gate per constitution §Tooling Commands).
- [ ] T033 Manual verification — execute the `quickstart.md` recipe against a local dev cluster with `./gradlew bootRun`. Validate SC-001 (stop within the hard-coded 30s window), SC-002 (rebuild succeeds first attempt), SC-003 (rejections don't mutate record), SC-004 (SSE closes), SC-005 (K8s Job gone within 60s), SC-006 (audit entry in `audit_activity` table records admin + image definition + timestamp).
- [X] T034 Verify `docs/db-schema.md` is unchanged (no Flyway migration in this feature) — if it shows drift, something went wrong in T013/T014/T015; diagnose before closing the task.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1, T001)**: No dependencies — start immediately. Output is informational; feeds T007.
- **Foundational (Phase 2, T002–T009)**: T002, T003, T004, T005, T007 are all in different files and parallel-safe. T006 depends on T004 and T005 (handler references the exceptions). T008 is a no-op placeholder (no docs entry needed for the hard-coded stop timeout). T009 depends on T007 (injects `app.image-build-stop-timeout-sec`).
- **User Story 1 (Phase 3, T010–T019)**: Depends on Phase 2 completion.
- **User Story 2 (Phase 4, T020–T024)**: Depends on Phase 3 (test-only phase; tests hit code built in US1).
- **User Story 3 (Phase 5, T025–T027)**: Depends on Phase 3 (test-only phase; relies on US1 code paths and the `isFinal` flag from T002).
- **Polish (Phase 6, T028–T034)**: T028 and T029 can start after Phase 3; T030–T034 depend on all prior phases.

### Within User Story 1

- Tests (T010, T011, T012) can be written in parallel before implementation. Verify they fail against an unmodified codebase.
- Implementation order: T013 → T014 → T015 → T016 → T017 → T018 → T019. T014 and T015 are both in the same file (`ImageDefinitionRepository.java`) so they are NOT parallel — sequence them. T018 depends on T016 and T009. T019 depends on T018.

### Parallel Opportunities

- **Foundational**: T002, T003, T004, T005, T007 run in parallel (different files). T006 sequential on T004/T005. T008 sequential on T007. T009 sequential on T007.
- **US1 tests**: T010, T011, T012 run in parallel.
- **US2 tests**: T020, T021, T022, T023, T024 run in parallel (different files, or disjoint methods in the same controller test file — split as separate nested classes or `@Nested` if needed to keep parallel).
- **US3 tests**: T025, T026, T027 run in parallel.
- **Polish cross-vendor tests**: T028 and T029 run in parallel (independent test files).

---

## Parallel Example: User Story 1 implementation phase

```bash
# After Phase 2 completes, write all US1 tests in parallel (they must fail first):
Task T010: "Unit test ImageBuildStopServiceTest in src/test/java/.../service/ImageBuildStopServiceTest.java"
Task T011: "Controller test case shouldStopBuild_whenBuildingAndAdmin in src/test/java/.../web/controller/none/ImageBuildControllerTest.java"
Task T012: "Functional test StopImageBuildFunctionalTest in src/test/java/.../functional/h2/StopImageBuildFunctionalTest.java"

# Then implement sequentially (each downstream depends on upstream):
Task T013 → T014 → T015 → T016 → T017 → T018 → T019
```

## Parallel Example: Phase 4 (US2)

```bash
# After Phase 3, run all US2 verification tasks simultaneously:
Task T020: "Parameterized controller test for non-running rejections"
Task T021: "Controller tests for not-found / forbidden / unauthenticated"
Task T022: "Parameterized functional test for state preservation on rejection"
Task T023: "Unit test shouldFailStopBuild_whenPipelineWonTheRace"
Task T024: "Unit test for ImageBuildNotInProgressException formatting"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (T001 — informational recon).
2. Complete Phase 2 (T002–T009 — shared primitives, config property, K8s delete).
3. Complete Phase 3 (T010–T019 — the stop flow itself).
4. **STOP and VALIDATE**: Run T012 + T031 (H2 functional + testFast). Manually verify a stop via `curl` against a running build.
5. Ship if ready — MVP delivers the full user-visible value promised by the spec.

### Incremental Delivery

1. Phase 1 + Phase 2 → primitives ready.
2. Phase 3 (US1) → MVP, testable in isolation via `./gradlew testFast --tests "*StopImageBuild*"`.
3. Phase 4 (US2) → contract hardening, still deployable as an additive test-only change.
4. Phase 5 (US3) → SSE-observer verification, still additive.
5. Phase 6 → cross-vendor + manual validation for PR-readiness.

### Suggested PR boundaries

- **PR 1**: Phases 1–3 (MVP). Ships the feature end-to-end with H2 tests.
- **PR 2** (optional): Phases 4–6 (hardening + cross-vendor). If T028/T029 reveal vendor-specific issues, fix and include in PR 2.

---

## Notes

- The `[P]` tasks are parallel-safe because they touch disjoint files.
- No Flyway migration is part of this feature (Phase 0 R8) — if any task here proposes a migration, it is wrong.
- `docs/configuration.md` IS updated (T008) because a new `@ConfigurationProperties` field is added (T007); constitution §Configuration documentation makes this mandatory.
- `docs/db-schema.md` is NOT regenerated (no migration — see T034).
- The `@LogExecution` annotation MUST appear on `ImageBuildStopService` (T018) and on any new `@Service` introduced; `@FullAdminOnly` MUST appear on the new DELETE endpoint (T019).
- The audit integration for the stop transition is automatic via Envers + `AuditRevisionListener` because T014 uses `saveAndFlush` on a managed entity — no explicit audit code is required; T012 case (d) guards against regression.
- Each task's "done" criterion is: code written, relevant tests passing, `./gradlew checkstyleMain checkstyleTest` green.
