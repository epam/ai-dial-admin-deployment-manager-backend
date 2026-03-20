# Tasks: Application Image Definition & Deployment Type

**Input**: Design documents from `/specs/011-application-type/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Database Migrations)

**Purpose**: Create the `application_image_definition` and `application_deployment` tables across all database vendors (V1.54)

- [X] T001 [P] Create H2 migration with `application_image_definition` (UUID PK, FK to `image_definition`) and `application_deployment` (VARCHAR(36) PK, FK to `deployment`) tables in `src/main/resources/db/migration/H2/V1.54__CreateApplicationTables.sql`
- [X] T002 [P] Create PostgreSQL migration with named constraints (`pk_application_*`, `fk_application_*`, ON DELETE RESTRICT) in `src/main/resources/db/migration/POSTGRES/V1.54__CreateApplicationTables.sql`
- [X] T003 [P] Create MS SQL Server migration with named constraints (`pk_application_*`, `fk_application_*_to_*`, ON DELETE NO ACTION, UNIQUEIDENTIFIER for UUID) in `src/main/resources/db/migration/MS_SQL_SERVER/V1.54__CreateApplicationTables.sql`

---

## Phase 2: Foundational (Model, Entity & Enum Layer)

**Purpose**: Create all new classes and register APPLICATION in enums and parent-class polymorphic annotations. These are blocking prerequisites for all user stories.

**All new classes are empty marker subclasses mirroring their Adapter counterparts.**

- [X] T004 [P] Create `ApplicationImageDefinition` model class (`@SuperBuilder`, `@AllArgsConstructor`, extends `ImageDefinition`) in `src/main/java/com/epam/aidial/deployment/manager/model/ApplicationImageDefinition.java`
- [X] T005 [P] Create `ApplicationDeployment` model class (`@SuperBuilder`, `@NoArgsConstructor`, extends `Deployment`) in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/ApplicationDeployment.java`
- [X] T006 [P] Create `CreateApplicationDeployment` model class (`@SuperBuilder`, `@NoArgsConstructor`, extends `CreateDeployment`) in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/CreateApplicationDeployment.java`
- [X] T007 [P] Add `@JsonSubTypes.Type(value = ApplicationImageDefinition.class, name = "application")` to `ImageDefinition.java` in `src/main/java/com/epam/aidial/deployment/manager/model/ImageDefinition.java`
- [X] T008 [P] Add `@JsonSubTypes.Type(value = ApplicationDeployment.class, name = "application")` to `Deployment.java` in `src/main/java/com/epam/aidial/deployment/manager/model/deployment/Deployment.java`
- [X] T009 [P] Add `APPLICATION` enum value to `ImageType` and add `case ApplicationImageDefinition ignored -> APPLICATION` to the `of()` switch in `src/main/java/com/epam/aidial/deployment/manager/model/ImageType.java`
- [X] T010 [P] Add `APPLICATION` enum value to `DeploymentTypeDto` in `src/main/java/com/epam/aidial/deployment/manager/web/dto/DeploymentTypeDto.java`
- [X] T011 [P] Create `ApplicationImageDefinitionEntity` JPA entity (`@Entity`, `@Table(name = "application_image_definition")`, `@EntityListeners(AuditingEntityListener.class)`, `@NoArgsConstructor`, extends `ImageDefinitionEntity`) in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/ApplicationImageDefinitionEntity.java`
- [X] T012 [P] Create `ApplicationDeploymentEntity` JPA entity (`@Entity`, `@Table(name = "application_deployment")`, `@EntityListeners(AuditingEntityListener.class)`, extends `DeploymentEntity`) in `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/ApplicationDeploymentEntity.java`

- [X] T012a Audit all `switch`/`instanceof` patterns on `AdapterDeployment`, `AdapterImageDefinition`, and `CreateAdapterDeployment` across the codebase (grep for these class names in `src/main/java/`). Verify every explicit case has an Application counterpart in the task list. Key files to check: `DeploymentService.isApplicableForRollingUpdate()`, `ImportConfigValidator`, `ExportConfigComponentType` (if enum exists in code). Add missing tasks if any gaps found.

