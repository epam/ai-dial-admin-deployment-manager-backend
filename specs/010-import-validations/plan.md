# Implementation Plan: Import Validations

**Branch**: `010-import-validations` | **Date**: 2026-03-19 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/010-import-validations/spec.md`

## Summary

Config import currently bypasses all validation that the REST API enforces via `@Valid` on DTOs. Additionally, a malicious user can craft a ZIP containing system-managed fields (e.g., `status`, `author`, `buildStatus`) that are normally excluded during export via Jackson MixIns but get deserialized during import because the regular `jsonMapper` (without MixIns) is used.

The fix introduces two steps between ZIP parsing and entity persistence: (1) **sanitization** — the parsed `ExportConfig` is round-tripped through `exportJsonMapper` (which has MixIns) to strip all system-managed fields uniformly; (2) **validation** — sanitized domain objects are mapped to their corresponding request DTOs using MapStruct reverse mappings, then validated programmatically using `jakarta.validation.Validator`. All violations are collected and reported. Sanitization lives in the service layer (`ConfigTransferService`); validation lives in the web layer (`ImportConfigValidator`). The controller orchestrates: parseAndSanitize (service) → validate (web) → import (service), with all service-layer calls routed through `ConfigTransferService` to maintain encapsulation.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: MapStruct 1.6.0, Jakarta Validation 3.0.2, Lombok, Jackson 2.21.1
**Storage**: N/A (no schema changes)
**Testing**: JUnit 5 + AssertJ + H2 (testFast), Testcontainers (full suite)
**Target Platform**: JVM (Spring Boot web service)
**Project Type**: web-service
**Performance Goals**: N/A (validation adds negligible overhead to import)
**Constraints**: Must not break existing valid imports; must respect strict layered architecture
**Scale/Scope**: ~10 files modified, ~5 new files, ~200-300 lines of new code

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|---|---|---|
| Strict Layered Architecture | PASS | `ImportConfigValidator` lives in `web/validation/`, called from controller. No service→web dependency. |
| Transactional Discipline | PASS | No `@Transactional` on controller. Import transaction boundary unchanged (`ConfigImporter`). |
| Kubernetes Isolation | PASS | No K8s changes. |
| Observability First | PASS | `@LogExecution` on all new Spring components. |
| Security by Configuration | PASS | No security changes. |
| Naming Conventions | PASS | `ImportConfigValidator` (validator), `ImportValidationException` (exception), `ImportValidationError` (model). |
| Code Style | PASS | Google Java Style, 180-char lines, no wildcard imports. |
| API Conventions | PASS | Error response uses existing `ErrorView` schema; `@Operation`/`@ApiResponse` annotations updated. |
| Testing Conventions | PASS | `shouldDoX()` / `shouldFailDoX_whenY()` naming; functional + unit tests. |
| MapStruct Pattern | PASS | `componentModel = "spring"`, `SubclassExhaustiveStrategy.RUNTIME_EXCEPTION` on polymorphic mappers. |
| `@LogExecution` | PASS | Applied to `ImportConfigValidator`. |
| Anti-Patterns | PASS | No business logic in entities; no silent swallowing; specific exceptions. |

**Post-Phase 1 re-check**: All gates still PASS. No violations introduced by the design.

## Project Structure

### Documentation (this feature)

```text
specs/010-import-validations/
├── plan.md              # This file
├── research.md          # Phase 0: approach decision + alternatives
├── data-model.md        # Phase 1: entities and mapping relationships
├── contracts/           # Phase 1: API contract changes
│   └── import-validation-errors.md
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── exception/
│   └── ImportValidationException.java          # NEW: wraps List<ImportValidationError>
├── model/config/
│   └── ImportValidationError.java              # NEW: entity type + identifier + field + message
├── web/
│   ├── controller/
│   │   └── ConfigController.java               # MODIFIED: split import into parseAndSanitize→validate→import (all service calls via ConfigTransferService)
│   ├── handler/
│   │   └── DefaultExceptionHandler.java        # MODIFIED: add ImportValidationException handler
│   ├── mapper/
│   │   ├── DeploymentDtoMapper.java            # MODIFIED: add Deployment→CreateDeploymentRequestDto
│   │   └── ImageDefinitionDtoMapper.java       # MODIFIED: add ImageDefinition→ImageDefinitionRequestDto
│   └── validation/
│       └── ImportConfigValidator.java          # NEW: maps domain→DTO, validates, collects errors
├── service/config/
│   ├── ConfigTransferService.java              # MODIFIED: add parseAndSanitizeExportConfig(), importConfig(ExportConfig, ...), getImportConfigPreview(ExportConfig, ...)
│   ├── imports/
│   │   └── ImageDefinitionImporter.java        # MODIFIED: remove cloneImageDefinition(), simplify mergeForOverwrite()
│   └── previews/
│       └── ConfigImportPreviewer.java          # MODIFIED: integrate validation into preview
└── ...

