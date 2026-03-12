# Data Model: Store Deployment Service Name

## Entity Changes

### DeploymentEntity (modified)

**Table**: `deployment`

| Column | Type | Nullable | Constraint | Notes |
|--------|------|----------|------------|-------|
| `service_name` | VARCHAR(255) | YES | UNIQUE (allows NULLs) | New column. Stores the K8s service name. NULL for NOT_DEPLOYED/STOPPED deployments that have never been deployed. |

**JPA Field**:
```java
@Column(name = "service_name", length = 255, unique = true)
private String serviceName;
```

**Domain Field** (Deployment base class):
```java
private String serviceName;
```

### State Transitions for service_name

```
createDeployment()     → serviceName = null (NOT_DEPLOYED)
deploy()               → serviceName = generateName(id) [if null] or reuse existing
undeploy()             → serviceName preserved (immutable)
redeploy after undeploy → serviceName reused from previous deploy
migration (active)     → serviceName = derived from current prefix + type convention
migration (inactive)   → serviceName = null (NOT_DEPLOYED/STOPPED)
```

## Migration: V1.52 + V1.53

### V1.52 — DDL: Add service_name column (SQL)

**H2**:
```sql
ALTER TABLE deployment ADD COLUMN service_name VARCHAR(255);
CREATE UNIQUE INDEX idx_deployment_service_name ON deployment(service_name);
```

**PostgreSQL**:
```sql
ALTER TABLE deployment ADD COLUMN service_name VARCHAR(255);
CREATE UNIQUE INDEX idx_deployment_service_name ON deployment(service_name);
```

**SQL Server**:
```sql
ALTER TABLE deployment ADD service_name VARCHAR(255);
CREATE UNIQUE NONCLUSTERED INDEX idx_deployment_service_name
    ON deployment(service_name)
    WHERE service_name IS NOT NULL;
```

Note: SQL Server requires a filtered index to allow multiple NULLs in a unique index.

### V1.53 — Data: Backfill service_name (Java)

**Base class**: `V1_53__BackfillServiceNameBase extends BaseJavaMigration`

**Logic**:
```
1. Read RESOURCE_NAME_PREFIX from System.getenv()
2. Query all deployments WHERE status NOT IN ('NOT_DEPLOYED', 'STOPPED') AND service_name IS NULL
3. For each deployment:
   a. Determine type by checking subtable existence:
      - EXISTS in mcp_deployment → prefix = mcpPrefix
      - EXISTS in adapter_deployment → prefix = mcpPrefix
      - EXISTS in interceptor_deployment → prefix = mcpPrefix
      - EXISTS in nim_deployment → prefix = mcpPrefix
      - EXISTS in inference_deployment → prefix = dmPrefix
   b. mcpPrefix = customPrefix if set, else "mcp"
   c. dmPrefix = customPrefix if set, else "dm"
   d. serviceName = prefix + "-" + deploymentId
4. Batch UPDATE deployment SET service_name = ? WHERE id = ?
```

## New Repository Queries

### DeploymentJpaRepository

```java
Optional<DeploymentEntity> findByServiceName(String serviceName);
```

### DeploymentRepository

```java
Optional<Deployment> getByServiceName(String serviceName);
```

## Affected Mappers

### PersistenceDeploymentMapper
- `toDomain`: Map `serviceName` field (automatic by MapStruct if field names match)
- `toEntity`: Map `serviceName` field
- `updateEntityFromDomain`: Include `serviceName` in field updates

### DeploymentMapper
- `toDeployment(CreateDeployment)`: `serviceName` defaults to null (not set from request)

### DeploymentDtoMapper
- No changes needed — service name is an internal concern, not exposed via API
