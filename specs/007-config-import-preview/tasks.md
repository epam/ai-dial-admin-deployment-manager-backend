# Tasks: Config Import Preview

**Input**: Design documents from `specs/007-config-import-preview/`
**Prerequisites**: plan.md ✅, spec.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Functional tests are included per plan.md Step 11 (explicitly required by the plan). Unit test updates are included per plan.md Step 10.

**Organization**: Tasks grouped by dependency layer and user story. US1, US2, US3 are all P1 and served by the same implementation; US4 is P2 (error handling).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to
- Paths are relative to `src/main/java/com/epam/aidial/deployment/manager/`
- Test paths are relative to `src/test/java/com/epam/aidial/deployment/manager/`

---

## Phase 1: Setup

**Purpose**: No project initialization required — this feature adds to an existing Spring Boot application.

- [X] T001 Confirm branch is `007-config-import-preview` and all design docs (`plan.md`, `data-model.md`, `contracts/`) are present in `specs/007-config-import-preview/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: New model and DTO types used by every previewer, mapper, and service task. All six files are independent and can be created in parallel.

**⚠️ CRITICAL**: No user story implementation can begin until T002–T007 are complete.

- [X] T002 [P] Create `ImportAction` enum (`CREATE`, `UPDATE`, `SKIP`, `FAIL`) in `model/config/ImportAction.java`
- [X] T003 [P] Create `ImportComponent<T>` class (`@Data @AllArgsConstructor @NoArgsConstructor`, fields: `ImportAction action`, `T prev`, `T next`) in `model/config/ImportComponent.java`
- [X] T004 [P] Create `ImportConfigPreview` class (`@Data @Builder @AllArgsConstructor @NoArgsConstructor`) with nine `@Builder.Default` fields (three image-definition lists, five deployment lists, one nullable `ImportComponent<List<String>> globalImageBuildDomainWhitelist`) in `model/config/ImportConfigPreview.java`
- [X] T005 [P] Create `ImportActionDto` enum (`CREATE`, `UPDATE`, `SKIP`, `FAIL`) in `web/dto/config/ImportActionDto.java`
- [X] T006 [P] Create `ImportComponentDto<T>` Java record (`importAction`, `prev`, `next`) in `web/dto/config/ImportComponentDto.java`
- [X] T007 [P] Create `ImportConfigPreviewDto` Java record with nine fields using existing web-layer DTO types as type params (e.g., `List<ImportComponentDto<McpImageDefinitionDto>> mcpImageDefinitions`, `ImportComponentDto<List<String>> globalImageBuildDomainWhitelist` nullable) in `web/dto/config/ImportConfigPreviewDto.java`

**Checkpoint**: Foundation ready — all model and DTO types exist; user story implementation can now proceed.

---

## Phase 3: User Stories 1, 2, 3 — Preview Accuracy, Type Coverage, Read-Only (Priority: P1) 🎯 MVP

**Goal**: Deliver the core `POST /api/v1/configs/import-preview` endpoint that returns correct `ImportAction` values for all entity types without writing any data.

**Independent Test**:
```bash
# Start app, POST a valid export ZIP → verify 200 response with importAction fields
curl -X POST http://localhost:8080/api/v1/configs/import-preview \
  -F "file=@export.zip" -F "resolutionPolicy=OVERWRITE"
