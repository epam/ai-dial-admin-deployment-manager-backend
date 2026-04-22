# API Contract: Node Pools

## GET /api/v1/node-pools

List all configured node pools. Returns configuration data only — no Kubernetes API calls.

**Authentication**: Same as deployment endpoints (OIDC/basic per security mode).

### Response 200

```json
[
  {
    "name": "gpu-a100-prod",
    "description": "LLM inference & fine-tuning",
    "instance": "a2-ultragpu-4g",
    "maxNodes": 10,
    "gpu": {
      "name": "NVIDIA A100",
      "vramBytes": 85899345920,
      "count": 4
    },
    "cpu": {
      "name": "AMD EPYC Milan",
      "milliCpus": 48000
    },
    "memory": {
      "bytes": 730144440320
    }
  },
  {
    "name": "cpu-highmem",
    "description": "Data preprocessing",
    "instance": null,
    "maxNodes": 5,
    "gpu": null,
    "cpu": {
      "name": null,
      "milliCpus": 64000
    },
    "memory": {
      "bytes": 549755813888
    }
  }
]
```

Returns empty array `[]` when no node pools are configured.

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