**Checkpoint**: All new classes exist, enums updated, parent model classes register APPLICATION. All switch/instanceof patterns audited. Mapper and repository changes can now proceed.

---

## Phase 3: User Story 1 — Application Image Definition CRUD (Priority: P1) MVP

**Goal**: Admins can create, list, update, and delete Application image definitions via the existing `/api/v1/images/definitions` endpoints.

**Independent Test**: Create an Application image definition via `POST /api/v1/images/definitions` with `$type: "application"`, then list with `?type=APPLICATION` and verify only Application definitions are returned.

### Implementation for User Story 1

- [X] T013 [P] [US1] Create `ApplicationImageDefinitionDto` (extends `ImageDefinitionDto`) in `src/main/java/com/epam/aidial/deployment/manager/web/dto/ApplicationImageDefinitionDto.java`
- [X] T014 [P] [US1] Create `ApplicationImageDefinitionRequestDto` (extends `ImageDefinitionRequestDto`) in `src/main/java/com/epam/aidial/deployment/manager/web/dto/ApplicationImageDefinitionRequestDto.java`
- [X] T015 [P] [US1] Add `@JsonSubTypes.Type(value = ApplicationImageDefinitionDto.class, name = "application")` to `ImageDefinitionDto` in `src/main/java/com/epam/aidial/deployment/manager/web/dto/ImageDefinitionDto.java`
- [X] T016 [P] [US1] Add `@JsonSubTypes.Type(value = ApplicationImageDefinitionRequestDto.class, name = "application")` to `ImageDefinitionRequestDto` in `src/main/java/com/epam/aidial/deployment/manager/web/dto/ImageDefinitionRequestDto.java`
- [X] T017 [US1] Add `@SubclassMapping(source = ApplicationImageDefinitionRequestDto.class, target = ApplicationImageDefinition.class)` and `@SubclassMapping(source = ApplicationImageDefinition.class, target = ApplicationImageDefinitionDto.class)` to `ImageDefinitionDtoMapper` in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/ImageDefinitionDtoMapper.java`
- [X] T018 [US1] Add `@SubclassMapping` entries for `ApplicationImageDefinitionEntity ↔ ApplicationImageDefinition` (both directions) to `PersistenceImageDefinitionMapper` in `src/main/java/com/epam/aidial/deployment/manager/dao/mapper/PersistenceImageDefinitionMapper.java`
- [X] T019 [US1] Add `case APPLICATION -> ApplicationImageDefinitionEntity.class` to the `detectEntityClass` switch in `ImageDefinitionRepository` in `src/main/java/com/epam/aidial/deployment/manager/dao/repository/ImageDefinitionRepository.java`
- [X] T020 [US1] Add `createApplicationImageDefinition()` helper method (mirroring `createAdapterImageDefinition()`) in `src/test/java/com/epam/aidial/deployment/manager/functional/utils/FunctionalTestHelper.java`
- [X] T021 [US1] Add Application image definition functional test methods (`shouldSuccessfullyCreateApplicationImageDefinition`, `shouldSuccessfullyGetAllImageDefinitionsByType_whenTypeIsApplication`) in the appropriate functional test class under `src/test/java/com/epam/aidial/deployment/manager/functional/tests/`

**Checkpoint**: Application image definitions can be created, listed by type, updated, and deleted. Run `./gradlew testFast` to verify.

---

## Phase 4: User Story 2 — Application Deployment CRUD & Lifecycle (Priority: P1)

**Goal**: Admins can create Application deployments (with any valid source type), deploy/undeploy them via Knative, and manage the full lifecycle.

**Independent Test**: Create an Application deployment via `POST /api/v1/deployments` with `$type: "application"`, then list with `?type=APPLICATION` and verify only Application deployments are returned.

### Implementation for User Story 2

- [X] T022 [P] [US2] Create `ApplicationDeploymentDto` (extends `ImageBasedDeploymentDto`) in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/ApplicationDeploymentDto.java`
- [X] T023 [P] [US2] Create `CreateApplicationDeploymentRequestDto` (extends `CreateImageBasedDeploymentRequestDto`) in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/CreateApplicationDeploymentRequestDto.java`
- [X] T024 [P] [US2] Add `@JsonSubTypes.Type(value = ApplicationDeploymentDto.class, name = "application")` to `DeploymentDto` in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/DeploymentDto.java`
- [X] T025 [P] [US2] Add `@JsonSubTypes.Type(value = CreateApplicationDeploymentRequestDto.class, name = "application")` to `CreateDeploymentRequestDto` in `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/CreateDeploymentRequestDto.java`
- [X] T026 [US2] Add `@SubclassMapping` entries for `CreateApplicationDeploymentRequestDto ↔ CreateApplicationDeployment` and `ApplicationDeployment ↔ ApplicationDeploymentDto` to `DeploymentDtoMapper` in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/DeploymentDtoMapper.java`
- [X] T027 [US2] Add `@SubclassMapping` entries for `ApplicationDeploymentEntity ↔ ApplicationDeployment` (both directions) to `PersistenceDeploymentMapper` in `src/main/java/com/epam/aidial/deployment/manager/dao/mapper/PersistenceDeploymentMapper.java`
- [X] T028 [US2] Add `@SubclassMapping` entries for `CreateApplicationDeployment ↔ ApplicationDeployment` (both directions) to `DeploymentMapper` in `src/main/java/com/epam/aidial/deployment/manager/mapper/DeploymentMapper.java`
- [X] T029 [US2] Add `case APPLICATION -> ApplicationDeploymentEntity.class` to the `toEntityClass` switch in `DeploymentRepository` in `src/main/java/com/epam/aidial/deployment/manager/dao/repository/DeploymentRepository.java`
- [X] T030 [US2] Add `ApplicationDeployment.class` to `getSupportedDeploymentClasses()` list and add `instanceof ApplicationDeployment` check to `getDeploymentOptional()` in `KnativeDeploymentManager` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/KnativeDeploymentManager.java`
- [X] T031 [US2] Add `case CreateApplicationDeployment ignored -> ApplicationDeployment.class` to the `provide(CreateDeployment)` switch in `DeploymentManagerProvider` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/DeploymentManagerProvider.java`
- [X] T032 [US2] Add `createApplicationDeploymentRequest(UUID imageDefinitionId)` helper method (mirroring `createAdapterDeploymentRequest()`) in `src/test/java/com/epam/aidial/deployment/manager/functional/utils/FunctionalTestHelper.java`
- [X] T033 [US2] Add Application deployment functional test methods (`shouldSuccessfullyCreateApplicationDeployment`, `shouldSuccessfullyGetAllDeploymentsByType_whenTypeIsApplication`) in `src/test/java/com/epam/aidial/deployment/manager/functional/tests/DeploymentFunctionalTest.java`

**Checkpoint**: Application deployments can be created, listed by type, deployed/undeployed via Knative. Run `./gradlew testFast` to verify.

---

## Phase 5: User Story 3 — Config Import/Export (Priority: P2)

**Goal**: Application image definitions and deployments are included in configuration export and correctly restored on import.

**Independent Test**: Create Application entities, export config, verify the export JSON contains `applicationImageDefinitions` and `applicationDeployments` maps.

### Implementation for User Story 3

- [X] T034 [US3] Add `applicationImageDefinitions` (`Map<String, ApplicationImageDefinition>`) and `applicationDeployments` (`Map<String, ApplicationDeployment>`) fields with `@Builder.Default` `LinkedHashMap<>()` initialization to `ExportConfig` in `src/main/java/com/epam/aidial/deployment/manager/model/config/ExportConfig.java`
- [X] T035 [US3] Add `case ApplicationImageDefinition a -> config.getApplicationImageDefinitions().put(key, a)` and `case ApplicationDeployment ignored -> config.getApplicationDeployments().put(key, (ApplicationDeployment) sanitized)` switch cases to `ConfigExporter` in `src/main/java/com/epam/aidial/deployment/manager/service/config/ConfigExporter.java`
- [X] T036 [US3] Add `importMap(config.getApplicationImageDefinitions(), policy)` call to `ImageDefinitionImporter.importImageDefinitions()` in `src/main/java/com/epam/aidial/deployment/manager/service/config/imports/ImageDefinitionImporter.java`
- [X] T037 [US3] Add `importMap(config.getApplicationDeployments(), policy)` call to `DeploymentImporter.importDeployments()` in `src/main/java/com/epam/aidial/deployment/manager/service/config/imports/DeploymentImporter.java`
- [X] T037a [US3] Add `APPLICATION_IMAGE_DEFINITION` and `APPLICATION_DEPLOYMENT` enum values to `ExportConfigComponentType` in `src/main/java/com/epam/aidial/deployment/manager/model/config/ExportConfigComponentType.java` and `ExportConfigComponentTypeDto` in `src/main/java/com/epam/aidial/deployment/manager/web/dto/config/ExportConfigComponentTypeDto.java`
- [X] T037b [US3] Add `APPLICATION_IMAGE_DEFINITION` and `APPLICATION_DEPLOYMENT` to the selective export switch in `ConfigExporter.getConfig()` in `src/main/java/com/epam/aidial/deployment/manager/service/config/ConfigExporter.java`
- [X] T037c [US3] Add Application streams to `ExportConfigMapper.toExportConfigPreviewDto()` for both image definitions and deployments in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/ExportConfigMapper.java`
- [X] T037d [US3] Add `applicationImageDefinitions` and `applicationDeployments` fields to `ImportConfigPreview` in `src/main/java/com/epam/aidial/deployment/manager/model/config/ImportConfigPreview.java` and `ImportConfigPreviewDto` in `src/main/java/com/epam/aidial/deployment/manager/web/dto/config/ImportConfigPreviewDto.java`
- [X] T037e [US3] Add Application preview calls in `ImageDefinitionImportPreviewer` and `DeploymentImportPreviewer` in `src/main/java/com/epam/aidial/deployment/manager/service/config/previews/`
- [X] T037f [US3] Add Application mapping entries in `ImportConfigDtoMapper.toImportConfigPreviewDto()` in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/ImportConfigDtoMapper.java`
- [X] T037g [US3] Add `validateImageDefinitions(config.getApplicationImageDefinitions(), ...)` and `validateDeployments(config.getApplicationDeployments(), ...)` calls to `ImportConfigValidator.collectErrors()` in `src/main/java/com/epam/aidial/deployment/manager/web/validation/ImportConfigValidator.java`
- [X] T037h [US3] Update `ImportConfigPreviewDto` constructor calls in `ConfigControllerTest` test data in `src/test/java/com/epam/aidial/deployment/manager/web/controller/none/ConfigControllerTest.java`

**Checkpoint**: Config export includes Application entities, import previews show them, import validation covers them, selective export handles them. Run `./gradlew testFast` to verify.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Update generic specs documentation, regenerate schema docs, and run full verification.

- [X] T038 [P] Create `specs/application-image-definitions/spec.md` mirroring `specs/adapter-image-definitions/spec.md` — document Application image definition contract, no additional fields beyond base, implementation notes with DTO class names
- [X] T039 [P] Create `specs/application-deployments/spec.md` mirroring `specs/adapter-deployments/spec.md` — document Application deployment contract, source types (internal_image, image_reference), Knative requirement, implementation notes with DTO class names
- [X] T040 [P] Update `specs/deployments/spec.md` — add APPLICATION to image-based deployment type lists and key terms (currently lists MCP, Interceptor, Adapter)
- [X] T041 [P] Update `specs/image-definitions/spec.md` — add APPLICATION to subtype list, `$type` discriminator values, and `ImageType` enum references (currently lists MCP, Interceptor, Adapter)
- [X] T042 [P] Update `specs/export-import/spec.md` — add `APPLICATION_IMAGE_DEFINITION` and `APPLICATION_DEPLOYMENT` to `ExportConfigComponentType` enum and image-based deployment lists
- [X] T043 [P] Update `specs/kubernetes-manifests/spec.md` — add Application to KNative-managed deployment type lists and related-specs links
- [X] T044 [P] Update `specs/image-builds/spec.md` — add Application alongside Adapter/Interceptor in pipeline selection and build scenarios
- [X] T045 [P] Update `specs/buildkit/spec.md` — add Application alongside Adapter/Interceptor in `ImageWrapperBuildPipeline` references and Docker-source copy scenarios
- [X] T046 [P] Update `specs/README.md` — add entries for `application-image-definitions` and `application-deployments` to the spec index table
- [X] T047 Regenerate database schema docs by running `./gradlew generateDbSchema` and verify `docs/db-schema.md` includes the new tables
- [X] T048 Run `./gradlew checkstyleMain checkstyleTest` to verify code style compliance
- [X] T049 Run `./gradlew testFast` to verify all H2 tests pass (fast feedback)
- [X] T050 Run `./gradlew clean build` for full verification (includes Postgres/SQL Server testcontainers tests)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 (migrations must exist for JPA entity validation)
- **US1 (Phase 3)**: Depends on Phase 2 (model classes + entities must exist before DTO/mapper/repository work)
- **US2 (Phase 4)**: Depends on Phase 2 (model classes + entities must exist). Independent of US1.
- **US3 (Phase 5)**: Depends on Phase 2 (model classes must exist for ExportConfig maps). Independent of US1/US2.
- **Polish (Phase 6)**: Depends on Phases 3–5 for full verification; spec updates (T038–T046) can start after Phase 2

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Phase 2 — no dependencies on other stories
- **User Story 2 (P1)**: Can start after Phase 2 — no dependencies on other stories. Can run in parallel with US1.
- **User Story 3 (P2)**: Can start after Phase 2 — independent of US1/US2

### Within Each User Story

- DTOs before mappers (mappers reference DTO classes)
- Mappers and repository before tests
- Parent @JsonSubTypes updates alongside DTO creation (different files)

### Parallel Opportunities

- All Phase 1 migrations (T001–T003) can run in parallel
- All Phase 2 tasks (T004–T012) can run in parallel — each is a different file
- Within US1: T013–T016 (DTOs + parent updates) can run in parallel, then T017–T019 (mappers + repo)
- Within US2: T022–T025 (DTOs + parent updates) can run in parallel, then T026–T031 (mappers + repo + services)
- US1, US2, and US3 can all run in parallel after Phase 2 completes
- All spec updates (T038–T046) can run in parallel

---

## Parallel Example: After Phase 2

```text
# All three user stories can start simultaneously:

# Stream 1 — US1 (Image Definitions):
T013, T014, T015, T016 (parallel DTOs) → T017, T018, T019 (mappers + repo) → T020, T021 (tests)

# Stream 2 — US2 (Deployments):
T022, T023, T024, T025 (parallel DTOs) → T026–T031 (mappers + repo + services) → T032, T033 (tests)

# Stream 3 — US3 (Config Import/Export):
T034 → T035, T036, T037 (parallel — different files)
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Complete Phase 1: Setup (3 migrations)
2. Complete Phase 2: Foundational (9 new classes + enum/parent updates)
3. Complete Phase 3: User Story 1 (image definition CRUD)
4. Complete Phase 4: User Story 2 (deployment CRUD + lifecycle)
5. **STOP and VALIDATE**: Run `./gradlew testFast` — Application type should be fully functional
6. Proceed to Phase 5 (config import/export) and Phase 6 (polish)

### Incremental Delivery

1. Phase 1 + 2 → Foundation ready
2. Add US1 → Application image definitions work → Validate
3. Add US2 → Application deployments work → Validate
4. Add US3 → Config import/export includes Application → Validate
5. Phase 6 → Specs updated, schema docs regenerated, full build passes

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- All new classes are empty marker subclasses — mirror Adapter counterparts exactly
- JSON discriminator: `"application"` (lowercase)
- Table names: `application_image_definition`, `application_deployment`
- No new API endpoints — existing polymorphic endpoints handle the new type automatically
- Source validation: `CreateApplicationDeployment` falls through to the `default` case in `validateSourceForDeploymentType()`, allowing `InternalImageSource` and `ImageReferenceSource`