# → HTTP 200, all entities show importAction=CREATE on empty DB
```

### Implementation

- [X] T008 [P] [US1] Create `ImageDefinitionImportPreviewer` (`@Slf4j @Component @LogExecution @RequiredArgsConstructor`) in `service/config/previews/ImageDefinitionImportPreviewer.java`: inject `ImageDefinitionService`; implement `previewImageDefinitions(ExportConfig, ConflictResolutionPolicy, ImportConfigPreview)` iterating all three image-definition maps; use `imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(type, name, version)` for conflict detection (conflict key = `(ImageType, name, version)`); return `ImportComponent<T>` per entry following the action semantics table (CREATE / UPDATE / SKIP / FAIL)

- [X] T009 [P] [US1] Create `DeploymentImportPreviewer` (`@Slf4j @Component @LogExecution @RequiredArgsConstructor`) in `service/config/previews/DeploymentImportPreviewer.java`: inject `DeploymentService`; implement `previewDeployments(ExportConfig, ConflictResolutionPolicy, ImportConfigPreview)` iterating all five deployment maps; use `deploymentService.getDeployment(id, false)` for conflict detection — `false` means return `Optional.empty()` rather than throw when the deployment is absent, matching the `DeploymentImporter` precedent (conflict key = deployment `id`); return `ImportComponent<T>` per entry following the action semantics table

- [X] T010 [P] [US2] Create `GlobalDomainWhitelistImportPreviewer` (`@Slf4j @Component @LogExecution @RequiredArgsConstructor`) in `service/config/previews/GlobalDomainWhitelistImportPreviewer.java`: inject `GlobalDomainWhitelistService`; implement `previewGlobalDomainWhitelist(List<String> incoming, ConflictResolutionPolicy)`; return `null` when incoming list is empty/null; catch `GlobalDomainWhitelistNotFoundException` → return `ImportComponent(CREATE, null, incoming)`; otherwise apply policy switch → UPDATE / SKIP / FAIL; return a single `ImportComponent<List<String>>`

- [X] T011 [US1] Create `ConfigImportPreviewer` (`@Slf4j @Service @LogExecution @RequiredArgsConstructor`) in `service/config/previews/ConfigImportPreviewer.java`: inject all three previewers from T008–T010; implement `previewImport(ExportConfig config, ConflictResolutionPolicy policy)` that builds an `ImportConfigPreview` via `@Builder`, calls all three previewers, sets `globalImageBuildDomainWhitelist` from the whitelist previewer result, and returns the populated preview object (depends on T008, T009, T010)

- [X] T012 [P] [US1] Create `ImportConfigDtoMapper` abstract class (`@Mapper(componentModel = "spring")`) in `web/mapper/ImportConfigDtoMapper.java`: `@Autowired ImageDefinitionDtoMapper` and `DeploymentDtoMapper`; add `public abstract ImportActionDto toActionDto(ImportAction action)` (MapStruct auto-generates); implement manual `public ImportConfigPreviewDto toImportConfigPreviewDto(ImportConfigPreview preview)` using streams to map each `ImportComponent<T>` to `ImportComponentDto<DTO>` — call `imageDefinitionDtoMapper.toImageDefinitionDto()` for image defs, `deploymentDtoMapper.toDeploymentDto()` for deployments; handle `null` `prev`/`next` safely; add private `<T, D> ImportComponentDto<D> toComponentDto(ImportComponent<T> c, Function<T, D> fn)` helper (depends on T003, T005, T006, T007)

- [X] T013 [US3] Add `getImportConfigPreview()` to `ConfigTransferService` in `service/config/ConfigTransferService.java`: add `ConfigImportPreviewer configImportPreviewer` to constructor; extract shared ZIP-parsing logic from `importConfig()` into a private `parseExportConfig(MultipartFile)` helper that returns `ExportConfig` (same validation: file name lookup, duplicate detection, error handling) — **⚠️ regression risk**: the existing `validEntryCount == 0` check fires inside the `while` loop, causing an immediate throw on any non-config entry; preserve this exact behaviour when extracting the helper and run `ConfigTransferServiceTest` import tests before and after to confirm no regression; call helper from both `importConfig()` and the new `@Transactional(readOnly = true) public ImportConfigPreview getImportConfigPreview(MultipartFile zipFile, ConflictResolutionPolicy resolutionPolicy)` method which calls `configImportPreviewer.previewImport(config, resolutionPolicy)` (depends on T011)

- [X] T014 [US1] Add `previewImport` endpoint to `ConfigController` in `web/controller/ConfigController.java`: add `ImportConfigDtoMapper importConfigDtoMapper` to constructor; add `@PostMapping(path = "/import-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) public ImportConfigPreviewDto previewImport(@RequestPart("file") MultipartFile file, @RequestParam("resolutionPolicy") ConflictResolutionPolicy resolutionPolicy)`; delegate to `configTransfer.getImportConfigPreview()` then `importConfigDtoMapper.toImportConfigPreviewDto()` (depends on T012, T013)

### Tests

- [X] T015 [US3] Update `ConfigTransferServiceTest` in `service/config/ConfigTransferServiceTest.java`: add `@Mock ConfigImportPreviewer configImportPreviewer`; add it to `ConfigTransferService` constructor in `setUp()`; add `shouldReturnImportConfigPreview_whenValidZip()` — build ZIP, stub `configImportPreviewer.previewImport()` to return a known `ImportConfigPreview`, call `configTransferService.getImportConfigPreview()`, verify previewer was called with correct `ExportConfig` and `ConflictResolutionPolicy` args; add `shouldNotCallPreviewer_whenInvalidZipForPreview()` mirroring existing import error test (depends on T013)

- [X] T016 [US1] Add `importPreview_returnsCreate_whenEntitiesDoNotExist()` to `ConfigExportImportFunctionalTest` in `functional/tests/ConfigExportImportFunctionalTest.java`: build ZIP with all entity types + whitelist; call `configTransferService.getImportConfigPreview(zipFile, OVERWRITE)` on empty DB; assert every `ImportComponent` in the preview has `action == CREATE`, `prev == null`, `next != null`; assert `globalImageBuildDomainWhitelist` component has `action == CREATE` (depends on T014)

- [X] T017 [P] [US1] Add `importPreview_returnsUpdate_whenEntitiesExistAndPolicyIsOverwrite()` to `ConfigExportImportFunctionalTest`: seed all entity types + whitelist; call preview with `OVERWRITE`; assert every component has `action == UPDATE`, `prev != null`, `next != null`

- [X] T018 [P] [US1] Add `importPreview_returnsSkip_whenEntitiesExistAndPolicyIsSkip()` to `ConfigExportImportFunctionalTest`: seed entities; call preview with `SKIP_IF_EXISTS`; assert `action == SKIP`, `prev != null`, `next == null` for all components

- [X] T019 [P] [US1] Add `importPreview_returnsFail_whenEntitiesExistAndPolicyIsFail()` to `ConfigExportImportFunctionalTest`: seed entities; call preview with `FAIL_IF_EXISTS`; assert `action == FAIL`, `prev != null`, `next != null` for all components

- [X] T020 [US2] Add `importPreview_coversAllEightEntityTypesInSingleZip()` to `ConfigExportImportFunctionalTest`: build ZIP with at least one entity of each of the 8 types; call preview with `OVERWRITE`; assert that all 8 typed lists in the preview are non-empty and each `ImportComponent` is correct (depends on T016)

- [X] T021 [US3] Add `importPreview_doesNotMutateDatabase()` to `ConfigExportImportFunctionalTest`: snapshot entity counts before preview call; call preview with `OVERWRITE`; assert entity counts are identical after preview; then call real `importConfig()` and assert counts increase (proving preview left DB untouched)

- [X] T028 [P] [US1] Add `importPreview_returnsEmptyLists_whenZipHasNoEntities()` to `ConfigExportImportFunctionalTest`: build a valid ZIP whose `ExportConfig` has all entity maps empty and no whitelist; call `configTransferService.getImportConfigPreview(zipFile, OVERWRITE)`; assert HTTP 200 (no exception) and all nine fields in the result are empty lists / null (covers FR-010)

**Checkpoint**: US1, US2, US3 fully functional. `POST /api/v1/configs/import-preview` returns accurate per-entity action previews for all entity types with no side effects.

---

## Phase 4: User Story 4 — Invalid ZIP Rejection (Priority: P2)

**Goal**: Validate that malformed inputs are rejected with informative errors, matching `/import` behaviour.

**Independent Test**:
```bash
# Upload a plain text file → expect 400
curl -X POST http://localhost:8080/api/v1/configs/import-preview \
  -F "file=@not-a-zip.txt" -F "resolutionPolicy=OVERWRITE"
# → HTTP 400
```

- [X] T022 [US4] Add `importPreview_returns400_whenFileIsNotAZip()` to `ConfigExportImportFunctionalTest`: upload a plain-text multipart file; call `configTransferService.getImportConfigPreview()`; assert `IllegalArgumentException` is thrown (same as `importConfig` invalid-ZIP test pattern); also verify that omitting the `resolutionPolicy` request parameter returns a 400 via Spring binding validation (no custom code required — this is inherent `@RequestParam` behaviour)

- [X] T023 [P] [US4] Add `importPreview_returns400_whenZipHasNoConfigFile()` to `ConfigExportImportFunctionalTest`: build a ZIP containing an entry with an unrecognised file name; assert the same `"No valid export configuration file"` error message as the real importer

**Checkpoint**: All four user stories complete and independently testable.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T024 Run `./gradlew checkstyleMain checkstyleTest` and resolve any violations across all new files
- [X] T025 Run `./gradlew testFast` to confirm all H2 tests pass
- [X] T026 Run `./gradlew clean build` (full suite including Postgres + SQL Server testcontainers) to confirm end-to-end build is green
- [X] T027 Follow `specs/007-config-import-preview/quickstart.md` smoke-test scenarios to confirm the endpoint behaves as specified

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user story work; T002–T007 are all fully parallelisable
- **US1/US2/US3 (Phase 3)**: Depends on Phase 2 completion
- **US4 (Phase 4)**: Depends on Phase 3 completion (reuses the ZIP-parsing helper from T013)
- **Polish (Phase 5)**: Depends on all story phases

### Within Phase 3

```
T002–T007 complete (Phase 2)
      │
      ├─── T008 (ImageDefinitionImportPreviewer)  ─────┐
      ├─── T009 (DeploymentImportPreviewer)        ─────┼──► T011 (ConfigImportPreviewer)
      ├─── T010 (GlobalDomainWhitelistImportPreviewer) ─┘        │
      └─── T012 (ImportConfigDtoMapper)  ──────────────────────────────────┐
                                                            │               │
                                                            ▼               ▼
                                                     T013 (ConfigTransferService) ──► T014 (ConfigController)
                                                            │                               │
                                                            ▼                               ▼
                                                     T015 (unit test)              T016–T023, T028 (functional tests)
```

### User Story Dependencies

- **US1/US2/US3 (P1)**: Depend only on Phase 2 (foundational types) — T008–T012 can start as soon as T002–T007 are done
- **US4 (P2)**: Depends on T013 (ZIP parsing helper) being in place

### Parallel Opportunities

- **Phase 2**: T002, T003, T004, T005, T006, T007 — all in parallel (6 independent files)
- **Phase 3 previewers**: T008, T009, T010, T012 — all in parallel (different files, same phase-2 prereqs)
- **Phase 3 functional tests**: T017, T018, T019 — all in parallel (same file, different test methods added sequentially by convention; mark as [P] if split across contributors)
- **Phase 4**: T022, T023 — in parallel

---

## Parallel Execution Examples

### Fastest path to Phase 2 checkpoint (6-way parallel)

```
Task: T002 — model/config/ImportAction.java
Task: T003 — model/config/ImportComponent.java
Task: T004 — model/config/ImportConfigPreview.java
Task: T005 — web/dto/config/ImportActionDto.java
Task: T006 — web/dto/config/ImportComponentDto.java
Task: T007 — web/dto/config/ImportConfigPreviewDto.java
```

### Fastest path to MVP endpoint (after Phase 2 checkpoint)

```
Task: T008 — ImageDefinitionImportPreviewer
Task: T009 — DeploymentImportPreviewer
Task: T010 — GlobalDomainWhitelistImportPreviewer
Task: T012 — ImportConfigDtoMapper
→ (all four complete)
Task: T011 — ConfigImportPreviewer
→ T013 — ConfigTransferService.getImportConfigPreview()
→ T014 — ConfigController.previewImport()
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: All foundational types (T002–T007)
3. Implement previewers + mapper + service + controller (T008–T014)
4. **Validate**: smoke-test `POST /api/v1/configs/import-preview` against empty DB → all `CREATE`
5. Add unit test (T015)

### Incremental Delivery

1. Phase 2 → foundation ready
2. Phase 3 → full preview accuracy + all 8 entity types + read-only guarantee (MVP)
3. Phase 4 → error handling parity with `/import`
4. Phase 5 → polish, build gate

---

## Notes

- [P] = different files, no dependencies on incomplete tasks in the same phase
- Each user story is independently verifiable via the quickstart or functional tests
- The `parseExportConfig()` extraction in T013 is a refactor of existing code — run `importConfig()` tests after to confirm no regression
- `ImportConfigDtoMapper` is named with the `*DtoMapper` convention (constitution §Naming), not `ImportConfigMapper` as the spec draft said
- Total tasks: **28** (T001–T027, T028)
