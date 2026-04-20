# Tasks: NIM KServe Migration & Configurable Storage Size

**Input**: Design documents from `specs/015-nim-kserve-migration/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md
**Status**: All tasks complete (post-factum documentation)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US5, US7)

## Phase 1: Setup (Database Migration)

**Purpose**: Database schema changes required before any storageSize work

- [x] T001 [P] Create H2 migration in src/main/resources/db/migration/H2/V1.57__AddStorageSizeToNimDeployment.sql
- [x] T002 [P] Create PostgreSQL migration in src/main/resources/db/migration/POSTGRES/V1.57__AddStorageSizeToNimDeployment.sql
- [x] T003 [P] Create SQL Server migration in src/main/resources/db/migration/MS_SQL_SERVER/V1.57__AddStorageSizeToNimDeployment.sql

---

## Phase 2: Foundational (Entity & Domain Layer)

**Purpose**: Add `storageSize` field across entity, domain model, and DTO layers — prerequisite for all storage-related user stories

- [x] T004 [P] Add `storageSize` field to NimDeploymentEntity in src/main/java/.../dao/entity/deployment/NimDeploymentEntity.java
- [x] T005 [P] Add `storageSize` field to NimDeployment domain model in src/main/java/.../model/deployment/NimDeployment.java
- [x] T006 [P] Add `storageSize` field to CreateNimDeployment in src/main/java/.../model/deployment/CreateNimDeployment.java
- [x] T007 [P] Add `storageSize` field to NimDeploymentDto response DTO in src/main/java/.../web/dto/deployment/NimDeploymentDto.java
- [x] T008 Add `storageSize` to NIM update block in PersistenceDeploymentMapper in src/main/java/.../dao/mapper/PersistenceDeploymentMapper.java

**Checkpoint**: storageSize field flows through all layers; MapStruct maps it automatically by name convention

---

## Phase 3: User Story 7 - Storage Size Validation (Priority: P2)

**Goal**: Validate storageSize using Fabric8 Quantity parser with configurable upper bound

**Independent Test**: Send invalid storageSize values and verify 400 responses; send values exceeding max and verify rejection

### Implementation for User Story 7

- [x] T009 [P] [US7] Create KubernetesQuantityParser utility in src/main/java/.../utils/KubernetesQuantityParser.java
- [x] T010 [P] [US7] Create ValidStorageSize constraint annotation in src/main/java/.../web/validation/ValidStorageSize.java
- [x] T011 [US7] Create StorageSizeValidator using KubernetesQuantityParser in src/main/java/.../web/validation/StorageSizeValidator.java
- [x] T012 [US7] Add @ValidStorageSize to CreateNimDeploymentRequestDto in src/main/java/.../web/dto/deployment/CreateNimDeploymentRequestDto.java
- [x] T013 [US7] Add max-storage-size config property in src/main/resources/application.yml

### Tests for User Story 7

- [x] T014 [P] [US7] Create KubernetesQuantityParserTest in src/test/java/.../utils/KubernetesQuantityParserTest.java
- [x] T015 [P] [US7] Create StorageSizeValidatorTest in src/test/java/.../web/validation/StorageSizeValidatorTest.java

**Checkpoint**: Invalid storageSize values rejected with 400; valid Kubernetes quantities accepted; max enforced

---

## Phase 4: User Story 5 & 6 - Storage Size in Manifests (Priority: P1)

**Goal**: Apply storageSize override to NIMService manifest PVC size during deploy

**Independent Test**: Deploy NIM with storageSize="50Gi" and verify manifest has correct PVC size; deploy without storageSize and verify 20Gi default

### Implementation for User Stories 5 & 6

- [x] T016 [P] [US5] Add Storage and Pvc field mappers to NimMappers in src/main/java/.../utils/mapping/NimMappers.java
- [x] T017 [US6] Add storageSize parameter and applyStorageSize method to NimManifestGenerator in src/main/java/.../service/manifest/NimManifestGenerator.java
- [x] T018 [US6] Pass storageSize from deployment to manifest generator in NimDeploymentManager in src/main/java/.../service/deployment/NimDeploymentManager.java

### Tests for User Stories 5 & 6

- [x] T019 [P] [US5] Add testServiceConfig_withNullStorageSize_preservesTemplateDefault test in src/test/java/.../service/manifest/NimManifestGeneratorTest.java
- [x] T020 [P] [US6] Add testServiceConfig_withStorageSize_overridesTemplateDefault test in src/test/java/.../service/manifest/NimManifestGeneratorTest.java
- [x] T021 [US6] Update all NimManifestGeneratorTest serviceConfig calls with new storageSize parameter in src/test/java/.../service/manifest/NimManifestGeneratorTest.java
- [x] T022 [US6] Update all NimDeploymentManagerTest mock setups with extra any() matcher in src/test/java/.../service/deployment/NimDeploymentManagerTest.java

**Checkpoint**: storageSize overrides PVC size when set; template default preserved when null

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, specs, and cleanup

- [x] T023 [P] Update nim-deployments base spec with storageSize requirement in specs/nim-deployments/spec.md
- [x] T024 [P] Create merged feature spec 015-nim-kserve-migration in specs/015-nim-kserve-migration/spec.md
- [x] T025 [P] Update configuration docs with max-storage-size property in docs/configuration.md
- [x] T026 Run checkstyleMain checkstyleTest — verified passing
- [x] T027 Run testFast — verified all tests pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — migration files are independent
- **Foundational (Phase 2)**: Depends on Phase 1 (schema must exist for entity)
- **US7 Validation (Phase 3)**: Depends on Phase 2 (DTO must have field for annotation)
- **US5/US6 Manifests (Phase 4)**: Depends on Phase 2 (domain model must have field)
- **Polish (Phase 5)**: Depends on all implementation phases

### User Story Dependencies

- **US5 (Default storage)**: Depends on Foundational — no other story dependencies
- **US6 (Custom storage)**: Depends on Foundational — no other story dependencies
- **US7 (Validation)**: Depends on Foundational — independent of US5/US6

### Parallel Opportunities

- T001, T002, T003: All migrations in parallel (different vendor dirs)
- T004, T005, T006, T007: All field additions in parallel (different files)
- T009, T010: Utility and annotation in parallel (different files)
- T014, T015: Both test classes in parallel
- T016: NimMappers independent of validator work
- T019, T020: Both manifest tests in parallel
- T023, T024, T025: All documentation in parallel

---

## Implementation Strategy

This feature was implemented as a single increment (all stories together) since the KServe migration was already in progress on the branch. The storageSize feature was added on top, following the existing `containerGrpcPort` pattern at every layer.

**Execution order used**: Migrations → Entity/Domain/DTO → Mappers → Validator → Manifest generator → Tests → Specs → Docs

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 27 |
| Phase 1 (Setup) | 3 |
| Phase 2 (Foundational) | 5 |
| Phase 3 (US7 Validation) | 7 |
| Phase 4 (US5/US6 Manifests) | 7 |
| Phase 5 (Polish) | 5 |
| Parallelizable tasks | 16 |
| All tasks complete | Yes |
