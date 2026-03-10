# Tasks: Support Command and Args for All Deployment Types

**Input**: Design documents from `/specs/002-deployment-command-args/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: Not explicitly requested in spec. Test tasks included for manifest generators (unit) and deployment CRUD (functional) as they are critical for verifying backward compatibility (FR-009) and correctness.

**Organization**: Tasks are grouped by user story. Note that US1 (MCP), US2 (Adapter), and US3 (Interceptor) share `KnativeManifestGenerator`, so the generator update is in the foundational phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Foundational (Database Migration)

**Purpose**: Migrate command/args columns from inference_deployment to deployment table across all DB vendors

**⚠️ CRITICAL**: All subsequent phases depend on this migration being in place

- [x] T001 [P] Create PostgreSQL migration in `src/main/resources/db/migration/POSTGRES/V1.49__MoveCommandArgsToDeploymentTable.sql` — add `command` and `args` JSONB columns to `deployment` table, copy data from `inference_deployment`, drop columns from `inference_deployment`
- [x] T002 [P] Create H2 migration in `src/main/resources/db/migration/H2/V1.49__MoveCommandArgsToDeploymentTable.sql` — same logic as T001 adapted for H2 syntax
- [x] T003 [P] Create SQL Server migration in `src/main/resources/db/migration/MS_SQL_SERVER/V1.49__MoveCommandArgsToDeploymentTable.sql` — same logic as T001 using NVARCHAR(MAX) and SQL Server UPDATE...FROM syntax

**Checkpoint**: Database schema supports command/args at the base deployment level for all vendors

---

## Phase 2: Foundational (Entity, Domain Model, DTO, Mapper Changes)

**Purpose**: Move command/args fields from Inference-specific classes to base classes across all layers

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Entity Layer

- [x] T004 Add `command` and `args` fields (List\<String\>, `@JdbcTypeCode(SqlTypes.JSON)`, `@Nullable`) to `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/DeploymentEntity.java`
- [x] T005 Remove `command` and `args` fields from `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/InferenceDeploymentEntity.java`

### Domain Model Layer

- [x] T006 [P] Add `command` and `args` fields (List\<String\>, `@Nullable`) to `src/main/java/com/epam/aidial/deployment/manager/model/deployment/Deployment.java`
- [x] T007 [P] Add `command` and `args` fields (List\<String\>, `@Nullable`) to `src/main/java/com/epam/aidial/deployment/manager/model/deployment/CreateDeployment.java`
- [x] T008 [P] Remove `command` and `args` fields from `src/main/java/com/epam/aidial/deployment/manager/model/deployment/InferenceDeployment.java`
- [x] T009 [P] Remove `command` and `args` fields from `src/main/java/com/epam/aidial/deployment/manager/model/deployment/CreateInferenceDeployment.java`

### DTO Layer

- [x] T010 [P] Add `command` and `args` fields (String, `@Nullable`) to `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/CreateDeploymentRequestDto.java`
- [x] T011 [P] Add `command` and `args` fields (String, `@Nullable`) to `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/DeploymentDto.java`
- [x] T012 [P] Remove `command` and `args` fields from `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/CreateInferenceDeploymentRequestDto.java`
- [x] T013 [P] Remove `command` and `args` fields from `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/InferenceDeploymentDto.java`

### Mapper Layer

- [x] T014 Update `PersistenceDeploymentMapper.updateEntityFromDomain()` in `src/main/java/com/epam/aidial/deployment/manager/dao/mapper/PersistenceDeploymentMapper.java` — move command/args update from Inference-specific block to base block
- [x] T015 Verify `DeploymentDtoMapper` in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/DeploymentDtoMapper.java` auto-discovers `stringToList`/`listToString` for base-level command/args fields (MapStruct should handle this; fix mapping annotations if needed)
- [x] T016 Verify `DeploymentMapper` in `src/main/java/com/epam/aidial/deployment/manager/mapper/DeploymentMapper.java` correctly maps base-level command/args from CreateDeployment to Deployment (inherited fields should work automatically)

**Checkpoint**: All layers (entity → domain → DTO) have command/args at base level. Inference-specific fields removed. Build should compile: `./gradlew checkstyleMain`

