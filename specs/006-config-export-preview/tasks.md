# Tasks: Config Export Preview

**Input**: Design documents from `specs/006-config-export-preview/`
**Prerequisites**: plan.md ✓, spec.md ✓, data-model.md ✓, contracts/ ✓

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: No new project infrastructure is required — this feature adds files to an existing,
fully-configured project. Phase 1 is intentionally minimal.

- [x] T001 Confirm branch `006-config-export-preview` is checked out and the project builds with `./gradlew clean build -x test`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: New DTOs required by the mapper, service, and controller. Both can be created in parallel.

**⚠️ CRITICAL**: T002 and T003 must be complete before any Phase 3 work can begin.

- [x] T002 [P] Create `ExportComponentInfoDto` record in `src/main/java/com/epam/aidial/deployment/manager/web/dto/config/ExportComponentInfoDto.java` — fields: `String id`, `String displayName`, `String version`, `String description`, `ExportConfigComponentTypeDto type`
- [x] T003 [P] Create `ExportConfigPreviewDto` record in `src/main/java/com/epam/aidial/deployment/manager/web/dto/config/ExportConfigPreviewDto.java` — fields: `List<String> globalImageBuildDomainWhitelist`, `List<ExportComponentInfoDto> imageDefinitions`, `List<ExportComponentInfoDto> deployments`

**Checkpoint**: Both DTOs compile cleanly (`./gradlew checkstyleMain`) — Phase 3 can now begin.

---

## Phase 3: User Story 1 — Preview Export Contents (Priority: P1) 🎯 MVP

**Goal**: Expose `POST /api/v1/configs/export-preview` returning a structured `ExportConfigPreviewDto`
for the same selection criteria used by the existing export endpoint.

**Independent Test**: `POST /api/v1/configs/export-preview` with `{"$type":"custom","addGlobalImageBuildDomainWhitelist":true,"components":[{"name":"<existing-deployment-name>","type":"MCP_DEPLOYMENT"}]}` returns HTTP 200 with the deployment in `deployments` and its image definition (if image-based) in `imageDefinitions`.

### Implementation

- [x] T004 [US1] Convert `ExportConfigMapper` from `interface` to `abstract class` in `src/main/java/com/epam/aidial/deployment/manager/web/mapper/ExportConfigMapper.java`: make `toExportRequest` `public abstract`; add concrete `toExportConfigPreviewDto(ExportConfig config)` method that flattens all five deployment maps and all three image-definition maps into `List<ExportComponentInfoDto>` using private helper methods `toComponentInfoDto(ImageDefinition, ExportConfigComponentTypeDto)` (maps `id.toString()→id`, `name→displayName`, `version→version`, `description→description`) and `toComponentInfoDto(Deployment, ExportConfigComponentTypeDto)` (maps `id→id`, `displayName→displayName`, `null→version`, `description→description`); returns `new ExportConfigPreviewDto(config.getGlobalImageBuildDomainWhitelist(), imageDefinitions, deployments)` (depends on T002, T003)

- [x] T005 [US1] Add `@Transactional(readOnly = true) public ExportConfig getExportConfig(ExportRequest request)` to `src/main/java/com/epam/aidial/deployment/manager/service/config/ConfigTransferService.java`: cast to `SelectedItemsExportRequest`, delegate to `configExporter.getConfig(...)`, and return the result (same guard as the existing `exportConfig` method)

- [x] T006 [US1] Add `POST /export-preview` endpoint to `src/main/java/com/epam/aidial/deployment/manager/web/controller/ConfigController.java`: method `previewConfig(@Valid @RequestBody ExportRequestDto dto)` returns `ExportConfigPreviewDto`; maps dto via `exportConfigMapper.toExportRequest(dto)`, calls `configTransfer.getExportConfig(request)`, maps result via `exportConfigMapper.toExportConfigPreviewDto(config)` (depends on T004, T005)

**Checkpoint**: `./gradlew testFast` passes. `POST /api/v1/configs/export-preview` returns HTTP 200 with correct `ExportConfigPreviewDto` — User Story 1 is fully functional.

---

## Phase 4: User Story 2 — Preview Empty Selection (Priority: P2)

**Goal**: `POST /api/v1/configs/export-preview` with an empty or absent `components` array returns
HTTP 200 with `ExportConfigPreviewDto` containing empty lists for all fields.

**Independent Test**: `POST /api/v1/configs/export-preview` with `{"$type":"custom","components":[]}` returns HTTP 200 with `{"globalImageBuildDomainWhitelist":[],"imageDefinitions":[],"deployments":[]}`.

**No additional implementation required.** `ConfigExporter.getConfig()` already returns an empty
`ExportConfig` when `components` is empty or null — the Phase 3 implementation handles this case
automatically.

**Checkpoint**: Manually verify the empty-selection scenario against the running service, or confirm via `./gradlew testFast` — User Story 2 is covered by the Phase 3 implementation.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final verification and style compliance.

- [x] T007 Run `./gradlew checkstyleMain checkstyleTest` and fix any style violations in the new and modified files (`ExportComponentInfoDto.java`, `ExportConfigPreviewDto.java`, `ExportConfigMapper.java`, `ConfigTransferService.java`, `ConfigController.java`)
- [x] T008 Run `./gradlew testFast` to confirm the full fast test suite passes with no regressions
- [x] T009 Add `previewConfig_returnsCorrectComponentInfoDtos_whenValidSelectionProvided` and `previewConfig_returnsEmptyLists_whenEmptySelectionProvided` test methods to `src/test/java/com/epam/aidial/deployment/manager/functional/tests/ConfigExportImportFunctionalTest.java`; inject `ExportConfigMapper` via `@Autowired`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — **BLOCKS Phase 3**
- **Phase 3 (US1)**: Depends on Phase 2 (T002, T003 complete)
- **Phase 4 (US2)**: Covered by Phase 3 — no separate implementation
- **Phase 5 (Polish)**: Depends on Phase 3 completion

### Task Dependencies

```
T001
  └─► T002 [P] ─┐
  └─► T003 [P] ─┤
                └─► T004 ─► T006
                └─► T005 ─► T006
                              └─► T007
                              └─► T008
```

### Parallel Opportunities

- **T002 + T003**: Both DTOs can be created simultaneously (different files)
- **T004 + T005**: Mapper and service changes are in different files with no inter-dependency — can be developed in parallel once T002 and T003 are done

---

## Implementation Strategy

### MVP (User Story 1 only)

1. Complete Phase 1 (T001)
2. Complete Phase 2 in parallel (T002, T003)
3. Complete Phase 3 (T004, T005 in parallel → T006)
4. **STOP and VALIDATE**: `POST /api/v1/configs/export-preview` works end-to-end
5. Phase 4 is already covered; run Phase 5 checks

### Parallel Execution

```bash
# Phase 2 — run simultaneously:
Task T002: Create ExportComponentInfoDto
Task T003: Create ExportConfigPreviewDto

# Phase 3 — run simultaneously after T002 + T003:
Task T004: Update ExportConfigMapper
Task T005: Update ConfigTransferService

# Phase 3 — after T004 + T005:
Task T006: Add previewConfig endpoint to ConfigController
```

---

## Notes

- [P] tasks = different files, no inter-task dependency — safe to run in parallel
- US2 has no implementation tasks: the empty-selection case is already handled by existing `ConfigExporter.getConfig()` logic
- No DB migrations, no new configuration properties, no Kubernetes changes
- `addSecrets` flag: accepted in request for API consistency; has no observable effect on response since `ExportComponentInfoDto` carries no env-var fields
