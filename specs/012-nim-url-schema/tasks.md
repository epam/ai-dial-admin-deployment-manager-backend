# Tasks: NIM Service URL Schema Prefix

**Input**: Design documents from `/specs/012-nim-url-schema/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Test tasks are included as the existing test file (`NimDeploymentManagerTest.java`) already covers `resolveServiceUrl` and must be updated to validate new behavior.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configuration property and YAML binding needed by both user stories

- [x] T001 Add `urlSchema` field to `src/main/java/com/epam/aidial/deployment/manager/configuration/NimDeployProperties.java`
- [x] T002 Add `url-schema: ${K8S_NIM_DEPLOYMENT_URL_SCHEMA:}` under `app.nim.deploy` in `src/main/resources/application.yml`

**Checkpoint**: New config property is available for injection. No behavioral changes yet.

---

## Phase 2: User Story 1 - Default Schema Prefix (Priority: P1) :dart: MVP

**Goal**: `resolveServiceUrl()` prepends `http://` for cluster-internal endpoints and `https://` for external endpoints. Already-prefixed URLs are returned unchanged. Null/empty endpoints still return null.

**Independent Test**: Deploy a NIM service, resolve its URL, verify the schema prefix matches endpoint type.

### Implementation for User Story 1

- [x] T003 [US1] Modify `resolveServiceUrl()` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManager.java` — after selecting the endpoint URL, check if it already has a schema prefix; if not, prepend `http://` when `useClusterInternalUrl` is true or `https://` when false. Return null unchanged for null/empty endpoints.
- [x] T004 [US1] Update existing test `shouldReturnExternalEndpoint` in `src/test/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManagerTest.java` — set endpoint values without schema prefix (e.g., `external:8000`) and assert result starts with `https://`
- [x] T005 [US1] Update existing test `shouldReturnClusterEndpointWhenUseClusterInternalUrlIsTrue` in `src/test/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManagerTest.java` — set endpoint values without schema prefix (e.g., `cluster-internal:8000`) and assert result starts with `http://`
- [x] T006 [US1] Add test `shouldPreserveExistingSchemaPrefix` in `src/test/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManagerTest.java` — set endpoint to `https://already-prefixed:8000` and assert no double prefix
- [x] T007 [US1] Run `./gradlew testFast` to verify all tests pass
- [x] T008 [US1] Run `./gradlew checkstyleMain checkstyleTest` to verify code style

**Checkpoint**: Default schema prefix behavior is fully functional and tested. URLs returned by the system always include a schema prefix.

---

## Phase 3: User Story 2 - Schema Override (Priority: P2)

**Goal**: When `urlSchema` config property is set, its value is used as the schema prefix for all resolved NIM URLs regardless of endpoint type. Override values with or without `://` suffix are handled correctly.

**Independent Test**: Set `K8S_NIM_DEPLOYMENT_URL_SCHEMA=https`, resolve a cluster-internal URL, verify it uses `https://` instead of the default `http://`.

### Implementation for User Story 2

- [x] T009 [US2] Extend `resolveServiceUrl()` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManager.java` — before applying default schema logic, check if `nimDeployProperties.getUrlSchema()` is non-empty; if so, normalize the value (strip trailing `://` if present) and use it as the schema prefix instead of the default
- [x] T010 [P] [US2] Add test `shouldUseOverriddenSchemaForClusterEndpoint` in `src/test/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManagerTest.java` — set `urlSchema` to `https`, use cluster-internal mode, assert URL starts with `https://`
- [x] T011 [P] [US2] Add test `shouldUseOverriddenSchemaForExternalEndpoint` in `src/test/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManagerTest.java` — set `urlSchema` to `http`, use external mode, assert URL starts with `http://`
- [x] T012 [P] [US2] Add test `shouldNormalizeOverrideValueWithProtocolSuffix` in `src/test/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManagerTest.java` — set `urlSchema` to `https://`, assert URL starts with `https://` (not `https:///`)
- [x] T013 [US2] Add test `shouldUseDefaultSchemaWhenOverrideIsEmpty` in `src/test/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManagerTest.java` — set `urlSchema` to empty string, assert default behavior applies
- [x] T014 [US2] Run `./gradlew testFast` to verify all tests pass
- [x] T015 [US2] Run `./gradlew checkstyleMain checkstyleTest` to verify code style

**Checkpoint**: Schema override is fully functional. Both default and override behaviors are tested.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Documentation and final validation

- [x] T016 [P] Add `K8S_NIM_DEPLOYMENT_URL_SCHEMA` entry to the NIM Configuration table in `docs/configuration.md` with property path `app.nim.deploy.url-schema`, default empty, and description of behavior
- [x] T017 Run `./gradlew clean build` for full build validation

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **User Story 1 (Phase 2)**: Depends on Phase 1 (T001, T002)
- **User Story 2 (Phase 3)**: Depends on Phase 2 (builds on default schema logic from T003)
- **Polish (Phase 4)**: Depends on Phase 3 completion

### User Story Dependencies

- **User Story 1 (P1)**: Depends only on Setup phase — core default behavior
- **User Story 2 (P2)**: Depends on User Story 1 — extends the schema prefixing logic with override support

### Within Each User Story

- Implementation before tests (tests validate the new behavior)
- All tests pass before moving to next phase
- Checkstyle passes before moving to next phase

### Parallel Opportunities

- T001 and T002 can run in parallel (different files)
- T004, T005, T006 can be written in parallel after T003 (different test methods)
- T010, T011, T012 can run in parallel (different test methods, marked [P])
- T016 can run in parallel with T017

---

## Parallel Example: User Story 2

```text
# After T009 (implementation) completes, launch test tasks in parallel:
Task T010: "Add test shouldUseOverriddenSchemaForClusterEndpoint"
Task T011: "Add test shouldUseOverriddenSchemaForExternalEndpoint"
Task T012: "Add test shouldNormalizeOverrideValueWithProtocolSuffix"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T002)
2. Complete Phase 2: User Story 1 (T003–T008)
3. **STOP and VALIDATE**: All NIM URLs now have schema prefixes with correct defaults
4. Deploy/demo if ready — this alone solves the core problem

### Incremental Delivery

1. Setup → Config property ready
2. Add User Story 1 → Default schema prefixing works → Deploy (MVP!)
3. Add User Story 2 → Override capability available → Deploy
4. Polish → Documentation updated, full build validated

---

## Notes

- [P] tasks = different files or independent test methods, no dependencies
- [Story] label maps task to specific user story for traceability
- Total tasks: 17
- User Story 1: 6 tasks (T003–T008)
- User Story 2: 7 tasks (T009–T015)
- Setup: 2 tasks (T001–T002)
- Polish: 2 tasks (T016–T017)