src/test/java/com/epam/aidial/deployment/manager/
├── web/validation/
│   └── ImportConfigValidatorTest.java          # NEW: unit tests for validation logic
└── functional/h2/
    └── ConfigImportValidationFunctionalTest.java  # NEW: end-to-end import validation tests
```

**Structure Decision**: All new production code fits within existing packages. `ImportConfigValidator` in `web/validation/` alongside existing custom validators. `ImportValidationError` in `model/config/` alongside `ExportConfig` and `ImportConfigPreview`. `ImportValidationException` in `exception/` alongside existing custom exceptions.

## Design Decisions

### D0: Sanitize imported config via exportJsonMapper round-trip

**Context**: Import uses the regular `jsonMapper` (no MixIns) for deserialization, meaning ALL fields from the ZIP are deserialized — including fields normally excluded during export by `DeploymentExportMixIn`, `ImageDefinitionExportMixIn`, `InternalImageSourceExportMixIn`, and `SensitiveEnvVarExportMixIn`. A malicious user can inject values for `status`, `author`, `url`, `serviceName`, `buildStatus`, `imageName`, `k8sSecretName`, etc.

**Security findings**:
- **Deployments**: Most system-managed fields are dropped by the `Deployment → CreateDeployment` mapping (CreateDeployment lacks those fields). However, `author` survives to DB because CreateDeployment HAS an author field, and `DeploymentService` uses it when non-blank.
- **ImageDefinitions**: The `cloneImageDefinition()` method already round-trips through `exportJsonMapper`, which strips MixIn-excluded fields. But this happens late in the flow (inside `ImageDefinitionImporter`), not at the entry point.
- **Source.imageDefinitionId**: Excluded by `InternalImageSourceExportMixIn` but could survive through the `Deployment.source` field.

**Decision**: After parsing the ZIP, round-trip the entire `ExportConfig` through `exportJsonMapper`: `exportJsonMapper.readValue(exportJsonMapper.writeValueAsBytes(config), ExportConfig.class)`. This strips all MixIn-excluded fields uniformly before any further processing.

**Rationale**: One-liner that leverages existing MixIn infrastructure. Strips ALL excluded fields for ALL entity types at a single point. No need to enumerate fields manually. Any future MixIn additions automatically apply. The `ImageDefinitionImporter.cloneImageDefinition()` step becomes redundant for sanitization but remains harmless.

**Trade-off**: Extra serialize/deserialize cycle at the entry point. Negligible for import payloads (typically <1MB). Accepted for the security guarantee.

**Follow-up simplification**: `ImageDefinitionImporter.cloneImageDefinition()` becomes fully redundant — it served two purposes: (1) sanitization via `exportJsonMapper` round-trip (now handled at entry point), and (2) defensive copying so mutations don't affect the original ExportConfig object (unnecessary because each map entry is processed exactly once and the ExportConfig is ephemeral). Remove `cloneImageDefinition()` and simplify `mergeForOverwrite()` to mutate the imported object directly.

### D1: Validate via DTO mapping (not domain annotations)

**Context**: Validation annotations exist only on request DTOs. Domain models have none.

**Decision**: Map domain objects → request DTOs using new MapStruct reverse mappings, then validate DTOs using programmatic `jakarta.validation.Validator`. No annotations added to domain models.

**Rationale**: Keeps DTOs as the single source of truth for validation rules. No annotation duplication. No drift risk. All existing custom validators work automatically.

**Trade-off**: Requires new mapper methods. Adds a mapping step before validation. Accepted because MapStruct generates efficient mapping code and field names match 1:1.

### D2: Validation in web layer, called from controller

**Context**: Constitution mandates strict `web → service → dao` dependency direction.

**Decision**: `ImportConfigValidator` lives in `web/validation/`. `ConfigController` calls: parseAndSanitize (service) → validate (web) → import (service). All service-layer calls go through `ConfigTransferService` — the controller does not inject `ConfigImporter` directly.

**Rationale**: The validator depends on web-layer DTOs and mappers. Placing it in the web layer respects the architecture. `ConfigTransferService` remains the single service-layer entry point for the controller, keeping the controller thin and maintaining encapsulation of the import orchestration within the service layer.

**Trade-off**: Controller becomes slightly more orchestrating (validate step between two service calls). Accepted because the alternative (service → web dependency) violates architecture.

### D3: Collect all violations before failing

**Context**: Spec requires all violations reported together (FR-005).

**Decision**: Iterate all entities in `ExportConfig`, validate each, collect all `ConstraintViolation` objects, then throw a single `ImportValidationException` if any exist.

**Rationale**: Administrators can fix all issues in one pass instead of iterating through one-at-a-time failures.

### D4: Preview surfaces errors in response (not exception)

**Context**: Preview is read-only; failing it with an exception is unfriendly.

**Decision**: For preview, validation errors are returned as a field in `ImportConfigPreview` / `ImportConfigPreviewDto` rather than thrown as an exception.

**Rationale**: Allows the user to see both conflict information AND validation errors in a single preview response.

### D5: Restructure ConfigTransferService as the single service-layer entry point

**Context**: Currently `ConfigTransferService.importConfig(MultipartFile, ConflictResolutionPolicy)` encapsulates parsing + importing in a single call. The controller needs the parsed `ExportConfig` between service calls to run web-layer validation.

**Decision**: Add three public methods to `ConfigTransferService`:
1. `parseAndSanitizeExportConfig(MultipartFile)` — parses the ZIP and sanitizes via `exportJsonMapper` round-trip, returns `ExportConfig`
2. `importConfig(ExportConfig, ConflictResolutionPolicy)` — delegates to `ConfigImporter.importConfig()` (new overload accepting pre-parsed config)
3. `getImportConfigPreview(ExportConfig, ConflictResolutionPolicy)` — delegates to `ConfigImportPreviewer.previewImport()` (new overload accepting pre-parsed config)

The existing `importConfig(MultipartFile, ...)` and `getImportConfigPreview(MultipartFile, ...)` methods can be kept for backward compatibility or removed if no other callers exist.

**Rationale**: The controller only interacts with `ConfigTransferService` for all service-layer operations. `ConfigImporter` and `ConfigImportPreviewer` remain encapsulated behind `ConfigTransferService`. This keeps the controller thin and avoids leaking internal service dependencies.

## Implementation Sequence

1. **New model classes**: `ImportValidationError`, `ImportValidationException`
2. **ConfigTransferService**: Add `parseAndSanitizeExportConfig(MultipartFile)` (combines parse + sanitize), `importConfig(ExportConfig, ConflictResolutionPolicy)` (delegates to ConfigImporter), and `getImportConfigPreview(ExportConfig, ConflictResolutionPolicy)` (delegates to ConfigImportPreviewer)
3. **ImageDefinitionImporter**: Remove `cloneImageDefinition()` and `exportJsonMapper` dependency; simplify `mergeForOverwrite()` to mutate imported object directly
4. **Reverse mapper methods**: `DeploymentDtoMapper`, `ImageDefinitionDtoMapper`
5. **ImportConfigValidator**: Core validation logic (maps domain → DTO, validates, collects errors)
6. **ConfigController**: Wire parseAndSanitize → validate → import (all service calls via ConfigTransferService)
7. **DefaultExceptionHandler**: Handle `ImportValidationException`
8. **Preview integration**: Add validation errors to preview response/model
9. **Tests**: Unit tests for `ImportConfigValidator` (`ImportConfigValidatorTest.java`); functional tests for import validation, sanitization, preview, and backward compatibility (`ConfigImportValidationFunctionalTest.java`)
10. **Checkstyle + build verification**
