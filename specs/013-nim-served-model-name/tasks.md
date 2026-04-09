# Tasks: NIM Served Model Name Override

**Input**: Design documents from `/specs/013-nim-served-model-name/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md

**Tests**: Included — unit tests follow existing `NimManifestGeneratorTest` patterns.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

---

## Phase 1: Setup

**Purpose**: No project setup needed — this feature modifies existing files only.

*No tasks in this phase.*

---

## Phase 2: Foundational

**Purpose**: No foundational work needed — all infrastructure (NimManifestGenerator, test harness, NIM env var mechanism) already exists.

*No tasks in this phase.*

---

## Phase 3: User Story 1 - Default Model Name Set to Deployment ID (Priority: P1) MVP

**Goal**: Auto-inject `NIM_SERVED_MODEL_NAME` environment variable with the deployment identifier as value when not explicitly provided by the user.

**Independent Test**: Create a NIM deployment manifest without any `NIM_SERVED_MODEL_NAME` in env vars and verify the generated NIMService manifest contains `NIM_SERVED_MODEL_NAME=<deploymentName>` in `spec.env[]`.

### Implementation for User Story 1

- [x] T001 [US1] Add `NIM_SERVED_MODEL_NAME_ENV` constant and `setServedModelNameIfNotSet()` private method to `src/main/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGenerator.java`. The method checks if `NIM_SERVED_MODEL_NAME` exists in either `simpleEnvs` or `sensitiveEnvs` lists (by name). If absent, adds `NIM_SERVED_MODEL_NAME=<deploymentName>` to the env list via the `ListMapper<Env>`. If present, logs info and returns. Reference: `InferenceManifestGenerator.setModelNameIfNotSet()` at line 125.
- [x] T002 [US1] Call `setServedModelNameIfNotSet()` in the `serviceConfig()` method of `src/main/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGenerator.java`, after `applySimpleEnvs()` and `applySensitiveEnvs()` (after current line 78). Pass `name`, `envs`, `sensitiveEnv`, and the `envListMapper`.
- [x] T003 [US1] Add unit test `shouldSetServedModelName_whenNotProvidedByUser()` to `src/test/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGeneratorTest.java`. Test: call `serviceConfig()` with empty env lists, assert the generated `NIMService.spec.env[]` contains an entry with name `NIM_SERVED_MODEL_NAME` and value equal to the deployment name.

**Checkpoint**: Default model name injection works. NIM deployments created without explicit `NIM_SERVED_MODEL_NAME` get the deployment ID as model name.

---

## Phase 4: User Story 2 - Explicit Model Name Override (Priority: P2)

**Goal**: When a user explicitly provides `NIM_SERVED_MODEL_NAME` via simple or sensitive env vars, the system preserves their value and does not override it.

**Independent Test**: Create a NIM deployment manifest with explicit `NIM_SERVED_MODEL_NAME` in env vars and verify the generated NIMService manifest preserves the user-provided value with no duplicate entry.

### Implementation for User Story 2

- [x] T004 [P] [US2] Add unit test `shouldNotOverrideServedModelName_whenProvidedAsSimpleEnvVar()` to `src/test/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGeneratorTest.java`. Test: call `serviceConfig()` with a `SimpleEnvVar("NIM_SERVED_MODEL_NAME", "custom-model-name")` in the simple env list, assert the generated env contains the user-provided value and does NOT contain the deployment name as a duplicate.
- [x] T005 [P] [US2] Add unit test `shouldNotOverrideServedModelName_whenProvidedAsSensitiveEnvVar()` to `src/test/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGeneratorTest.java`. Test: call `serviceConfig()` with a `SensitiveEnvVar("NIM_SERVED_MODEL_NAME", ...)` in the sensitive env list, assert the deployment name is NOT injected as a default.

**Checkpoint**: Both user stories complete. Default injection and explicit override both verified.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [x] T006 Run `./gradlew checkstyleMain checkstyleTest` to verify code style compliance
- [x] T007 Run `./gradlew testFast --tests "com.epam.aidial.deployment.manager.service.manifest.NimManifestGeneratorTest"` to verify all tests pass
- [x] T008 Run `./gradlew testFast` to verify no regressions across the test suite

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: N/A
- **Foundational (Phase 2)**: N/A
- **User Story 1 (Phase 3)**: Can start immediately — T001 → T002 → T003 (sequential)
- **User Story 2 (Phase 4)**: Depends on Phase 3 completion (T001-T002 must exist for tests to validate override behavior)
- **Polish (Phase 5)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: No dependencies — implements core method + default behavior
- **User Story 2 (P2)**: Depends on US1 (the `setServedModelNameIfNotSet()` method must exist for override tests to be meaningful)

### Within Each User Story

- T001 before T002 (method must exist before calling it)
- T002 before T003 (call site must exist before testing)
- T004 and T005 are parallelizable (different test methods, same file but independent)

### Parallel Opportunities

- T004 and T005 can run in parallel (both are independent test additions)
- T006, T007, T008 are sequential (verification chain)

---

## Parallel Example: User Story 2

```bash
# Launch both override tests together:
Task: "Test shouldNotOverrideServedModelName_whenProvidedAsSimpleEnvVar in NimManifestGeneratorTest.java"
Task: "Test shouldNotOverrideServedModelName_whenProvidedAsSensitiveEnvVar in NimManifestGeneratorTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 3: User Story 1 (T001-T003)
2. **STOP and VALIDATE**: Run `NimManifestGeneratorTest` — default injection works
3. Continue to User Story 2 for override verification

### Incremental Delivery

1. US1 (T001-T003) → Default model name injection working
2. US2 (T004-T005) → Override behavior verified
3. Polish (T006-T008) → Code style + full regression check

---

## Notes

- All changes are in 2 existing files — no new files created
- Total production code: ~15 lines (constant + method + call site)
- Total test code: ~60 lines (3 test methods)
- No database migrations, no API changes, no mapper changes
