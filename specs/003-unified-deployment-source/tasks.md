# Tasks: Unified Deployment Source Model

**Input**: Design documents from `/specs/003-unified-deployment-source/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: Test tasks are included as this feature modifies core data models, API contracts, and database schema across multiple deployment types — test coverage is essential for confidence.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the unified Source sealed interface hierarchy at all layers (domain, persistence, DTO) that all user stories depend on.

- [ ] T001 [P] Create `Source` sealed interface with Jackson `$type` discriminator and four subtypes in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/Source.java`
- [ ] T002 [P] Create `InternalImageSource` record (imageDefinitionId, imageDefinitionType, imageDefinitionName, imageDefinitionVersion) in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/InternalImageSource.java`
- [ ] T003 [P] Create `ImageReferenceSource` record (imageReference) in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/ImageReferenceSource.java`
- [ ] T004 [P] Rename `InferenceDeploymentHuggingFaceSource` to `HuggingFaceSource` implementing `Source` in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/HuggingFaceSource.java`
- [ ] T005 [P] Rename `NimDeploymentNgcRegistrySource` to `NgcRegistrySource` implementing `Source` in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/NgcRegistrySource.java`
- [ ] T006 Remove `InferenceDeploymentSource` interface in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/InferenceDeploymentSource.java`
- [ ] T007 Remove `NimDeploymentSource` interface in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/NimDeploymentSource.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Modify base domain models, entity layer, persistence mappers, and DTO layer to use the unified Source. These changes MUST complete before any user story work.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Domain Model Changes

- [ ] T008 Replace `imageDefinitionId`, `imageDefinitionType`, `imageDefinitionName`, `imageDefinitionVersion` with `Source source` field on `Deployment` base class in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/Deployment.java`
- [ ] T009 Replace `imageDefinitionId`, `imageDefinitionType`, `imageDefinitionName`, `imageDefinitionVersion` with `Source source` field on `CreateDeployment` base class in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/CreateDeployment.java`
- [ ] T010 [P] Change `InferenceDeployment.source` type from `InferenceDeploymentSource` to `Source` in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/InferenceDeployment.java`
- [ ] T011 [P] Change `NimDeployment.source` type from `NimDeploymentSource` to `Source` in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/NimDeployment.java`
- [ ] T012 [P] Remove redundant Lombok annotations from empty subclasses: `CreateAdapterDeployment`, `CreateInterceptorDeployment`, `AdapterDeployment`, `InterceptorDeployment`, `AdapterDeploymentEntity`, `InterceptorDeploymentEntity`

### Persistence Layer Changes

- [ ] T013 [P] Create `PersistenceSource` sealed interface with Jackson `$type` discriminator in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/PersistenceSource.java`
- [ ] T014 [P] Create `PersistenceInternalImageSource` record in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/PersistenceInternalImageSource.java`
- [ ] T015 [P] Create `PersistenceImageReferenceSource` record in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/PersistenceImageReferenceSource.java`
- [ ] T016 [P] Create `PersistenceHuggingFaceSource` record (replacing `PersistenceInferenceDeploymentHuggingFaceSource`) in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/PersistenceHuggingFaceSource.java`
- [ ] T017 [P] Create `PersistenceNgcRegistrySource` record (replacing `PersistenceNimDeploymentNgcRegistrySource`) in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/PersistenceNgcRegistrySource.java`
- [ ] T018 Modify `DeploymentEntity` to replace `imageDefinitionType`, `imageDefinitionName`, `imageDefinitionVersion` columns with `@JdbcTypeCode(SqlTypes.JSON) PersistenceSource source` column; retain `imageDefinitionId` in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/DeploymentEntity.java`
- [ ] T019 [P] Remove `source` field from `InferenceDeploymentEntity` in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/InferenceDeploymentEntity.java`
- [ ] T020 [P] Remove `source` field from `NimDeploymentEntity` in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/NimDeploymentEntity.java`
- [ ] T021 Remove old persistence source classes: `PersistenceInferenceDeploymentSource`, `PersistenceInferenceDeploymentHuggingFaceSource`, `PersistenceNimDeploymentSource`, `PersistenceNimDeploymentNgcRegistrySource`

### DTO Layer Changes

- [ ] T022 [P] Create `DeploymentSourceDto` sealed interface with `$type` discriminator (subtypes: `internal_image`, `image_reference`) in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/DeploymentSourceDto.java`
- [ ] T023 [P] Create `InternalImageDeploymentSourceDto` record (@NotNull imageDefinitionId, imageDefinitionName, imageDefinitionVersion) in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/InternalImageDeploymentSourceDto.java`
- [ ] T024 [P] Create `ImageReferenceDeploymentSourceDto` record (@NotNull imageReference) in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/ImageReferenceDeploymentSourceDto.java`
- [ ] T025 [P] Create `CreateDeploymentSourceRequestDto` sealed interface with `$type` discriminator in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/CreateDeploymentSourceRequestDto.java`
- [ ] T026 [P] Create `CreateInternalImageDeploymentSourceRequestDto` record with @AssertTrue `isValidImageReference()` validation in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/CreateInternalImageDeploymentSourceRequestDto.java`
- [ ] T027 [P] Create `CreateImageReferenceDeploymentSourceRequestDto` record (@NotNull @ValidDockerImageName imageReference) in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/CreateImageReferenceDeploymentSourceRequestDto.java`
- [ ] T028 Modify `ImageBasedDeploymentDto` to replace `imageDefinitionId`, `imageDefinitionType`, `imageDefinitionName`, `imageDefinitionVersion` with `@NotNull @Valid DeploymentSourceDto source` in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/ImageBasedDeploymentDto.java`
- [ ] T029 Modify `CreateImageBasedDeploymentRequestDto` to replace image definition fields with `@NotNull @Valid CreateDeploymentSourceRequestDto source` in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/CreateImageBasedDeploymentRequestDto.java`

### Mapper Layer Changes

- [ ] T030 Update `PersistenceDeploymentMapper` to map unified `Source` ↔ `PersistenceSource` using @SubclassMapping in `src/main/java/com/epam/aidial/deployment/manager/dao/mapper/PersistenceDeploymentMapper.java`
- [ ] T031 Refactor `DeploymentDtoMapper` to use `@Mapping(target = "source", ignore = true)` on base `toDeploymentDto` and `toCreateDeployment` methods, add `@AfterMapping` methods for source conversion, remove redundant concrete mapper methods in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/DeploymentDtoMapper.java`
- [ ] T032 Update `DeploymentInfoDto` to use source field if applicable in `src/main/java/com/epam/aidial/deployment/manager/web/dto/DeploymentInfoDto.java`

