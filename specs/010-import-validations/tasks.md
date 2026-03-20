# Tasks: Import Validations

**Input**: Design documents from `/specs/010-import-validations/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: New model/exception classes used by multiple user stories

- [x] T001 [P] Create `ImportValidationError` record (entityType, entityIdentifier, fieldPath, message) in `src/main/java/com/epam/aidial/deployment/manager/model/config/ImportValidationError.java`
- [x] T002 [P] Create `ImportValidationException` extending `RuntimeException` with `List<ImportValidationError> errors` field in `src/main/java/com/epam/aidial/deployment/manager/exception/ImportValidationException.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Expose parsing, add sanitization method, and create reverse mappers needed by US4 and US1

**CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 Add `parseAndSanitizeExportConfig(MultipartFile)` public method in `src/main/java/com/epam/aidial/deployment/manager/service/config/ConfigTransferService.java` — calls existing `parseExportConfig()` then round-trips result through `exportJsonMapper` (`exportJsonMapper.readValue(exportJsonMapper.writeValueAsBytes(config), ExportConfig.class)`) to strip MixIn-excluded fields; returns sanitized `ExportConfig`
- [x] T004 Add `importConfig(ExportConfig, ConflictResolutionPolicy)` overload in `src/main/java/com/epam/aidial/deployment/manager/service/config/ConfigTransferService.java` — delegates to `configImporter.importConfig(config, resolutionPolicy)`; this allows the controller to pass a pre-parsed and validated config without injecting `ConfigImporter` directly
- [x] T004a Add `getImportConfigPreview(ExportConfig, ConflictResolutionPolicy)` overload in `src/main/java/com/epam/aidial/deployment/manager/service/config/ConfigTransferService.java` — delegates to `configImportPreviewer.previewImport(config, resolutionPolicy)`; this allows the controller to pass a pre-parsed and validated config without injecting `ConfigImportPreviewer` directly
- [x] T005 [P] Add reverse mapping `Deployment → CreateDeploymentRequestDto` (with subclass mappings for all 5 deployment types) in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/DeploymentDtoMapper.java` — use `@Mapping(target = "name", source = "id")`, add `@AfterMapping` methods for source field reverse mapping (domain Source → DTO DeploymentSourceRequestDto), and `listToString` for command/args
- [x] T006 [P] Add reverse mapping `ImageDefinition → ImageDefinitionRequestDto` (with subclass mappings for Mcp, Adapter, Interceptor) in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/ImageDefinitionDtoMapper.java` — ignore system-managed fields (id, createdAt, updatedAt, imageName, buildStatus, buildLogs, builtAt)

**Checkpoint**: Foundation ready — parse exposed, sanitizer available, reverse mappers generate correctly. Run `./gradlew checkstyleMain` to verify.

---

## Phase 3: User Story 4 - Maliciously injected system-managed fields are stripped (Priority: P1)

**Goal**: Strip MixIn-excluded fields from imported config before any further processing, preventing malicious injection of `status`, `author`, `buildStatus`, `url`, `serviceName`, `imageName`, `k8sSecretName`, etc.

**Independent Test**: Import a ZIP with injected system-managed fields and verify they are not persisted.

### Implementation for User Story 4

- [x] T007 [US4] Refactor `ConfigController.importConfig()` to call `configTransferService.parseAndSanitizeExportConfig(zipFile)` then `configTransferService.importConfig(config, resolutionPolicy)` in `src/main/java/com/epam/aidial/deployment/manager/web/controller/ConfigController.java` — replace the single `configTransferService.importConfig(zipFile, ...)` call with the two-step flow; all service calls remain routed through `ConfigTransferService` (no direct `ConfigImporter` injection)
- [x] T008 [US4] Remove `cloneImageDefinition()` method and `exportJsonMapper` field from `src/main/java/com/epam/aidial/deployment/manager/service/config/imports/ImageDefinitionImporter.java` — simplify `mergeForOverwrite()` to mutate `imported` directly (`imported.setAuthor(existing.getAuthor())`); update `importOne()` to pass `imported` directly to `createImageDefinition()` and `updateImageDefinition()` without cloning
- [x] T009 [US4] Refactor `ConfigController.getImportConfigPreview()` to call `configTransferService.parseAndSanitizeExportConfig(zipFile)` then `configTransferService.getImportConfigPreview(config, resolutionPolicy)` in `src/main/java/com/epam/aidial/deployment/manager/web/controller/ConfigController.java` — same pattern as T007; all service calls via `ConfigTransferService`

