# Research: Store Deployment Service Name

## R1: Migration Strategy — Java vs SQL

**Decision**: Use a **Java Flyway migration** (BaseJavaMigration) for the data backfill, with SQL DDL for the column addition.

**Rationale**: Java migrations in this project have JDBC access only (no Spring beans). However, `System.getenv("RESOURCE_NAME_PREFIX")` is available to read the configured prefix. The backfill must determine deployment type (by checking which subtable has a matching row) and apply the correct naming convention. This logic is too complex for pure SQL across three vendors.

**Alternatives considered**:
- Pure SQL migration: Rejected because deriving the service name requires conditional logic based on deployment type (subtable joins) and prefix resolution from env var. While possible in SQL, it's fragile across H2/Postgres/SQL Server dialects and harder to test.
- Application-level migration (Spring Boot startup): Rejected because it would require handling the case where some deployments are already backfilled (idempotency), and it couples application startup to data migration state.

## R2: When to Assign Service Name

**Decision**: Assign service name at **deploy time** (when `deploy()` is called), not at deployment record creation time.

**Rationale**: Deployments in NOT_DEPLOYED status have no K8s resource, so no service name is needed. The service name is generated on first `deploy()` and persisted before the K8s resource is created. On subsequent deploys (after undeploy), the existing service name is reused. This matches the spec requirement that NOT_DEPLOYED/STOPPED deployments have no service name during migration.

**Alternatives considered**:
- Assign at `createDeployment()` time: Rejected because it would assign names to deployments that may never be deployed, and the migration skip logic for NOT_DEPLOYED/STOPPED would be inconsistent.

## R3: Event Handler Lookup Strategy

**Decision**: Replace `IdExtractor` functional interface with a **repository lookup by service name**.

**Rationale**: Currently, event handlers extract the deployment ID from the K8s resource name using prefix-based parsing. After this change, the resource name IS the service name, so the handler looks up the deployment by matching `service_name` in the database. This eliminates the coupling between K8s resource naming and deployment ID extraction.

**Flow change**:
- Before: K8s event → `extractId(resourceName)` → `deploymentId` → `repository.getById(deploymentId)`
- After: K8s event → `resourceName` → `repository.getByServiceName(resourceName)` → deployment

**Alternatives considered**:
- Keep IdExtractor and also store service name: Rejected because it maintains the fragile naming convention dependency that we're trying to eliminate.
- In-memory cache of service name → deployment ID: Rejected because it adds complexity without benefit; a DB index on `service_name` provides efficient lookups.

## R4: Migration Version Numbering

**Decision**: Use version **V1.52** for DDL (SQL) and **V1.53** for data backfill (Java).

**Rationale**: The latest migration is V1.51 (H2-only fix for double-encoded source). V1.50 is the latest shared version. Two separate versions avoid conflicts:
- SQL migration `V1.52__AddServiceNameColumn.sql` for DDL (add column + unique index) — vendor-specific syntax
- Java migration `V1_53__BackfillServiceName.java` for data backfill — reads env var, determines type, generates names

**Note**: Flyway orders by version number, so V1.52 (DDL) runs before V1.53 (backfill).

## R5: Service Name Generation for Backfill

**Decision**: The Java migration replicates the naming logic from `K8sNamingUtils` to generate service names.

**Rationale**: Java migrations cannot access Spring beans, so `K8sNamingUtils` (which requires `NamingUtilsConfig` initialization) is not available. The migration must inline the naming logic:
- Read `RESOURCE_NAME_PREFIX` via `System.getenv()`
- If prefix is blank: use `"mcp"` for Knative/NIM types, `"dm"` for Inference
- If prefix is set: use that prefix for all types
- Format: `{prefix}-{deploymentId}`

This is a one-time operation, so duplicating the simple concatenation logic is acceptable.

**Alternatives considered**:
- Call `K8sNamingUtils` statically without Spring initialization: Rejected because the static `resourceNamePrefix` field wouldn't be set.
- Initialize `K8sNamingUtils` manually in the migration: Rejected because it creates a fragile coupling between migration code and utility class internals.

## R6: Uniqueness Constraint on service_name

**Decision**: Add a **unique index** on `service_name` that allows NULLs.

**Rationale**: The spec requires uniqueness (FR-006). NOT_DEPLOYED/STOPPED deployments may have NULL service names, and multiple NULLs should be allowed. All three databases (H2, PostgreSQL, SQL Server) allow multiple NULLs in unique indexes by default (SQL Server requires a filtered index or allows it in modern versions).

**Alternatives considered**:
- Application-level uniqueness check only: Rejected because race conditions could allow duplicates.
- Non-null column with empty string default: Rejected because it conflates "no service name" with "empty name" and complicates queries.

## R7: Unified Naming Convention for New Deployments

**Decision**: All new deployments use `K8sNamingUtils.generateName(id)` (DM prefix by default), replacing the legacy split where Knative/NIM used `generateMcpPrefixedName`.

**Rationale**: Per the spec (FR-007), the MCP prefix distinction is legacy. Since service names are now stored, the naming convention only matters at generation time. Unifying under `generateName` simplifies the code and eliminates the deprecated `generateMcpPrefixedName` / `extractMcpPrefixedId` methods.

**Impact**: New Knative/NIM deployments will have `dm-{id}` names (or `{customPrefix}-{id}`) instead of `mcp-{id}`. Existing deployments keep their migrated names. The `IdExtractor` interface and `extractMcpPrefixedId`/`extractId`/`generateMcpPrefixedName` methods are removed entirely (not just deprecated), as event handlers no longer use ID extraction and all new deployments use unified `generateName`.

**Refactoring**: Since all deployment manager subclasses (Knative, NIM, Inference) now use the stored service name identically, `getServiceName()` is moved to `AbstractDeploymentManager` where it reads the stored `serviceName` from the deployment record. The per-type overrides in child classes are removed.

## R8: DisposableResourceManager Changes

**Decision**: Update `DisposableResourceManager.generateServiceName()` to accept the stored service name instead of generating it.

**Rationale**: The `generateServiceName(id, kind)` method currently uses the same naming conventions as deployment managers. After this change, the service name is already known (stored in the deployment record), so the method should receive it as a parameter rather than regenerating it.

**Impact**: All callers of `saveKnativeServiceResource`, `saveNimServiceResource`, `saveInferenceServiceResource`, and their cleanup counterparts need to pass the stored service name.
