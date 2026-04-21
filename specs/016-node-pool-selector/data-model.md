# Data Model: Node Pool Selector

## Configuration Model (not persisted in DB)

### NodePoolConfig

Bound from `application.yml` via `@ConfigurationProperties`.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| name | String | Yes | Unique pool identifier, used as the selection key |
| description | String | No | Human-readable description (e.g., "NVIDIA A100 80 GB SXM — large model training and inference") |
| maxNodes | int | Yes | Maximum number of nodes that can run in this pool |
| labelSelector | Map<String, String> | Yes | Kubernetes node labels used to query nodes belonging to this pool |
| nodeSpec | NodeSpec | Yes | Per-node resource specification |

### NodeSpec (nested in NodePoolConfig)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| cpuMillis | long | Yes | CPU capacity per node in millicores |
| memoryBytes | long | Yes | Memory capacity per node in bytes |
| gpu | int | No | GPU count per node (default: 0) |

**Example YAML**:
```yaml
app:
  node-pools:
    - name: gpu-a100-pool
      description: "NVIDIA A100 80 GB SXM — large model training and inference"
      max-nodes: 8
      label-selector:
        node-pool: gpu-a100
      node-spec:
        cpu-millis: 96000
        memory-bytes: 687194767360  # 640 GiB
        gpu: 3
    - name: gpu-t4-pool
      description: "NVIDIA T4 16 GB — cost-efficient inference and light fine-tuning"
      max-nodes: 12
      label-selector:
        node-pool: gpu-t4
      node-spec:
        cpu-millis: 48000
        memory-bytes: 206158430208  # 192 GiB
        gpu: 4
    - name: cpu-highmem-pool
      description: "CPU only — data preprocessing, tokenization, CPU-bound workloads"
      max-nodes: 5
      label-selector:
        node-pool: cpu-highmem
      node-spec:
        cpu-millis: 64000
        memory-bytes: 549755813888  # 512 GiB
        gpu: 0
```

## Database Changes

### Migration V1.57 — AddNodePoolColumn

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
NodePoolProperties (config)
  └── List<NodePoolConfig>
        ├── name ──────────── referenced by ──── DeploymentEntity.nodePool
        ├── labelSelector ─── used at deploy time for affinity injection
        └── nodeSpec

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
