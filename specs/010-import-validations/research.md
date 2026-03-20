# Research: Import Validations

## R1: Where does validation currently happen?

**Decision**: Validation is entirely at the web layer via Jakarta `@Valid` on controller method parameters.

**Findings**:
- Controllers use `@Valid` on `CreateDeploymentRequestDto` and `ImageDefinitionRequestDto` parameters
- Spring's `MethodArgumentNotValidException` is caught by `DefaultExceptionHandler` (400 BAD_REQUEST)
- `ConstraintViolationException` is also handled (400 BAD_REQUEST)
- No programmatic `jakarta.validation.Validator` usage exists anywhere in the codebase
- Custom validators live in `web/validation/`: `@ValidSemanticVersion`, `@ValidDockerImageName`, `@ValidDomainList`, `@ValidTopics`, `@ValidResources`, `@ValidScaling`, `@NoPathTraversal`, `@NoSurroundingWhitespace`
- Domain model classes have zero validation annotations

## R2: What is the import flow and where is the gap?

**Decision**: The gap is at `ConfigTransferService.parseExportConfig()` (line 112) — Jackson deserializes directly into domain models with no validation.

**Findings**:
- Import flow: `ConfigController.importConfig()` → `ConfigTransferService.importConfig()` → `parseExportConfig()` → `ConfigImporter.importConfig()`
- `parseExportConfig()` uses `jsonMapper.readValue(zipInputStream, ExportConfig.class)` — pure deserialization
- `ExportConfig` contains maps of domain objects: `Deployment` subtypes and `ImageDefinition` subtypes
- `ConfigImporter.importConfig()` is `@Transactional` and calls individual importers
- `DeploymentImporter` already maps `Deployment → CreateDeployment` via `DeploymentMapper.toCreateDeployment()` before calling `DeploymentService`
- `ImageDefinitionImporter` works directly with `ImageDefinition` domain objects (clones via JSON serialization)
- `GlobalDomainWhitelistImporter` works with `List<String>` (simple merge logic)

## R3: What approach best validates imported entities?

**Decision**: Map domain objects → request DTOs in the web layer, then validate the DTOs programmatically using `jakarta.validation.Validator`. This keeps DTOs as the single source of truth for validation.

**Rationale**:
- DTOs already have comprehensive validation annotations — reusing them avoids any duplication
- Custom validators are already production-tested and work automatically via Spring's `LocalValidatorFactoryBean`
- Field names between domain models and request DTOs match almost 1:1 (only `Deployment.id` ↔ `name` mapping needed)
- MapStruct reverse mappings are straightforward to add
- Respects the layered architecture: validation component lives in web layer, called from controller

**Alternatives considered**:

| Alternative | Why Rejected |
|---|---|
| Add annotations to domain models | Massive annotation duplication across Deployment, ImageDefinition, and all their nested types (DeploymentMetadata, Scaling, Resources, ProbeProperties, ImageSource, etc.). Drift risk between DTOs and domain annotations. |
| Add annotations to CreateDeployment | Only works for deployments; ImageDefinition has no Create* equivalent. Still duplicates annotations. |
| Validate in service layer using DTOs | Violates layered architecture (service → web dependency). |
| Custom programmatic validation service | Code duplication of validation rules; divergence risk; doesn't benefit from existing custom validators. |

## R4: How do domain model fields map to DTO fields?

**Decision**: Fields match 1:1 with minor exceptions. MapStruct reverse mapping is feasible.

**Findings**:

| Domain Field | DTO Field | Notes |
|---|---|---|
| `Deployment.id` | `CreateDeploymentRequestDto.name` | Explicit `@Mapping(target="id", source="name")` |
| `Deployment.command` (List) | `CreateDeploymentRequestDto.command` (String) | Join/split via `CommandLineUtils` |
| `Deployment.args` (List) | `CreateDeploymentRequestDto.args` (String) | Join/split via `CommandLineUtils` |
| All other Deployment fields | Same names | Exact match |
| All ImageDefinition fields | Same names | Exact match; system fields (id, createdAt, etc.) ignored |
| Nested objects | DTO equivalents | Scaling↔ScalingDto, Resources↔ResourcesDto, etc. — existing mappers handle these |