---

## Phase 3: User Story 1 — Configure Custom Command for MCP Deployment (Priority: P1) 🎯 MVP

**Goal**: MCP deployments support custom command and args via the API, applied to the Knative service manifest

**Independent Test**: Create an MCP deployment with command/args, verify the Knative service manifest contains the specified values

### Implementation for User Story 1

- [x] T017 [US1] Update `KnativeManifestGenerator.serviceConfig()` in `src/main/java/com/epam/aidial/deployment/manager/service/manifest/KnativeManifestGenerator.java` — add `@Nullable List<String> command` and `@Nullable List<String> args` parameters; apply to container spec with null-check guards (follow pattern from `InferenceManifestGenerator` lines 76-84)
- [x] T018 [US1] Update all callers of `KnativeManifestGenerator.serviceConfig()` to pass `deployment.getCommand()` and `deployment.getArgs()` — search for usages in MCP, Adapter, and Interceptor deployment services
- [x] T019 [US1] Add unit tests for command/args in `src/test/java/com/epam/aidial/deployment/manager/service/manifest/KnativeManifestGeneratorTest.java` — test cases: command+args provided, command only, args only, neither (null), verify container spec
- [x] T020 [US1] Add/update MCP deployment functional tests to cover create and update with command/args — verify round-trip (create with command/args → GET returns same values) and clearing (update without command/args → values become null)

**Checkpoint**: MCP deployments fully support command/args. Knative manifest includes entrypoint override. Round-trip verified.

---

## Phase 4: User Story 2 — Configure Custom Command for Adapter Deployment (Priority: P2)

**Goal**: Adapter deployments support custom command and args via the API

**Independent Test**: Create an Adapter deployment with command/args, verify the Knative service manifest contains the specified values

### Implementation for User Story 2

- [x] T021 [US2] Add/update Adapter deployment functional tests to cover create and update with command/args — verify round-trip and clearing behavior (Adapter shares KnativeManifestGenerator updated in T017; callers updated in T018)

**Checkpoint**: Adapter deployments fully support command/args. No additional code changes needed beyond T017-T018.

---

## Phase 5: User Story 3 — Configure Custom Command for Interceptor Deployment (Priority: P3)

**Goal**: Interceptor deployments support custom command and args via the API

**Independent Test**: Create an Interceptor deployment with command/args, verify the Knative service manifest contains the specified values

### Implementation for User Story 3

- [x] T022 [US3] Add/update Interceptor deployment functional tests to cover create and update with command/args — verify round-trip and clearing behavior (Interceptor shares KnativeManifestGenerator updated in T017; callers updated in T018)

**Checkpoint**: Interceptor deployments fully support command/args. No additional code changes needed beyond T017-T018.

---

## Phase 6: User Story 4 — Configure Custom Command for NIM Deployment (Priority: P3)

**Goal**: NIM deployments support custom command and args via the API, applied to the NIM service manifest

**Independent Test**: Create a NIM deployment with command/args, verify the manifest contains the specified values

### Implementation for User Story 4

- [x] T023 [US4] Update `NimManifestGenerator.serviceConfig()` in `src/main/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGenerator.java` — add `@Nullable List<String> command` and `@Nullable List<String> args` parameters; apply to container spec with null-check guards
- [x] T024 [US4] Update all callers of `NimManifestGenerator.serviceConfig()` to pass `deployment.getCommand()` and `deployment.getArgs()`
- [x] T025 [US4] Add unit tests for command/args in `src/test/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGeneratorTest.java` — test cases: command+args provided, command only, args only, neither (null)
- [x] T026 [US4] Add/update NIM deployment functional tests to cover create and update with command/args — verify round-trip and clearing behavior

**Checkpoint**: NIM deployments fully support command/args.

---

## Phase 7: User Story 5 — Retrieve Deployment with Command and Args (Priority: P1)

**Goal**: All deployment types return command and args in GET responses

**Independent Test**: Create deployments of each type with and without command/args, verify GET responses include/omit the fields correctly

### Implementation for User Story 5