### Repository Changes

- [ ] T033 Update `DeploymentRepository.updateImageDefinitionForDeployments()` to accept `ImageType` parameter and update the `source` JSON column in `src/main/java/com/epam/aidial/deployment/manager/dao/repository/DeploymentRepository.java`
- [ ] T034 Update `DeploymentJpaRepository` query methods to work with unified source column in `src/main/java/com/epam/aidial/deployment/manager/dao/jpa/DeploymentJpaRepository.java`

### Database Migration

- [ ] T035 Create abstract base migration `V1_50__UnifyDeploymentSourceBase` with common migration logic (migrate NIM sources, Inference sources, internal image sources; drop old columns) in `src/main/java/db/migration/common/V1_50__UnifyDeploymentSourceBase.java`
- [ ] T036 [P] Create H2-specific migration `V1_50__UnifyDeploymentSource` extending base in `src/main/java/db/migration/H2/V1_50__UnifyDeploymentSource.java`
- [ ] T037 [P] Create PostgreSQL-specific migration `V1_50__UnifyDeploymentSource` extending base in `src/main/java/db/migration/POSTGRES/V1_50__UnifyDeploymentSource.java`
- [ ] T038 [P] Create MSSQL-specific migration `V1_50__UnifyDeploymentSource` with `isjson` check constraint, extending base in `src/main/java/db/migration/MS_SQL_SERVER/V1_50__UnifyDeploymentSource.java`

**Checkpoint**: Foundation ready — all layers (domain, persistence, DTO, mapper, migration) support the unified Source model. User story implementation can now begin.

---

## Phase 3: User Story 1 — Deploy Knative with direct image reference (Priority: P1) 🎯 MVP

**Goal**: Enable operators to create and deploy MCP/Adapter/Interceptor services using a direct Docker image reference without an image definition.

**Independent Test**: Create an MCP deployment with `image_reference` source, deploy it, and verify the Knative service uses the provided image directly.

### Implementation for User Story 1

