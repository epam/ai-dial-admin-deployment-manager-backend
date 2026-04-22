# Data Model: Node Pool Selector

## Configuration Model (not persisted in DB)

Configured via two environment variables, parsed from JSON at startup by `NodePoolConfiguration`.

### Cluster-wide settings

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `app.node-pool-label-key` | `NODE_POOL_LABEL_KEY` | `node-pool` | Kubernetes node label key used to identify pools. Label selector is derived as `{labelKey: pool.name}` |

### NodePoolConfig (JSON array via `NODE_POOLS`)

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| name | String | Yes | non-blank, unique | Pool identifier. Also used as the label value for node selection |
| description | String | No | - | Human-readable description |
| maxNodes | int | Yes | > 0 | Maximum number of nodes in this pool |
| cpuMillis | long | Yes | > 0 | CPU capacity per node in millicores |
| memoryBytes | long | Yes | > 0 | Memory capacity per node in bytes |
| gpu | int | Yes | >= 0 | GPU count per node (0 for CPU-only pools) |

**Example**:
```bash
NODE_POOL_LABEL_KEY=node-pool
NODE_POOLS='[{"name":"gpu-a100-pool","description":"NVIDIA A100 80 GB SXM","maxNodes":8,"cpuMillis":96000,"memoryBytes":687194767360,"gpu":3},{"name":"cpu-highmem-pool","description":"CPU only","maxNodes":5,"cpuMillis":64000,"memoryBytes":549755813888,"gpu":0}]'
```

## Database Changes

### Migration V1.58 — AddNodePoolColumn

Add nullable VARCHAR column to the `deployment` table (base table in JOINED inheritance).

```sql
-- H2 / POSTGRES
ALTER TABLE deployment ADD COLUMN node_pool VARCHAR(255);

-- MS_SQL_SERVER
ALTER TABLE deployment ADD node_pool NVARCHAR(255);
```

### DeploymentEntity (modified)

| New Field | Column | Type | Nullable | Description |
|-----------|--------|------|----------|-------------|
| nodePool | node_pool | VARCHAR(255) | Yes | Name of the selected node pool (references config, not FK) |

## Domain Model Changes

### Deployment (modified)

| New Field | Type | Nullable | Description |
|-----------|------|----------|-------------|
| nodePool | String | Yes | Selected node pool name |

### CreateDeployment (modified)

| New Field | Type | Nullable | Description |
|-----------|------|----------|-------------|
| nodePool | String | Yes | Node pool to assign |

## DTO Changes

### DeploymentDto (modified)

| New Field | Type | Nullable | Description |
|-----------|------|----------|-------------|
| nodePool | String | @Nullable | Selected node pool name (returned on get/list) |

### CreateDeploymentRequestDto (modified)

| New Field | Type | Nullable | Description |
|-----------|------|----------|-------------|
| nodePool | String | @Nullable | Node pool to assign on create/update |

## Response DTOs (new)

### NodePoolDto

Top-level response object for `GET /api/v1/node-pools`.

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| name | String | No | Pool name (from config) |
| description | String | Yes | Human-readable description (from config) |
| maxNodes | int | No | Max nodes in pool (from config) |
| runningNodes | int | No | Count of currently running nodes (from K8s) |
| nodeSpec | NodeSpecDto | No | Per-node resource spec (from config) |
| nodes | List<NodeUtilizationDto> | No | Per-node utilization data (from K8s); empty list if no running nodes |

### NodeSpecDto

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| cpuMillis | long | No | CPU per node in millicores |
| memoryBytes | long | No | Memory per node in bytes |
| gpu | int | No | GPU count per node |

### NodeUtilizationDto

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| nodeName | String | No | Kubernetes node name |
| allocatableCpuMillis | long | No | Allocatable CPU in millicores (from node status) |
| allocatableMemoryBytes | long | No | Allocatable memory in bytes (from node status) |
| allocatableGpu | int | No | Allocatable GPU count (from node status) |
| requestedCpuMillis | long | No | Sum of pod CPU requests in millicores |
| requestedMemoryBytes | long | No | Sum of pod memory requests in bytes |
| requestedGpu | int | No | Sum of pod GPU requests |

## Entity Relationship

```
NodePoolProperties (config, from NODE_POOLS JSON + NODE_POOL_LABEL_KEY)
  ├── nodePoolLabelKey ─── cluster-wide K8s label key
  └── List<NodePoolConfig>
        ├── name ──────────── referenced by ──── DeploymentEntity.nodePool
        ├── cpuMillis, memoryBytes, gpu (flat)
        └── label selector = {nodePoolLabelKey: name} (derived, not stored)

DeploymentEntity (DB, JOINED inheritance)
  └── nodePool (VARCHAR, nullable) ── validated against NodePoolProperties on create/update/deploy
```

## State Transitions

No new state transitions. The `nodePool` field is a configuration attribute that does not affect the deployment status lifecycle. It influences K8s manifest generation at deploy time only.

## Mapper Impact

All mappers auto-map `nodePool` by field name convention — no custom mapping logic needed:
- `PersistenceDeploymentMapper`: entity ↔ domain (nodePool String ↔ nodePool String)
- `DeploymentMapper`: CreateDeployment → Deployment (nodePool carried through)
- `DeploymentDtoMapper`: DTO ↔ domain (nodePool String ↔ nodePool String)