**Checkpoint**: US4 complete — import and preview both sanitize. Existing import functional tests must still pass (`./gradlew testFast`).

---

## Phase 4: User Story 1 + User Story 2 - Import validation with clear errors / Valid configs pass transparently (Priority: P1) MVP

**Goal**: Validate all imported entities using the same rules as the REST API. Reject imports with invalid data and report all violations. Valid configs must continue to import without errors.

**Independent Test**: Upload a ZIP with known-invalid entities → verify 400 with all violations listed. Re-import a previously valid export → verify success unchanged.

### Implementation for User Story 1 + 2

- [x] T010 [US1] Create `ImportConfigValidator` as `@Component` with `@LogExecution` in `src/main/java/com/epam/aidial/deployment/manager/web/validation/ImportConfigValidator.java` — inject `jakarta.validation.Validator`, `DeploymentDtoMapper`, `ImageDefinitionDtoMapper`, `DomainListValidator`; implement `validate(ExportConfig config)` that: (1) iterates all deployment maps, maps each `Deployment` → `CreateDeploymentRequestDto` via `DeploymentDtoMapper`, validates with `validator.validate()`, (2) iterates all image definition maps, maps each `ImageDefinition` → `ImageDefinitionRequestDto` via `ImageDefinitionDtoMapper`, validates with `validator.validate()`, (3) validates `config.getGlobalImageBuildDomainWhitelist()` using `DomainListValidator.isValid()` directly (mirrors `@ValidDomainList` on `GlobalDomainWhitelistController.updateDomainWhitelistForImageBuild()`), (4) collects all `ConstraintViolation` objects and domain whitelist errors into `List<ImportValidationError>` with entity type (from `ExportConfigComponentType`) and entity identifier, (5) throws `ImportValidationException` if list is non-empty; implement `collectErrors(ExportConfig config)` that returns `List<ImportValidationError>` without throwing (used by preview)
- [x] T011 [US1] Wire validation into `ConfigController.importConfig()` — call `importConfigValidator.validate(config)` after `parseAndSanitizeExportConfig()` and before `configTransferService.importConfig(config, ...)` in `src/main/java/com/epam/aidial/deployment/manager/web/controller/ConfigController.java`
- [x] T012 [US1] Add `@ExceptionHandler(ImportValidationException.class)` method in `src/main/java/com/epam/aidial/deployment/manager/web/handler/DefaultExceptionHandler.java` — return 400 BAD_REQUEST `ErrorView` with message formatted as `"Import validation failed:\n"` followed by one line per error: `"[{entityType} '{entityIdentifier}'] Field [{fieldPath}]: {message}"`
- [x] T013 [US1] Update `@Operation` and `@ApiResponse` annotations on the import endpoint in `src/main/java/com/epam/aidial/deployment/manager/web/controller/ConfigController.java` to document the 400 validation error response

**Checkpoint**: US1+US2 complete — invalid imports rejected with all violations; valid imports succeed unchanged. Run `./gradlew testFast`.

---

## Phase 5: User Story 3 - Import preview also validates entities (Priority: P2)

**Goal**: Surface validation errors in the preview response alongside conflict information so administrators can fix issues before attempting the actual import.

**Independent Test**: Preview a ZIP with invalid entities → verify the response includes `validationErrors` alongside the conflict preview.

### Implementation for User Story 3

- [x] T014 [P] [US3] Add `validationErrors` field (`List<ImportValidationError>`) to `src/main/java/com/epam/aidial/deployment/manager/model/config/ImportConfigPreview.java` with `@Builder.Default` initialized to empty list
- [x] T015 [P] [US3] Add `validationErrors` field (`List<ImportValidationError>`) to the preview response DTO `ImportConfigPreviewDto` and update `ImportConfigDtoMapper` to map the field — reuse `ImportValidationError` directly (it is a simple record with only primitive/String fields, no domain-layer dependencies, safe for serialization as-is; no separate DTO type needed)
- [x] T016 [US3] Wire validation into preview flow — in `ConfigController.getImportConfigPreview()`, after sanitize call `importConfigValidator.collectErrors(config)` and set the result on the preview object before returning, in `src/main/java/com/epam/aidial/deployment/manager/web/controller/ConfigController.java`
- [x] T017 [US3] Update `@Operation` and `@ApiResponse` annotations on the preview endpoint in `src/main/java/com/epam/aidial/deployment/manager/web/controller/ConfigController.java` to document the `validationErrors` field in the response

**Checkpoint**: US3 complete — preview shows validation errors alongside conflicts. Run `./gradlew testFast`.

