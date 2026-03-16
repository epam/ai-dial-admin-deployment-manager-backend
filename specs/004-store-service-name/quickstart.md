# Quickstart: Store Deployment Service Name

## Overview

This feature adds a `service_name` column to the `deployment` table that persists the Kubernetes service name. This decouples deployment identity from naming conventions, making deployments resilient to `resourceNamePrefix` changes.

## Key Changes

### 1. Database Migration (V1.52 SQL + V1.53 Java)
- Adds `service_name` column with unique index
- Backfills active deployments using current prefix + type-specific conventions
- Skips NOT_DEPLOYED/STOPPED deployments (no K8s resource exists)

### 2. Deploy Flow
- On first `deploy()`, generate service name via `K8sNamingUtils.generateName(id)` and persist
- On subsequent deploys (after undeploy), reuse the stored service name
- All K8s operations use stored name instead of deriving it

### 3. Event Handling
- Event handlers look up deployments by `service_name` instead of extracting IDs from resource names
- `IdExtractor` interface removed entirely

### 4. Unified Naming & Refactoring
- All new deployments use `generateName(id)` regardless of type
- `generateMcpPrefixedName`, `extractMcpPrefixedId`, `extractId` removed from `K8sNamingUtils`
- `getServiceName()` moved from child deployment managers to `AbstractDeploymentManager` (reads stored name)

## Implementation Order

1. **Migration** — Add column + backfill (can be tested independently)
2. **Entity/DAO** — Add field to entity, domain model, mappers, repository query
3. **Service layer** — Generate and persist service name in deploy flow
4. **Event handlers** — Replace IdExtractor with service name lookup
5. **DisposableResourceManager** — Use stored service name
6. **Cleanup** — Remove IdExtractor, extractMcpPrefixedId, extractId, generateMcpPrefixedName

## Testing Strategy

- **Functional tests**: Verify migration backfills correctly, deploy persists service name, undeploy/redeploy preserves name
- **Event handler tests**: Verify lookup by service name works, unknown names are ignored
- **Multi-vendor**: Migration tested against H2 (testFast), Postgres and SQL Server (full suite)

## Verification Commands

```bash
# Run fast tests (H2 only)
./gradlew testFast

# Run checkstyle
./gradlew checkstyleMain checkstyleTest

# Full build with all tests
./gradlew clean build
```
