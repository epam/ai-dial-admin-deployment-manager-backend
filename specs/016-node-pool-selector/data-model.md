# Data Model: Node Pool Selector

## Configuration Model (not persisted in DB)

Configured via two environment variables, parsed from JSON at startup by `NodePoolConfiguration`.

### Cluster-wide settings

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `app.node-pool-label-key` | `NODE_POOL_LABEL_KEY` | `node-pool` | Kubernetes node label key used to identify pools. Label selector is derived as `{labelKey: pool.name}`. Must be non-blank whenever `NODE_POOLS` is non-empty; may be blank when no pools are configured |

### NodePoolConfig (JSON array via `NODE_POOLS`)

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| name | String | Yes | non-blank, unique | Pool identifier. Also used as the label value for node selection |
| description | String | No | - | Human-readable description |
| instance | String | No | - | Cloud instance type (e.g., `a2-ultragpu-4g`) |
| minNodes | int | No | >= 0 | Minimum number of nodes in this pool (default 0) |
| maxNodes | int | Yes | > 0, >= minNodes | Maximum number of nodes in this pool |
| gpu | GpuSpec | No | - | GPU spec per node. Null for CPU-only pools |
| gpu.name | String | Yes (if gpu set) | non-blank | GPU model name |
| gpu.vramBytes | long | Yes (if gpu set) | > 0 | VRAM capacity per GPU in bytes |
| gpu.count | int | Yes (if gpu set) | > 0 | Number of GPUs per node |
| cpu | CpuSpec | Yes | - | CPU spec per node |
| cpu.name | String | No | - | Processor name |
| cpu.milliCpus | long | Yes | > 0 | CPU capacity in millicores |
| memory | MemorySpec | Yes | - | Memory spec per node |
| memory.bytes | long | Yes | > 0 | Memory capacity in bytes |

**Example**:
```bash
NODE_POOL_LABEL_KEY=node-pool
NODE_POOLS='[{"name":"gpu-a100-prod","description":"LLM inference","instance":"a2-ultragpu-4g","maxNodes":10,"gpu":{"name":"NVIDIA A100","vramBytes":85899345920,"count":4},"cpu":{"name":"AMD EPYC Milan","milliCpus":48000},"memory":{"bytes":730144440320}}]'
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

Top-level response object for `GET /api/v1/node-pools`. Returns configuration data only — no K8s API calls.

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| name | String | No | Pool name |
| description | String | Yes | Human-readable description |
| instance | String | Yes | Cloud instance type |
| minNodes | int | No | Min nodes in pool (default 0) |
| maxNodes | int | No | Max nodes in pool |
| gpu | GpuSpecDto | Yes | GPU spec per node (null for CPU-only) |
| cpu | CpuSpecDto | No | CPU spec per node |
| memory | MemorySpecDto | No | Memory spec per node |

### GpuSpecDto

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| name | String | No | GPU model name |
| vramBytes | long | No | VRAM per GPU in bytes |
| count | int | No | GPUs per node |

### CpuSpecDto

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| name | String | Yes | Processor name |
| milliCpus | long | No | CPU capacity in millicores |

### MemorySpecDto

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| bytes | long | No | Memory capacity in bytes |

## Entity Relationship

```
NodePoolProperties (config, from NODE_POOLS JSON + NODE_POOL_LABEL_KEY)
  ├── nodePoolLabelKey ─── cluster-wide K8s label key
  └── List<NodePoolConfig>
        ├── name ──────────── referenced by ──── DeploymentEntity.nodePool
        ├── gpu (name, vramBytes, count) — nullable
        ├── cpu (name, milliCpus)
        ├── memory (bytes)
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
