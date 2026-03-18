# Implementation Plan: Config Import Preview

**Branch**: `007-config-import-preview` | **Date**: 2026-03-16 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/007-config-import-preview/spec.md`

## Summary

Add a read-only `POST /api/v1/configs/import/preview` endpoint that accepts the same multipart inputs as `/import` (ZIP file + `resolutionPolicy`) and returns `ImportConfigPreviewDto` — a per-entity-type breakdown of what action (`CREATE`, `UPDATE`, `SKIP`, `FAIL`) the real import would take for each entity, together with the before (`prev`) and after (`next`) DTO representations. No data is written; the feature is purely read-only.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10
**Primary Dependencies**: Lombok, MapStruct 1.6.0, SpringDoc OpenAPI 2.8.5, Jackson 2.21.1
**Storage**: H2 (dev/test) / PostgreSQL / SQL Server — read-only queries only; no migrations
**Testing**: JUnit 5, Mockito, AssertJ, Testcontainers; `./gradlew testFast` for H2 fast path
**Target Platform**: Linux backend service (Spring Boot REST API)
**Project Type**: Web service
**Performance Goals**: Preview in under 3 seconds for ZIPs up to 200 entities (SC-001)
**Constraints**: Strictly read-only (`@Transactional(readOnly = true)`); no modifying K8s API calls; no DB writes
**Scale/Scope**: Same as existing `/import`; single-response (no pagination)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Rule | Status | Notes |
|------|--------|-------|
| Strict layered architecture (web → service → dao) | ✅ Pass | No DAO or K8s calls from web layer; previewers call service layer only |
| `@Transactional` only on service/dao layer | ✅ Pass | `@Transactional(readOnly = true)` on `ConfigTransferService.getImportConfigPreview()` only |
| No `@Transactional` on controllers | ✅ Pass | `ConfigController` carries no transaction annotations |
| K8s API calls only in `kubernetes/` package | ✅ Pass | Feature makes no K8s calls |
| `@LogExecution` on all Spring components | ✅ Pass | Must be added to all 4 new `@Component` / `@Service` classes |
| MapStruct `componentModel = "spring"` | ✅ Pass | `ImportConfigDtoMapper` will follow this rule |
| `*DtoMapper` naming for web-layer mappers | ✅ Pass | Mapper named `ImportConfigDtoMapper` (spec said `ImportConfigMapper` — corrected) |
| No wildcard imports | ✅ Pass | Enforced by Checkstyle |
| No DB migrations | ✅ Pass | Feature is read-only; no schema changes |
| `docs/configuration.md` update | ✅ N/A | No new config properties or env vars introduced |

## Project Structure

### Documentation (this feature)

```text
specs/007-config-import-preview/
├── plan.md              # This file
├── research.md          # Phase 0 — patterns, decisions, conflict key analysis
├── data-model.md        # Phase 1 — new types and conflict semantics
├── quickstart.md        # Phase 1 — how to smoke-test the endpoint locally
├── contracts/
│   └── import-preview-endpoint.md  # HTTP contract
└── tasks.md             # Phase 2 (/speckit.tasks — NOT created here)
```

### Source Code Layout

```text
src/main/java/com/epam/aidial/deployment/manager/

# ── Model layer (new) ─────────────────────────────────────────────────
model/config/
├── ImportAction.java                       # enum: CREATE, UPDATE, SKIP, FAIL
├── ImportComponent.java                    # generic @Data wrapper: action, prev, next
└── ImportConfigPreview.java                # @Data @Builder aggregate with per-type lists

# ── Web DTOs (new) ────────────────────────────────────────────────────
web/dto/config/
├── ImportActionDto.java                    # enum DTO: CREATE, UPDATE, SKIP, FAIL
├── ImportComponentDto.java                 # generic record: importAction, prev, next
└── ImportConfigPreviewDto.java             # response record with typed collections

# ── Web mapper (new) ──────────────────────────────────────────────────
web/mapper/
└── ImportConfigDtoMapper.java              # abstract @Mapper: ImportConfigPreview → ImportConfigPreviewDto

# ── Service layer — previewers (new) ──────────────────────────────────
service/config/previews/
├── ImageDefinitionImportPreviewer.java     # reads image defs, determines action per entry
├── DeploymentImportPreviewer.java          # reads deployments, determines action per entry
├── GlobalDomainWhitelistImportPreviewer.java  # reads whitelist, determines single action
└── ConfigImportPreviewer.java              # orchestrates all three previewers

# ── Modified files ────────────────────────────────────────────────────
service/config/
└── ConfigTransferService.java              # +getImportConfigPreview()