Existing mappers already handle all type conversions (`McpTransport` ↔ `McpTransportDto`, `ImageSource` ↔ `ImageSourceDto`, etc.).

## R5: How should validation errors be reported?

**Decision**: Collect all violations across all entities; throw a single exception with structured error details. The exception handler formats it as a 400 BAD_REQUEST response.

**Findings**:
- `DefaultExceptionHandler` already handles `MethodArgumentNotValidException` (for DTO validation) and `ConstraintViolationException`
- For import, we need to report: entity type, entity identifier (name/id), field path, and violation message
- A new `ImportValidationException` wrapping all violations is cleaner than reusing `ConstraintViolationException` because it allows grouping by entity
- The exception handler can format the message similarly to how `MethodArgumentNotValidException` is handled today

## R6: Can malicious fields in a crafted ZIP reach the database?

**Decision**: Yes, for some fields. A sanitization step is required before validation and persistence.

**Findings**:
- Import uses the regular `jsonMapper` (no MixIns) to deserialize the ZIP. This means ALL fields are deserialized, including those that `exportJsonMapper` normally strips via MixIns.
- Four MixIns exist: `DeploymentExportMixIn` (strips `url`, `status`, `serviceName`, `author`, `createdAt`, `updatedAt`, `imageDefinitionId`), `ImageDefinitionExportMixIn` (strips `id`, `imageName`, `buildStatus`, `buildLogs`, `builtAt`, `author`, `createdAt`, `updatedAt`), `InternalImageSourceExportMixIn` (strips `imageDefinitionId`), `SensitiveEnvVarExportMixIn` (strips `k8sSecretName`, `k8sSecretKey`).
- **Deployment `author`**: Survives through `Deployment → CreateDeployment` mapping (CreateDeployment has an `author` field) and reaches `DeploymentService.createDeployment()` which uses it when non-blank. A malicious user can spoof authorship.
- **Deployment `source.imageDefinitionId`**: Could survive through direct source mapping.
- **ImageDefinition fields**: Safely stripped by `cloneImageDefinition()` which uses `exportJsonMapper`. But this happens late in the flow, inside the importer.

**Solution**: Round-trip the entire `ExportConfig` through `exportJsonMapper` immediately after parsing: `exportJsonMapper.readValue(exportJsonMapper.writeValueAsBytes(config), ExportConfig.class)`. This strips all MixIn-excluded fields uniformly at a single point.

**Alternatives considered**:

| Alternative | Why Rejected |
|---|---|
| Manually null out specific fields | Fragile; must be updated whenever MixIns change. No guarantee of parity. |
| Use `exportJsonMapper` for import deserialization | Would prevent deserialization of fields that the domain model expects but export excludes (e.g., import might receive valid data that export intentionally strips). Changes the contract. |
| Rely on downstream code (service layer) to ignore fields | Already partially works (Deployments via CreateDeployment, ImageDefinitions via cloneImageDefinition), but has gaps (`author` on deployments, `source.imageDefinitionId`). Defense-in-depth favors explicit sanitization. |

## R7: How should preview validation work?

**Decision**: Same sanitization and validation logic applies to import preview. Validation errors should be included in the preview response rather than throwing an exception, so the user sees both conflict info and validation errors.

**Findings**:
- `ConfigImportPreviewer.previewImport()` builds `ImportConfigPreview` with per-entity conflict status
- Adding validation errors to the preview response (as an additional field) is more user-friendly than failing the preview
- The actual import endpoint should still fail-fast on validation errors (reject the import)
- Sanitization must also apply to preview (strip MixIn-excluded fields before previewing)
