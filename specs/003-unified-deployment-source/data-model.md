# Data Model: Unified Deployment Source

**Feature**: 003-unified-deployment-source
**Date**: 2026-03-10

## Domain Model

### Source (sealed interface)

Polymorphic value object representing the origin of a deployment's container image or model. Discriminated by `$type` in JSON serialization.

| Variant | `$type` | Fields | Used By |
|---------|---------|--------|---------|
| `InternalImageSource` | `internal_image` | `imageDefinitionId: UUID`, `imageDefinitionType: ImageType`, `imageDefinitionName: String`, `imageDefinitionVersion: String` | MCP, Adapter, Interceptor |
| `ImageReferenceSource` | `image_reference` | `imageReference: String` (valid Docker image name) | MCP, Adapter, Interceptor |
| `HuggingFaceSource` | `huggingface` | `modelName: String` (valid HuggingFace model name) | Inference |
| `NgcRegistrySource` | `ngc_registry` | `imageRef: String` (valid Docker image name) | NIM |

**Validation rules**:
- `InternalImageSource`: requires either `imageDefinitionId` OR all of (`imageDefinitionType`, `imageDefinitionName`, `imageDefinitionVersion`)
- `ImageReferenceSource.imageReference`: must pass `@ValidDockerImageName` validation
- `HuggingFaceSource.modelName`: must pass `@ValidHuggingFaceModelName` validation
- `NgcRegistrySource.imageRef`: must pass `@ValidDockerImageName` validation

**Source-to-deployment-type mapping** (enforced at service layer):

| Deployment Type | Accepted Source Types |
|----------------|----------------------|
| MCP | `InternalImageSource`, `ImageReferenceSource` |
| Adapter | `InternalImageSource`, `ImageReferenceSource` |
| Interceptor | `InternalImageSource`, `ImageReferenceSource` |
| Inference | `HuggingFaceSource` |
| NIM | `NgcRegistrySource` |

### Deployment (base class, modified)

**Fields removed** from base:
- `imageDefinitionType: ImageType` (moved into `InternalImageSource`)
- `imageDefinitionName: String` (moved into `InternalImageSource`)
- `imageDefinitionVersion: String` (moved into `InternalImageSource`)

**Fields added** to base:
- `source: Source` — nullable; present for all deployment types

**Fields retained** on base (unchanged):
- `imageDefinitionId: UUID` — kept as a separate field for efficient query filtering (e.g., `findByImageDefinitionId`)

### CreateDeployment (base class, modified)

Same field changes as `Deployment`: replaced `imageDefinitionId`, `imageDefinitionType`, `imageDefinitionName`, `imageDefinitionVersion` with `source: Source`.

### Renamed Domain Classes

| Old Name | New Name |
|----------|----------|
| `InferenceDeploymentSource` | _(removed — replaced by `Source`)_ |
| `InferenceDeploymentHuggingFaceSource` | `HuggingFaceSource` |
| `NimDeploymentSource` | _(removed — replaced by `Source`)_ |
| `NimDeploymentNgcRegistrySource` | `NgcRegistrySource` |

## Persistence Model

### PersistenceSource (sealed interface)

Mirrors the domain `Source` hierarchy for JPA persistence. Stored as `@JdbcTypeCode(SqlTypes.JSON)` on `DeploymentEntity`.

| Variant | Fields |
|---------|--------|
| `PersistenceInternalImageSource` | `imageDefinitionType: PersistenceImageType`, `imageDefinitionName: String`, `imageDefinitionVersion: String` |
| `PersistenceImageReferenceSource` | `imageReference: String` |
| `PersistenceHuggingFaceSource` | `modelName: String` |
| `PersistenceNgcRegistrySource` | `imageRef: String` |

**Note**: `PersistenceInternalImageSource` does NOT contain `imageDefinitionId` — that remains a separate column on `DeploymentEntity`.

### DeploymentEntity (modified)

**Columns removed**:
- `image_definition_type` (VARCHAR/ENUM)
- `image_definition_name` (VARCHAR)
- `image_definition_version` (VARCHAR)

**Columns added**:
- `source` (JSON) — stores `PersistenceSource` as JSON

**Columns retained**:
- `image_definition_id` (UUID) — separate column for indexing/filtering

### InferenceDeploymentEntity / NimDeploymentEntity (modified)

**Columns removed**:
- `source` (JSON) — moved to base `DeploymentEntity`

### Removed Persistence Classes

