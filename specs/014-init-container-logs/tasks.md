# Tasks: Init Container Logs

**Input**: Design documents from `specs/014-init-container-logs/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: No setup needed — existing project, no new dependencies or migrations.

(No tasks — project structure and dependencies already in place.)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: New model classes and interface changes that both user stories depend on.

- [x] T001 [P] Create `ContainerType` enum (`INIT`, `REGULAR`) in `src/main/java/com/epam/aidial/deployment/manager/model/ContainerType.java`
- [x] T002 [P] Create `ContainerDetails` model class (fields: `name`, `type`, `state`, `stateReason`) in `src/main/java/com/epam/aidial/deployment/manager/model/ContainerDetails.java`
- [x] T003 [P] Create `ContainerDetailsDto` record (fields: `name`, `type`, `state`, `stateReason`) in `src/main/java/com/epam/aidial/deployment/manager/web/dto/ContainerDetailsDto.java`
- [x] T004 Add `containerName` parameter (nullable `String`) to `getContainerResourceForLogs` in `DeploymentManager` interface at `src/main/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentManager.java`

**Checkpoint**: Foundation ready — new types created and interface updated.

---

## Phase 3: User Story 1 - Retrieve init container logs (Priority: P1) :dart: MVP

**Goal**: Users can stream logs from any container (including init containers) in a deployment pod by specifying the container name.

**Independent Test**: Call the log endpoint with `containerName` targeting an init container of a crashed deployment. Verify the SSE stream returns the init container's log output. Call without `containerName` and verify backward-compatible default behavior.

### Implementation for User Story 1

- [x] T005 [US1] Modify `getContainerResourceForLogs` in `AbstractDeploymentManager` to accept `containerName` parameter — when non-null, use it directly instead of calling `getContainerName(pod)`. File: `src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractDeploymentManager.java`
- [x] T006 [US1] Modify `findContainerStatus` in `AbstractDeploymentManager` to search both `pod.getStatus().getContainerStatuses()` and `pod.getStatus().getInitContainerStatuses()`, and track whether the found container is an init container. File: `src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractDeploymentManager.java`
- [x] T007 [US1] Modify `assertContainerLoggable` in `AbstractDeploymentManager` to allow terminated init containers to be loggable without `previous=true` (terminated is the normal final state for init containers). File: `src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractDeploymentManager.java`
- [x] T008 [US1] Add `containerName` parameter to `streamLogs` method in `DeploymentLogsService` and pass it through to `getContainerResourceForLogs`. File: `src/main/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentLogsService.java`
- [x] T009 [US1] Add optional `containerName` `@RequestParam` to `subscribeToLogs` in `DeploymentController` and pass it to `DeploymentLogsService.streamLogs`. Update `@Operation`/`@ApiResponse` annotations. File: `src/main/java/com/epam/aidial/deployment/manager/web/controller/DeploymentController.java`
- [x] T010 [US1] Update `DeploymentLogsServiceTest` — update existing mocks for the new `containerName` parameter and add test cases: streaming with explicit container name, streaming with null container name (backward compat), error for nonexistent container name. File: `src/test/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentLogsServiceTest.java`
- [x] T011 [P] [US1] Update `NimDeploymentManagerTest` — add tests for `getContainerResourceForLogs` with explicit `containerName`, with init container (terminated, should succeed), and with nonexistent container name. File: `src/test/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManagerTest.java`
- [x] T012 [P] [US1] Update `KnativeDeploymentManagerTest` — add tests for `getContainerResourceForLogs` with explicit `containerName` and with init container. File: `src/test/java/com/epam/aidial/deployment/manager/service/deployment/KnativeDeploymentManagerTest.java`
- [x] T013 [P] [US1] Update `InferenceDeploymentManagerTest` — add tests for `getContainerResourceForLogs` with explicit `containerName` and with init container. File: `src/test/java/com/epam/aidial/deployment/manager/service/deployment/InferenceDeploymentManagerTest.java`

**Checkpoint**: Users can stream logs from any container by name. Default behavior preserved. Run `./gradlew testFast` to validate.

---

## Phase 4: User Story 2 - List available containers for a pod (Priority: P2)

**Goal**: Pod listing response includes container details (name, type, state) so users can discover which containers exist before requesting logs.

**Independent Test**: Call `GET /api/v1/deployments/{id}/pods` for a deployment with init containers. Verify the response includes `containers` array with each container's name, type (`INIT`/`REGULAR`), state, and stateReason.

### Implementation for User Story 2

- [x] T014 [US2] Add `List<ContainerDetails> containers` field to `PodInfo` model. File: `src/main/java/com/epam/aidial/deployment/manager/model/PodInfo.java`
- [x] T015 [US2] Add `List<ContainerDetailsDto> containers` field to `PodInfoDto` record. File: `src/main/java/com/epam/aidial/deployment/manager/web/dto/PodInfoDto.java`
- [x] T016 [US2] Add `List<ContainerDetailsDto> toContainerDetailsDto(List<ContainerDetails>)` mapping method to `DeploymentDtoMapper`. File: `src/main/java/com/epam/aidial/deployment/manager/web/mapper/DeploymentDtoMapper.java`
- [x] T017 [US2] Modify `toPodInfo` in `AbstractDeploymentManager` to build `List<ContainerDetails>` from `pod.getSpec().getInitContainers()` (type=INIT) and `pod.getSpec().getContainers()` (type=REGULAR), matching each with its status from `initContainerStatuses`/`containerStatuses` to extract state and stateReason. Set on `PodInfo`. File: `src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractDeploymentManager.java`

**Checkpoint**: Pod listing now includes container details. Run `./gradlew testFast` to validate.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Spec documentation and final validation.

- [x] T018 Update "Stream pod logs via SSE" requirement in `specs/deployments/spec.md` to document the new `containerName` parameter and init container support
- [x] T019 Run `./gradlew checkstyleMain checkstyleTest` and fix any style violations
- [x] T020 Run `./gradlew testFast` — full validation pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 2)**: No dependencies — can start immediately. T001-T003 are parallel. T004 is parallel with T001-T003.
- **User Story 1 (Phase 3)**: Depends on T004 (interface change). T005-T009 are sequential (same file or dependent). T011-T013 are parallel (different test files).
- **User Story 2 (Phase 4)**: Depends on T001-T003 (new model types). Independent of User Story 1.
- **Polish (Phase 5)**: Depends on all user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: Depends on Foundational (T004). No dependency on US2.
- **User Story 2 (P2)**: Depends on Foundational (T001-T003). No dependency on US1.

### Parallel Opportunities

- T001, T002, T003, T004 can all run in parallel (Phase 2)
- T011, T012, T013 can run in parallel (different test files)
- US1 and US2 can proceed in parallel after Phase 2

---

## Parallel Example: Phase 2

```text
# Launch all foundational tasks together:
Task T001: "Create ContainerType enum"
Task T002: "Create ContainerDetails model"
Task T003: "Create ContainerDetailsDto record"
Task T004: "Update DeploymentManager interface"
```

## Parallel Example: User Story 1 Tests

```text
# Launch deployment manager tests in parallel:
Task T011: "Update NimDeploymentManagerTest"
Task T012: "Update KnativeDeploymentManagerTest"
Task T013: "Update InferenceDeploymentManagerTest"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 2: Foundational (T001-T004)
2. Complete Phase 3: User Story 1 (T005-T013)
3. **STOP and VALIDATE**: `./gradlew testFast` — users can now retrieve init container logs
4. Deploy/demo if ready

### Incremental Delivery

1. Complete Foundational → New types and interface ready
2. Add User Story 1 → Init container log streaming works → Deploy (MVP!)
3. Add User Story 2 → Container discovery in pod listing → Deploy
4. Polish → Spec docs updated, final validation

---

## Notes

- No database migrations needed — purely read-only Kubernetes API
- No new dependencies — reuses existing Fabric8 client patterns
- Backward compatibility is critical — all existing API calls must work unchanged
- Commit after each task or logical group
