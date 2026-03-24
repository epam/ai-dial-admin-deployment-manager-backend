# Research: Application Image Definition & Deployment Type

## R-001: Adapter Type Implementation Pattern

**Decision**: Mirror the Adapter type exactly — all Application classes are empty marker subclasses extending the same parent classes as Adapter.

**Rationale**: The Adapter type is the simplest image-based type (no extra fields, unlike MCP which has `transportType` and `mcpEndpointPath`). Application intentionally mirrors it for future divergence. Following the same pattern ensures consistency and zero risk.

**Alternatives considered**:
- Reusing Adapter classes with a type flag → Rejected: prevents independent evolution, violates polymorphic design
- Creating a shared base class between Adapter and Application → Rejected: over-engineering for zero current behavioral difference

## R-002: Database Migration Versioning

**Decision**: Use V1.54 for the new migration (current latest is V1.52 in SQL, V1.53 in Java migrations).

**Rationale**: Next available version number. All three database vendors (H2, Postgres, MS SQL Server) must receive the same migration.

**Alternatives considered**: None — Flyway versioning is sequential.

## R-003: Knative Deployment Manager Support

**Decision**: Add `ApplicationDeployment.class` to `KnativeDeploymentManager.getSupportedDeploymentClasses()` and its `getDeploymentOptional()` instanceof guard.

**Rationale**: Application uses Knative services just like Adapter. The KnativeDeploymentManager already supports MCP, Interceptor, and Adapter. Adding Application follows the same registration pattern.

**Alternatives considered**:
- Separate ApplicationDeploymentManager → Rejected: identical behavior, unnecessary duplication

## R-004: Source Type Validation

**Decision**: Application deployments accept `InternalImageSource` and `ImageReferenceSource`, same as Adapter. The existing `validateSourceForDeploymentType()` default case already handles this — `CreateApplicationDeployment` falls through to the default branch which allows these two source types.

**Rationale**: The switch in `DeploymentService.validateSourceForDeploymentType()` only has explicit cases for NIM (NgcRegistrySource) and Inference (HuggingFaceSource). The `default` branch handles MCP, Adapter, Interceptor — and will automatically handle Application.

**Alternatives considered**: None — the existing default case is correct for Application.

## R-005: Config Import/Export

**Decision**: Add `applicationImageDefinitions` and `applicationDeployments` maps to `ExportConfig`, add switch cases in `ConfigExporter`, and add `importMap()` calls in importers.

**Rationale**: Each type has its own typed map in ExportConfig. This is the established pattern for all types.

**Alternatives considered**: None — must follow existing pattern for serialization compatibility.

## R-006: JSON Polymorphic Discriminator

**Decision**: Use `"application"` as the `$type` discriminator value (lowercase).

**Rationale**: Follows existing convention: `"mcp"`, `"adapter"`, `"interceptor"`, `"nim"`, `"inference"`.

**Alternatives considered**: None — naming convention is established.

## R-007: Existing Type Validation Gap

**Decision**: No cross-type validation exists (e.g., nothing prevents an Adapter deployment from referencing an MCP image definition). This is the current state for all types. Application will have the same behavior — no additional validation needed for parity.

**Rationale**: Adding cross-type validation is out of scope for this feature. The spec's FR-009 describes the expected behavior but the system currently doesn't enforce it for any type.

**Alternatives considered**:
- Add type validation for Application only → Rejected: inconsistent with other types
- Add type validation for all types → Rejected: out of scope, separate feature