| Old Name | Replacement |
|----------|-------------|
| `PersistenceInferenceDeploymentSource` | `PersistenceSource` (on base entity) |
| `PersistenceInferenceDeploymentHuggingFaceSource` | `PersistenceHuggingFaceSource` |
| `PersistenceNimDeploymentSource` | `PersistenceSource` (on base entity) |
| `PersistenceNimDeploymentNgcRegistrySource` | `PersistenceNgcRegistrySource` |

## DTO Model

### Response DTOs (for Knative deployments)

**DeploymentSourceDto** (sealed interface, `$type` discriminator):

| Variant | `$type` | Fields |
|---------|---------|--------|
| `InternalImageDeploymentSourceDto` | `internal_image` | `imageDefinitionId: @NotNull UUID`, `imageDefinitionName: @NotNull String`, `imageDefinitionVersion: @NotNull String` |
| `ImageReferenceDeploymentSourceDto` | `image_reference` | `imageReference: @NotNull String` |

**ImageBasedDeploymentDto** (modified):
- Removed: `imageDefinitionId`, `imageDefinitionType`, `imageDefinitionName`, `imageDefinitionVersion`
- Added: `source: @NotNull @Valid DeploymentSourceDto`

### Request DTOs (for Knative deployments)

**CreateDeploymentSourceRequestDto** (sealed interface, `$type` discriminator):

| Variant | `$type` | Fields |
|---------|---------|--------|
| `CreateInternalImageDeploymentSourceRequestDto` | `internal_image` | `imageDefinitionId: @Nullable UUID`, `imageDefinitionType: @Nullable ImageTypeDto`, `imageDefinitionName: @Nullable String`, `imageDefinitionVersion: @Nullable String` + `@AssertTrue isValidImageReference()` |
| `CreateImageReferenceDeploymentSourceRequestDto` | `image_reference` | `imageReference: @NotNull @ValidDockerImageName String` |

**CreateImageBasedDeploymentRequestDto** (modified):
- Removed: `imageDefinitionId`, `imageDefinitionName`, `imageDefinitionVersion`, `imageDefinitionType`, `isValidImageReference()`
- Added: `source: @NotNull @Valid CreateDeploymentSourceRequestDto`

### NIM & Inference DTOs (unchanged)

NIM and Inference deployment DTOs retain their existing separate source type interfaces:
- `NimDeploymentSourceDto` / `NimDeploymentNgcRegistrySourceDto`
- `InferenceDeploymentSourceDto` / `InferenceDeploymentHuggingFaceSourceDto`

## Database Migration V1.50

**Migration type**: Java-based (complex JSON data transformation)
**Version**: V1.50
**Class hierarchy**: `V1_50__UnifyDeploymentSourceBase` (abstract) → vendor-specific subclasses

### Migration steps:

1. **Add** `source` JSON column to `deployment` table
2. **Migrate NIM sources**: Copy `nim_deployment.source` → `deployment.source` (data already in correct JSON format)
3. **Migrate Inference sources**: Copy `inference_deployment.source` → `deployment.source` (data already in correct JSON format)
4. **Migrate internal image sources**: Build `{"$type":"internal_image","imageDefinitionType":"...","imageDefinitionName":"...","imageDefinitionVersion":"..."}` from `deployment.image_definition_type`, `deployment.image_definition_name`, `deployment.image_definition_version` columns → `deployment.source`
5. **Drop** columns: `image_definition_type`, `image_definition_name`, `image_definition_version` from `deployment`; `source` from `nim_deployment`; `source` from `inference_deployment`
6. **Add constraint** (MSSQL only): `CHECK (source IS NULL OR isjson(source) > 0)`

### Vendor differences:
- **H2**: Standard `ALTER TABLE ADD COLUMN` / `DROP COLUMN`
- **PostgreSQL**: Standard `ALTER TABLE ADD COLUMN` / `DROP COLUMN`; JSON type is `jsonb`
- **MSSQL**: `ALTER TABLE ADD` / `DROP COLUMN`; adds `isjson` check constraint

## Entity Relationship Changes

```
Before:
  DeploymentEntity                    InferenceDeploymentEntity    NimDeploymentEntity
  ├── image_definition_id (UUID)      ├── source (JSON)            ├── source (JSON)
  ├── image_definition_type (ENUM)    ├── model_format             ├── container_grpc_port
  ├── image_definition_name (VARCHAR) ├── command
  └── image_definition_version (VARCHAR) └── args

After:
  DeploymentEntity                    InferenceDeploymentEntity    NimDeploymentEntity
  ├── image_definition_id (UUID)      ├── model_format             ├── container_grpc_port
  └── source (JSON)                   ├── command
                                      └── args
```
