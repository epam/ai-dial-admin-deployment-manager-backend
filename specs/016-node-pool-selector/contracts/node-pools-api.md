# API Contract: Node Pools

## GET /api/v1/node-pools

List all configured node pools with live Kubernetes node utilization.

**Authentication**: Same as deployment endpoints (OIDC/basic per security mode).

### Response 200

```json
[
  {
    "name": "gpu-a100-pool",
    "description": "NVIDIA A100 80 GB SXM — large model training and inference",
    "maxNodes": 8,
    "runningNodes": 3,
    "nodeSpec": {
      "cpuMillis": 96000,
      "memoryBytes": 687194767360,
      "gpu": 3
    },
    "nodes": [
      {
        "nodeName": "gke-gpu-a100-pool-abc123",
        "allocatableCpuMillis": 95500,
        "allocatableMemoryBytes": 684500000000,
        "allocatableGpu": 3,
        "requestedCpuMillis": 57300,
        "requestedMemoryBytes": 410700000000,
        "requestedGpu": 2
      },
      {
        "nodeName": "gke-gpu-a100-pool-def456",
        "allocatableCpuMillis": 95500,
        "allocatableMemoryBytes": 684500000000,
        "allocatableGpu": 3,
        "requestedCpuMillis": 57300,
        "requestedMemoryBytes": 273800000000,
        "requestedGpu": 2
      },
      {
        "nodeName": "gke-gpu-a100-pool-ghi789",
        "allocatableCpuMillis": 95500,
        "allocatableMemoryBytes": 684500000000,
        "allocatableGpu": 3,
        "requestedCpuMillis": 57400,
        "requestedMemoryBytes": 0,
        "requestedGpu": 0
      }
    ]
  },
  {
    "name": "cpu-highmem-pool",
    "description": "CPU only — data preprocessing, tokenization, CPU-bound workloads",
    "maxNodes": 5,
    "runningNodes": 0,
    "nodeSpec": {
      "cpuMillis": 64000,
      "memoryBytes": 549755813888,
      "gpu": 0
    },
    "nodes": []
  }
]
```

### Response 500 (K8s API unreachable)

```json
{
  "path": "/api/v1/node-pools",
  "method": "GET",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Failed to retrieve node utilization from Kubernetes",
  "traceparent": "00-abc123..."
}
```

## Deployment API Changes

### POST /api/v1/deployments (create) — updated request body

New optional field `nodePool` added alongside existing fields:

```json
{
  "name": "my-deployment",
  "displayName": "My Deployment",
  "type": "MCP",
  "source": { "$type": "internal_image", "imageDefinitionId": "..." },
  "metadata": { "envs": [] },
  "nodePool": "gpu-a100-pool"
}
```

### PUT /api/v1/deployments/{id} (update) — updated request body

```json
{
  "displayName": "My Deployment",
  "source": { "$type": "internal_image", "imageDefinitionId": "..." },
  "metadata": { "envs": [] },
  "nodePool": "gpu-t4-pool"
}
```

Setting `"nodePool": null` or omitting the field clears the selection.

### GET /api/v1/deployments/{id} — updated response

New field `nodePool` included when set:

```json
{
  "name": "my-deployment",
  "displayName": "My Deployment",
  "status": "RUNNING",
  "nodePool": "gpu-a100-pool",
  "...": "existing fields"
}
```

When no pool is selected, `nodePool` is `null`.

### Validation Errors

**Invalid node pool on create/update** (400):

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Node pool 'nonexistent-pool' is not configured",
  "traceparent": "00-..."
}
```

**Stale node pool on deploy** (400):

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Node pool 'removed-pool' referenced by deployment 'my-deployment' is no longer configured",
  "traceparent": "00-..."
}
```