- [x] T027 [US5] Verify existing Inference deployment functional tests still pass with command/args at base level — run full test suite to confirm backward compatibility (FR-009)
- [x] T028 [US5] Add functional test verifying that deployments created without command/args return null for these fields in GET response across all deployment types

**Checkpoint**: Retrieval works correctly for all deployment types with and without command/args.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Final validation and cleanup

- [x] T029 Run `./gradlew checkstyleMain checkstyleTest` to verify code style compliance
- [x] T030 Run `./gradlew clean build` — full build with all tests across all DB vendors
- [x] T031 Verify OpenAPI spec auto-generates correctly with new command/args fields on base DTOs (check Swagger UI or generated spec)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (DB Migration)**: No dependencies — start immediately
- **Phase 2 (Entity/Domain/DTO/Mapper)**: Depends on Phase 1 completion — BLOCKS all user stories
- **Phase 3 (US1 - MCP)**: Depends on Phase 2 — also updates KnativeManifestGenerator used by US2/US3
- **Phase 4 (US2 - Adapter)**: Depends on Phase 2 + T017-T018 from Phase 3
- **Phase 5 (US3 - Interceptor)**: Depends on Phase 2 + T017-T018 from Phase 3
- **Phase 6 (US4 - NIM)**: Depends on Phase 2 only — independent of Phases 3-5
- **Phase 7 (US5 - Retrieval)**: Depends on Phase 2 — can run after any user story phase
- **Phase 8 (Polish)**: Depends on all phases complete

### User Story Dependencies

- **US1 (MCP, P1)**: After Phase 2 — updates shared KnativeManifestGenerator
- **US2 (Adapter, P2)**: After Phase 2 + US1's T017-T018 (shared generator)
- **US3 (Interceptor, P3)**: After Phase 2 + US1's T017-T018 (shared generator)
- **US4 (NIM, P3)**: After Phase 2 — fully independent of US1-US3
- **US5 (Retrieval, P1)**: After Phase 2 — verification only, no code changes

### Parallel Opportunities

- T001, T002, T003 (all DB migrations) can run in parallel
- T006-T013 (domain model + DTO changes) can run in parallel
- T017-T018 (Knative generator) and T023-T024 (NIM generator) can run in parallel after Phase 2
- US4 (NIM) can run fully in parallel with US1-US3 (Knative types)
- US2 and US3 functional tests (T021, T022) can run in parallel

---

## Parallel Example: Phase 2

```
# Launch all domain model changes together:
Task T006: "Add command/args to Deployment.java"
Task T007: "Add command/args to CreateDeployment.java"
Task T008: "Remove command/args from InferenceDeployment.java"
Task T009: "Remove command/args from CreateInferenceDeployment.java"

# Launch all DTO changes together:
Task T010: "Add command/args to CreateDeploymentRequestDto.java"
Task T011: "Add command/args to DeploymentDto.java"
Task T012: "Remove command/args from CreateInferenceDeploymentRequestDto.java"
Task T013: "Remove command/args from InferenceDeploymentDto.java"
```

## Parallel Example: Manifest Generators (after Phase 2)

```
# These can run in parallel (different generators):
Task T017: "Update KnativeManifestGenerator.serviceConfig()"
Task T023: "Update NimManifestGenerator.serviceConfig()"
```

---

## Implementation Strategy

### MVP First (User Story 1 + Foundational)

1. Complete Phase 1: DB Migration (T001-T003)
2. Complete Phase 2: Entity/Domain/DTO/Mapper (T004-T016)
3. Complete Phase 3: US1 - MCP (T017-T020)
4. **STOP and VALIDATE**: MCP deployments work end-to-end with command/args
5. Deploy/demo if ready

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready
2. Add US1 (MCP) → Test → Deploy (MVP!)
3. Add US2 (Adapter) + US3 (Interceptor) → Test → Deploy (shared generator already done)
4. Add US4 (NIM) → Test → Deploy
5. US5 (Retrieval verification) + Polish → Final validation

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- US2 and US3 are lightweight phases (functional tests only) because KnativeManifestGenerator is shared with US1
- US4 (NIM) is fully independent and can be parallelized with US1-US3
- Total: 31 tasks across 8 phases
- Commit after each phase for clean git history