- [ ] T039 [US1] Add `validateSourceForDeploymentType(CreateDeployment)` method in `DeploymentService` to validate that Knative types accept only `InternalImageSource` or `ImageReferenceSource`; call on create and update in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentService.java`
- [ ] T040 [US1] Update `resolveImageDefinition(CreateDeployment)` in `DeploymentService` to extract `imageDefinitionId` from `InternalImageSource` instead of the removed base deployment fields in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentService.java`
- [ ] T041 [US1] Add `resolveImageName(Deployment)` method in `KnativeDeploymentManager` that pattern-matches on `Source`: `ImageReferenceSource` returns imageReference directly, `InternalImageSource` looks up image definition; update `prepareServiceSpec` to use it in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/KnativeDeploymentManager.java`

### Tests for User Story 1

- [ ] T042 [P] [US1] Update `KnativeDeploymentManagerTest` to test `resolveImageName` with both `ImageReferenceSource` and `InternalImageSource` in `src/test/java/com/epam/aidial/deployment/manager/service/deployment/KnativeDeploymentManagerTest.java`
- [ ] T043 [P] [US1] Update `DeploymentControllerTest` to test creating MCP/Adapter/Interceptor deployments with `image_reference` source and verify validation rejects incompatible sources in `src/test/java/com/epam/aidial/deployment/manager/web/controller/none/DeploymentControllerTest.java`
- [ ] T044 [P] [US1] Update `DeploymentFunctionalTest` to add end-to-end scenarios for creating and deploying with `image_reference` source in `src/test/java/com/epam/aidial/deployment/manager/functional/tests/DeploymentFunctionalTest.java`

**Checkpoint**: Operators can create and deploy Knative services using direct image references. Core new capability is functional.

---

## Phase 4: User Story 2 — Unified source CRUD for all deployment types (Priority: P1)

**Goal**: All deployment types (MCP, Adapter, Interceptor, Inference, NIM) use the unified source model for CRUD operations, returning typed source objects in API responses.

**Independent Test**: Create deployments of each type with their respective source types and verify CRUD operations return correct typed source objects with `$type` discriminator.

### Implementation for User Story 2

- [ ] T045 [US2] Ensure `validateSourceForDeploymentType` also validates Inference accepts only `HuggingFaceSource` and NIM accepts only `NgcRegistrySource` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentService.java`
- [ ] T046 [P] [US2] Update `InferenceDeploymentManager` to work with unified `Source` type instead of `InferenceDeploymentSource` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/InferenceDeploymentManager.java`
- [ ] T047 [P] [US2] Update `NimDeploymentManager` to work with unified `Source` type instead of `NimDeploymentSource` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManager.java`

### Tests for User Story 2

- [ ] T048 [P] [US2] Update `InferenceDeploymentManagerTest` to use `HuggingFaceSource` instead of `InferenceDeploymentHuggingFaceSource` in `src/test/java/com/epam/aidial/deployment/manager/service/deployment/InferenceDeploymentManagerTest.java`
- [ ] T049 [P] [US2] Update `NimDeploymentManagerTest` to use `NgcRegistrySource` instead of `NimDeploymentNgcRegistrySource` in `src/test/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManagerTest.java`
- [ ] T050 [P] [US2] Update `DeploymentRepositoryTest` to verify persistence of unified source on base entity in `src/test/java/com/epam/aidial/deployment/manager/dao/repository/DeploymentRepositoryTest.java`
- [ ] T051 [US2] Update `FullWorkflowFunctionalTest` to use unified source model in deployment creation flows in `src/test/java/com/epam/aidial/deployment/manager/functional/tests/FullWorkflowFunctionalTest.java`
- [ ] T052 [US2] Update `FullWorkflowWithMockedK8sClientFunctionalTest` to use unified source model in `src/test/java/com/epam/aidial/deployment/manager/functional/tests/FullWorkflowWithMockedK8sClientFunctionalTest.java`

**Checkpoint**: All deployment types use the unified source model. CRUD operations return consistent typed source objects.

---

## Phase 5: User Story 3 — Seamless data migration (Priority: P1)

**Goal**: Existing deployments are automatically migrated to the unified source format on upgrade with zero manual intervention.

**Independent Test**: Populate database with pre-migration data, run V1.50 migration, verify all deployments accessible with correct source JSON.

### Tests for User Story 3

- [ ] T053 [US3] Verify migration runs correctly by running `DeploymentFunctionalTest` suite against H2 (migration auto-applied on startup) and checking all pre-existing deployment fixtures load with correct source format in `src/test/java/com/epam/aidial/deployment/manager/functional/tests/DeploymentFunctionalTest.java`

**Checkpoint**: Migration verified — existing deployments survive upgrade intact.

---

## Phase 6: User Story 4 — Export/import with unified sources (Priority: P2)

**Goal**: Export/import preserves source information, with `imageDefinitionId` excluded from export and resolved on import.

**Independent Test**: Export deployments with various source types, import into clean environment, verify sources are correctly preserved and image definitions resolved.

### Implementation for User Story 4

