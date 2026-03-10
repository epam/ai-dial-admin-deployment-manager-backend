# Research: Unified Deployment Source Model

**Feature**: 003-unified-deployment-source
**Date**: 2026-03-10

## Decision 1: Unified Source Sealed Interface

**Decision**: Introduce a `Source` sealed interface at the domain model level with four implementations replacing separate per-deployment-type source interfaces.

**Rationale**: Currently, source information is fragmented:
- MCP/Adapter/Interceptor: use `imageDefinitionId`, `imageDefinitionType`, `imageDefinitionName`, `imageDefinitionVersion` fields directly on the base `Deployment` class
- NIM: has its own `NimDeploymentSource` interface with `NimDeploymentNgcRegistrySource` implementation
- Inference: has its own `InferenceDeploymentSource` interface with `InferenceDeploymentHuggingFaceSource` implementation

Unifying into a single `Source` interface enables: (a) adding `ImageReferenceSource` for direct Docker image references on Knative deployments, (b) consistent JSON storage on the base `deployment` table, (c) simpler service-layer logic via pattern matching.

**Alternatives considered**:
- Keep separate interfaces and add `imageReference` as a nullable field on base Deployment — rejected because it perpetuates the scattered-fields pattern and doesn't solve the data model inconsistency
- Create a separate `KnativeSource` interface only for image-based types — rejected because it adds yet another interface without unifying the existing NIM/Inference source handling

## Decision 2: JSON Column on Base Deployment Table

**Decision**: Store the unified `source` as a JSON column (`@JdbcTypeCode(SqlTypes.JSON)`) on the base `deployment` table. Remove the `source` column from `nim_deployment` and `inference_deployment` subtype tables. Remove `image_definition_type`, `image_definition_name`, `image_definition_version` from `deployment`. Retain `image_definition_id` as a separate indexed column.

**Rationale**: JSON storage is already the proven pattern for source data in this codebase (NIM and Inference entities use `@JdbcTypeCode(SqlTypes.JSON)` for their source fields). Moving it to the base table eliminates the need for subtype-specific source columns while keeping `imageDefinitionId` available for efficient filtering queries.

**Alternatives considered**:
- Keep source on subtype tables and add a new column for Knative source — rejected because it doesn't unify the model
- Use a separate `deployment_source` table with a foreign key — rejected as over-engineering for a value object that is always loaded with the deployment

## Decision 3: Separate DTO Source Hierarchies per Deployment Family

**Decision**: Knative deployments use `DeploymentSourceDto` / `CreateDeploymentSourceRequestDto` (with `internal_image` and `image_reference` subtypes). NIM and Inference deployments retain their existing separate DTO source types (`NimDeploymentSourceDto`, `InferenceDeploymentSourceDto`) unchanged.

**Rationale**: This preserves OpenAPI documentation clarity — Swagger shows exactly which source types are valid per deployment type. The domain model is unified, but the API layer keeps deployment-family-specific source DTOs for better developer experience and Swagger rendering.

**Alternatives considered**:
- Single unified `SourceDto` across all deployment types — rejected because it produces worse Swagger API documentation (all source types shown for all deployment types, confusing consumers)

## Decision 4: Java-Based Migration V1.50

**Decision**: Use Java-based Flyway migration (`V1_50__UnifyDeploymentSource`) with a common abstract base class and vendor-specific subclasses for H2, PostgreSQL, and SQL Server.

**Rationale**: The migration involves reading JSON from one column, transforming it, and writing to another — this logic is complex enough to warrant Java rather than raw SQL. A common base class (`V1_50__UnifyDeploymentSourceBase`) avoids code duplication; vendor-specific subclasses override only DDL operations (ADD COLUMN, DROP COLUMN) that differ across database dialects.

**Alternatives considered**:
- Pure SQL migration — rejected because JSON manipulation varies significantly across H2/Postgres/MSSQL and would require three completely separate migration scripts with duplicated transformation logic

## Decision 5: MapStruct @SubclassMapping with @AfterMapping for Source

**Decision**: Use `@Mapping(target = "source", ignore = true)` on base mapper methods and handle source conversion in `@AfterMapping` methods. Remove redundant concrete mapper methods — `@SubclassMapping` on the base method auto-generates subclass implementations.

**Rationale**: MapStruct cannot automatically map between `Source` (domain) and `DeploymentSourceDto` (DTO) because they are incompatible polymorphic hierarchies. Ignoring `source` in the auto-generated mapping and handling it manually in `@AfterMapping` is the cleanest MapStruct-idiomatic approach. Removing concrete methods reduces boilerplate since `@SubclassMapping` propagates base method annotations to generated subclass methods.

**Alternatives considered**:
- Explicit concrete mapper methods per subtype — rejected as redundant boilerplate when `@SubclassMapping` handles propagation

## Decision 6: Source Type Validation in DeploymentService

**Decision**: Add `validateSourceForDeploymentType(CreateDeployment)` in `DeploymentService` to enforce that source type matches deployment type at the service layer.

**Rationale**: DTO-layer validation ensures the request is well-formed (e.g., `@NotNull`, `@ValidDockerImageName`), but cannot enforce the business rule that Knative deployments only accept `InternalImageSource` or `ImageReferenceSource` while NIM accepts only `NgcRegistrySource`. This cross-field business validation belongs in the service layer.

**Alternatives considered**:
- DTO-only validation — rejected because the DTO hierarchy already constrains input per deployment type, but the service-layer check provides defense-in-depth and clearer error messages

## Decision 7: Export Mix-In for InternalImageSource

**Decision**: Add `InternalImageSourceExportMixIn` to exclude `imageDefinitionId` from exported `InternalImageSource` objects. On import, resolve the image definition by `(imageDefinitionType, imageDefinitionName, imageDefinitionVersion)` triple.

**Rationale**: UUIDs are environment-specific and not portable across environments. The existing `DeploymentExportMixIn` already excludes `imageDefinitionId` from the base deployment; the new mix-in applies the same pattern to the nested `InternalImageSource` record.

**Alternatives considered**:
- Export with UUID and fail on import if not found — rejected because it breaks the established portable export/import contract