web/controller/
└── ConfigController.java                   # +previewImport() endpoint

src/test/java/com/epam/aidial/deployment/manager/

service/config/
└── ConfigTransferServiceTest.java          # +mock ConfigImportPreviewer, +test getImportConfigPreview()

functional/tests/
└── ConfigExportImportFunctionalTest.java   # +import-preview tests for all action types
```

**Structure Decision**: Single-project, extending existing package hierarchy. Previewers go in `service/config/previews/` (new sub-package mirroring `service/config/imports/`). No new top-level packages.

---

## Implementation Steps

### Step 1 — Model layer: `ImportAction`, `ImportComponent<T>`, `ImportConfigPreview`

**File**: `model/config/ImportAction.java`
- Enum with values: `CREATE`, `UPDATE`, `SKIP`, `FAIL`

**File**: `model/config/ImportComponent.java`
- Generic class: `@Data @AllArgsConstructor @NoArgsConstructor`
- Fields: `ImportAction action`, `T prev`, `T next`

**File**: `model/config/ImportConfigPreview.java`
- `@Data @Builder @AllArgsConstructor @NoArgsConstructor`
- Nine fields with `@Builder.Default`:
  - `List<ImportComponent<McpImageDefinition>> mcpImageDefinitions`
  - `List<ImportComponent<AdapterImageDefinition>> adapterImageDefinitions`
  - `List<ImportComponent<InterceptorImageDefinition>> interceptorImageDefinitions`
  - `ImportComponent<List<String>> globalImageBuildDomainWhitelist` (nullable, no default)
  - `List<ImportComponent<McpDeployment>> mcpDeployments`
  - `List<ImportComponent<AdapterDeployment>> adapterDeployments`
  - `List<ImportComponent<InterceptorDeployment>> interceptorDeployments`
  - `List<ImportComponent<NimDeployment>> nimDeployments`
  - `List<ImportComponent<InferenceDeployment>> inferenceDeployments`

---

### Step 2 — Web DTOs: `ImportActionDto`, `ImportComponentDto<T>`, `ImportConfigPreviewDto`

**File**: `web/dto/config/ImportActionDto.java`
- Enum: `CREATE`, `UPDATE`, `SKIP`, `FAIL`

**File**: `web/dto/config/ImportComponentDto.java`
- Java record: `public record ImportComponentDto<T>(ImportActionDto importAction, T prev, T next) {}`

**File**: `web/dto/config/ImportConfigPreviewDto.java`
- Java record with nine fields mirroring `ImportConfigPreview`, using DTO types:
  - `List<ImportComponentDto<McpImageDefinitionDto>> mcpImageDefinitions`
  - `List<ImportComponentDto<AdapterImageDefinitionDto>> adapterImageDefinitions`
  - `List<ImportComponentDto<InterceptorImageDefinitionDto>> interceptorImageDefinitions`
  - `ImportComponentDto<List<String>> globalImageBuildDomainWhitelist` (nullable)
  - `List<ImportComponentDto<McpDeploymentDto>> mcpDeployments`
  - `List<ImportComponentDto<AdapterDeploymentDto>> adapterDeployments`
  - `List<ImportComponentDto<InterceptorDeploymentDto>> interceptorDeployments`
  - `List<ImportComponentDto<NimDeploymentDto>> nimDeployments`
  - `List<ImportComponentDto<InferenceDeploymentDto>> inferenceDeployments`

---

### Step 3 — Service layer: `ImageDefinitionImportPreviewer`

**File**: `service/config/previews/ImageDefinitionImportPreviewer.java`
- `@Slf4j @Component @LogExecution @RequiredArgsConstructor`
- Dependencies: `ImageDefinitionService`
- `public void previewImageDefinitions(ExportConfig config, ConflictResolutionPolicy policy, ImportConfigPreview preview)`
  - Calls `previewMap(config.getMcpImageDefinitions(), policy, preview.getMcpImageDefinitions())`
  - Calls `previewMap(config.getAdapterImageDefinitions(), policy, preview.getAdapterImageDefinitions())`
  - Calls `previewMap(config.getInterceptorImageDefinitions(), policy, preview.getInterceptorImageDefinitions())`
- `private <T extends ImageDefinition> void previewMap(Map<String, T> map, policy, List<ImportComponent<T>> out)`
  - Iterates map values; calls `previewOne()` for each; adds result to `out`
- `private <T extends ImageDefinition> ImportComponent<T> previewOne(T incoming, policy)`
  - Derives `ImageType` via pattern switch (same logic as `ImageDefinitionImporter.imageTypeOf()`)
  - Calls `imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(type, name, version)`
  - If absent: returns `new ImportComponent<>(CREATE, null, incoming)`
  - If present: returns based on policy:
    - `FAIL_IF_EXISTS` → `new ImportComponent<>(FAIL, existing, incoming)`
    - `SKIP_IF_EXISTS` → `new ImportComponent<>(SKIP, existing, null)`
    - `OVERWRITE` → `new ImportComponent<>(UPDATE, existing, incoming)`

---

### Step 4 — Service layer: `DeploymentImportPreviewer`

**File**: `service/config/previews/DeploymentImportPreviewer.java`
- `@Slf4j @Component @LogExecution @RequiredArgsConstructor`
- Dependencies: `DeploymentService`
- `public void previewDeployments(ExportConfig config, ConflictResolutionPolicy policy, ImportConfigPreview preview)`
  - Calls `previewMap(config.getMcpDeployments(), policy, preview.getMcpDeployments())`
  - ... (all 5 deployment type maps)
- `private <T extends Deployment> void previewMap(Map<String, T> map, policy, List<ImportComponent<T>> out)`
  - Iterates map values; calls `previewOne()` for each
- `private <T extends Deployment> ImportComponent<T> previewOne(T incoming, policy)`
  - `String id = incoming.getId()`
  - `deploymentService.getDeployment(id, false)` → `Optional<Deployment>`
  - If absent: `new ImportComponent<>(CREATE, null, incoming)`
  - If present (cast to T): same policy switch as image defs

---

### Step 5 — Service layer: `GlobalDomainWhitelistImportPreviewer`

**File**: `service/config/previews/GlobalDomainWhitelistImportPreviewer.java`
- `@Slf4j @Component @LogExecution @RequiredArgsConstructor`
- Dependencies: `GlobalDomainWhitelistService`
- `public ImportComponent<List<String>> previewGlobalDomainWhitelist(List<String> incoming, ConflictResolutionPolicy policy)`
  - If `CollectionUtils.isEmpty(incoming)` → return `null` (no component emitted)
  - Try `globalDomainWhitelistService.getDomainWhitelist()`:
    - `GlobalDomainWhitelistNotFoundException` caught → return `new ImportComponent<>(CREATE, null, incoming)`
  - Policy switch on existing list:
    - `FAIL_IF_EXISTS` → `new ImportComponent<>(FAIL, existing, incoming)`
    - `SKIP_IF_EXISTS` → `new ImportComponent<>(SKIP, existing, null)`
    - `OVERWRITE` → `new ImportComponent<>(UPDATE, existing, incoming)`

---

### Step 6 — Service layer: `ConfigImportPreviewer`

**File**: `service/config/previews/ConfigImportPreviewer.java`
- `@Slf4j @Service @LogExecution @RequiredArgsConstructor`
- Dependencies: `ImageDefinitionImportPreviewer`, `DeploymentImportPreviewer`, `GlobalDomainWhitelistImportPreviewer`
- `public ImportConfigPreview previewImport(ExportConfig config, ConflictResolutionPolicy policy)`
  - Creates `ImportConfigPreview preview = ImportConfigPreview.builder().build()`
  - Calls `imageDefinitionImportPreviewer.previewImageDefinitions(config, policy, preview)`
  - Calls `deploymentImportPreviewer.previewDeployments(config, policy, preview)`
  - Calls `preview.setGlobalImageBuildDomainWhitelist(globalDomainWhitelistImportPreviewer.previewGlobalDomainWhitelist(config.getGlobalImageBuildDomainWhitelist(), policy))`
  - Returns `preview`
- No `@Transactional` — parent `ConfigTransferService.getImportConfigPreview()` owns the transaction

---

### Step 7 — Web mapper: `ImportConfigDtoMapper`

**File**: `web/mapper/ImportConfigDtoMapper.java`
- `@Mapper(componentModel = "spring")` abstract class
- `@Autowired ImageDefinitionDtoMapper imageDefinitionDtoMapper`
- `@Autowired DeploymentDtoMapper deploymentDtoMapper`

**Method**: `public abstract ImportActionDto toActionDto(ImportAction action)` (MapStruct auto-generates)

**Method**: `public ImportConfigPreviewDto toImportConfigPreviewDto(ImportConfigPreview preview)` (manual)
- Maps each typed list by streaming and mapping `ImportComponent<T>` → `ImportComponentDto<DTO>`:
  - Image defs: call `imageDefinitionDtoMapper.toImageDefinitionDto(component.getPrev()/getNext())`
  - Deployments: call `deploymentDtoMapper.toDeploymentDto(component.getPrev()/getNext())`
  - Whitelist: `prev`/`next` are `List<String>` — no DTO conversion needed
- Handles `null` `prev`/`next` safely
- Returns `new ImportConfigPreviewDto(...)` record

**Private helper**: `private <T, D> ImportComponentDto<D> toComponentDto(ImportComponent<T> c, Function<T, D> mapper)` — maps prev/next using the supplied function, returning `null`-safe results.

---

### Step 8 — Modify `ConfigTransferService`

**File**: `service/config/ConfigTransferService.java`
- Add `ConfigImportPreviewer configImportPreviewer` to constructor (via `@RequiredArgsConstructor`)
- Add method:
  ```java
  @Transactional(readOnly = true)
  public ImportConfigPreview getImportConfigPreview(MultipartFile zipFile, ConflictResolutionPolicy resolutionPolicy) {
      // Same ZIP parsing logic as importConfig()
      // Call configImportPreviewer.previewImport(config, resolutionPolicy)
      // Return ImportConfigPreview
  }
  ```
- The ZIP parsing code (loop, entry lookup, duplicate detection, error handling) is extracted from `importConfig()` into a private `parseExportConfig(MultipartFile)` helper to avoid duplication — then both `importConfig()` and `getImportConfigPreview()` call the helper.

---

### Step 9 — Modify `ConfigController`

**File**: `web/controller/ConfigController.java`
- Add `ImportConfigDtoMapper importConfigDtoMapper` to constructor
- Add endpoint:
  ```java
  @PostMapping(path = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ImportConfigPreviewDto previewImport(
      @RequestPart("file") MultipartFile file,
      @RequestParam("resolutionPolicy") ConflictResolutionPolicy resolutionPolicy) {
      ImportConfigPreview preview = configTransfer.getImportConfigPreview(file, resolutionPolicy);
      return importConfigDtoMapper.toImportConfigPreviewDto(preview);
  }
  ```

---

### Step 10 — Unit test: `ConfigTransferServiceTest`

**File**: `src/test/java/.../service/config/ConfigTransferServiceTest.java` (modify)
- Add `@Mock ConfigImportPreviewer configImportPreviewer` field
- Add `configImportPreviewer` to `ConfigTransferService` constructor in `setUp()`
- Add test `shouldReturnImportConfigPreview_whenValidZip()`:
  - Build ZIP with an `ExportConfig`
  - Stub `configImportPreviewer.previewImport()` to return a known `ImportConfigPreview`
  - Call `configTransferService.getImportConfigPreview(multipartFile, OVERWRITE)`
  - Verify `configImportPreviewer.previewImport()` was called with correct args
- Add test `shouldNotCallPreviewer_whenInvalidZip()` (mirrors existing import error tests)

---

### Step 11 — Functional test: `ConfigExportImportFunctionalTest`

**File**: `src/test/java/.../functional/tests/ConfigExportImportFunctionalTest.java` (modify)
- Add `@Autowired ConfigImportPreviewer configImportPreviewer` and `@Autowired ConfigTransferService`
- Add test `importPreview_returnsCreate_whenEntitiesDoNotExist()`:
  - Build ZIP with all entity types
  - Call `configTransferService.getImportConfigPreview(zipFile, OVERWRITE)` on empty DB
  - Assert all components have `action = CREATE`, `prev = null`, `next != null`
- Add test `importPreview_returnsUpdate_whenEntitiesExistAndPolicyIsOverwrite()`:
  - Create entities in DB, then call preview with same ZIP
  - Assert `action = UPDATE`, `prev != null`, `next != null`
- Add test `importPreview_returnsSkip_whenEntitiesExistAndPolicyIsSkip()`:
  - Create entities, call preview with `SKIP_IF_EXISTS`
  - Assert `action = SKIP`, `prev != null`, `next = null`
- Add test `importPreview_returnsFail_whenEntitiesExistAndPolicyIsFail()`:
  - Create entities, call preview with `FAIL_IF_EXISTS`
  - Assert `action = FAIL`, `prev != null`, `next != null`
- Add test `importPreview_whitelistComponent_singleComponentForWholeList()`:
  - Set whitelist in DB, call preview with ZIP containing different whitelist
  - Assert `preview.getGlobalImageBuildDomainWhitelist()` is non-null and `action = UPDATE`
- Add test `importPreview_noWhitelistComponent_whenIncomingWhitelistIsEmpty()`

---

## Complexity Tracking

No constitution violations. No complexity justification required.