- [ ] T054 [US4] Create `InternalImageSourceExportMixIn` to exclude `imageDefinitionId` from exported `InternalImageSource` in `src/main/java/com/epam/aidial/deployment/manager/configuration/export/InternalImageSourceExportMixIn.java`
- [ ] T055 [US4] Register `InternalImageSourceExportMixIn` in `JsonMapperConfiguration.getExportJsonMapper()` in `src/main/java/com/epam/aidial/deployment/manager/configuration/JsonMapperConfiguration.java`
- [ ] T056 [US4] Update `ConfigExporter` to handle unified source in export/import flow in `src/main/java/com/epam/aidial/deployment/manager/service/config/ConfigExporter.java`

### Tests for User Story 4

- [ ] T057 [P] [US4] Update `ConfigExportImportFunctionalTest` to verify export excludes `imageDefinitionId` from `internal_image` sources and import resolves it correctly in `src/test/java/com/epam/aidial/deployment/manager/functional/tests/ConfigExportImportFunctionalTest.java`
- [ ] T058 [P] [US4] Update `ConfigExporterTest` unit test to verify source handling in `src/test/java/com/epam/aidial/deployment/manager/service/config/ConfigExporterTest.java`
- [ ] T059 [P] [US4] Update expected export JSON fixtures to match new source format in `src/test/resources/config/expected_export_config_for_import.json`

**Checkpoint**: Export/import works correctly with unified source model.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Test fixture updates, MCP-related test updates, and final validation.

- [ ] T060 [P] Update MCP deployment test fixtures to use `source` instead of image definition fields in `src/test/resources/mcp/deployment/*.json`
- [ ] T061 [P] Update `McpServiceTest` to use unified source model in `src/test/java/com/epam/aidial/deployment/manager/service/McpServiceTest.java`
- [ ] T062 [P] Update `FunctionalTestHelper` utility to create deployments with unified source in `src/test/java/com/epam/aidial/deployment/manager/functional/utils/FunctionalTestHelper.java`
- [ ] T063 Run `./gradlew checkstyleMain checkstyleTest` to verify code style compliance
- [ ] T064 Run `./gradlew clean build` to verify full build passes with all tests

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 (Source interface must exist before modifying Deployment to use it)
- **User Stories (Phase 3–6)**: All depend on Phase 2 completion
  - US1 and US2 can proceed in parallel
  - US3 depends on Phase 2 migration tasks (T035–T038)
  - US4 can proceed independently after Phase 2
- **Polish (Phase 7)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Requires Phase 2. No dependencies on other stories.
- **User Story 2 (P1)**: Requires Phase 2. No dependencies on other stories.
- **User Story 3 (P1)**: Requires Phase 2 (migration tasks). No dependencies on other stories.
- **User Story 4 (P2)**: Requires Phase 2. No dependencies on other stories.

### Within Each User Story

- Implementation tasks before test tasks (tests verify implementation)
- Service-layer changes before test updates

### Parallel Opportunities

- Phase 1: T001–T005 can all run in parallel (independent new files)
- Phase 2: T013–T017 (persistence records), T022–T027 (DTO records), T036–T038 (vendor migrations) — all parallel within their groups
- Phase 3+: All user stories can be worked on in parallel after Phase 2 completes
- Test tasks within each story marked [P] can run in parallel

---

## Parallel Example: Phase 1 (Setup)

```text
# Launch all new Source interface files in parallel:
Task T001: Create Source.java sealed interface
Task T002: Create InternalImageSource.java record
Task T003: Create ImageReferenceSource.java record
Task T004: Rename to HuggingFaceSource.java
Task T005: Rename to NgcRegistrySource.java
```

## Parallel Example: User Story 1

```text
# After T039-T041 implementation, launch tests in parallel:
Task T042: KnativeDeploymentManagerTest updates
Task T043: DeploymentControllerTest updates
Task T044: DeploymentFunctionalTest updates
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (Source interface hierarchy)
2. Complete Phase 2: Foundational (all layer modifications + migration)
3. Complete Phase 3: User Story 1 (direct image reference for Knative)
4. **STOP and VALIDATE**: Test creating MCP deployment with `image_reference` source
5. Deploy/demo if ready

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready
2. Add User Story 1 → Test image_reference deployment → Deploy (MVP!)
3. Add User Story 2 → Test all deployment types with unified source → Deploy
4. Add User Story 3 → Verify migration on all DB vendors → Deploy
5. Add User Story 4 → Test export/import round-trip → Deploy
6. Phase 7 → Final polish and full test suite validation

### Single Developer Strategy

Since all user stories share the same foundational changes (Phase 2), the recommended execution order is:
1. Phase 1 → Phase 2 (largest effort — ~60% of work)
2. Phase 3 (US1) + Phase 4 (US2) together (tightly coupled service changes)
3. Phase 5 (US3) — migration verification
4. Phase 6 (US4) — export/import
5. Phase 7 — polish

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- The foundational phase (Phase 2) is the largest phase because the unified source model touches all layers simultaneously