---

## Phase 6: Tests

**Purpose**: Verify sanitization, validation, backward compatibility, and malicious field injection

- [x] T018 [P] Write unit tests for `ImportConfigValidator` in `src/test/java/com/epam/aidial/deployment/manager/web/validation/ImportConfigValidatorTest.java` — test cases: `shouldPassValidation_whenAllEntitiesValid()`, `shouldFailValidation_whenDeploymentNameInvalid()`, `shouldFailValidation_whenImageDefinitionVersionInvalid()`, `shouldFailValidation_whenNestedObjectInvalid()` (e.g., invalid resources), `shouldCollectAllViolations_whenMultipleEntitiesInvalid()`, `shouldFailValidation_whenGlobalDomainWhitelistInvalid()`
- [x] T019 [P] Write functional tests for import validation in `src/test/java/com/epam/aidial/deployment/manager/functional/h2/ConfigImportValidationFunctionalTest.java` — test cases: `shouldRejectImport_whenDeploymentHasInvalidName()`, `shouldRejectImport_whenMultipleEntitiesInvalid()` (verify all violations returned), `shouldImportSuccessfully_whenAllEntitiesValid()` (backward compat / US2), `shouldStripInjectedStatusField_whenMaliciousZipImported()` (US4), `shouldStripInjectedAuthorField_whenMaliciousZipImported()` (US4), `shouldIncludeValidationErrorsInPreview_whenEntitiesInvalid()` (US3)

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verification and cleanup

- [x] T020 Run `./gradlew checkstyleMain checkstyleTest` and fix any violations
- [x] T021 Run `./gradlew testFast` and verify all tests pass (including new tests from Phase 6)
- [x] T022 Run `./gradlew clean build` for full suite verification

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: T003/T004 depend on Phase 1 conceptually but different files so can start in parallel; T005/T006 are fully independent
- **US4 (Phase 3)**: Depends on T003, T004 (parse + sanitize methods must exist)
- **US1+US2 (Phase 4)**: Depends on T005, T006 (reverse mappers must exist) and Phase 3 (sanitization wired first)
- **US3 (Phase 5)**: Depends on T010 (ImportConfigValidator must exist)
- **Tests (Phase 6)**: Depends on all user stories being complete (tests exercise the full flow)
- **Polish (Phase 7)**: Depends on Phase 6 (tests must exist before final verification)

### User Story Dependencies

- **US4 (P1)**: Can start after Foundational — no dependencies on other stories
- **US1+US2 (P1)**: Depends on US4 (sanitization must be wired before validation)
- **US3 (P2)**: Depends on US1 (uses ImportConfigValidator.collectErrors())

### Parallel Opportunities

- T001 and T002 can run in parallel (different files)
- T005 and T006 can run in parallel (different mapper files)
- T014 and T015 can run in parallel (different model/DTO files)
- Phase 1 tasks and Phase 2 tasks can overlap (different files, no compile dependencies)

---

## Parallel Example: Phase 2

```bash
# These touch different files and can run simultaneously:
Task T005: "Add reverse Deployment mapping in DeploymentDtoMapper.java"
Task T006: "Add reverse ImageDefinition mapping in ImageDefinitionDtoMapper.java"
```

## Parallel Example: Phase 5

```bash
# These touch different files and can run simultaneously:
Task T014: "Add validationErrors to ImportConfigPreview.java"
Task T015: "Add validationErrors to ImportConfigPreviewDto"
```

---

## Implementation Strategy

### MVP First (US4 + US1 + US2)

1. Complete Phase 1: Setup (T001-T002)
2. Complete Phase 2: Foundational (T003-T006)
3. Complete Phase 3: US4 — Sanitization (T007-T009)
4. Complete Phase 4: US1+US2 — Validation (T010-T013)
5. Complete Phase 6: Tests (T018-T019)
6. **STOP and VALIDATE**: Run `./gradlew testFast` — all tests pass, import with invalid data is rejected
7. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → infrastructure ready
2. Add US4 → sanitization active → malicious fields stripped
3. Add US1+US2 → validation active → invalid imports rejected, valid imports unaffected (MVP!)
4. Add US3 → preview surfaces validation errors
5. Tests → unit + functional coverage
6. Polish → checkstyle, full build

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- US2 (valid imports still work) has no dedicated tasks — it is verified by running existing tests after US1 implementation
- US4 must be implemented before US1 because validation runs on sanitized data
- The `exportJsonMapper` dependency on `ImageDefinitionImporter` becomes removable after T008 since it was only used for cloning
