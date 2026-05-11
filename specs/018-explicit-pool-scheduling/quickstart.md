# Quickstart — Explicit Node Pool Scheduling

Phase 1 output. End-to-end smoke test you can run against a local checkout to verify the feature behaves as specified. Targets a developer or operator wanting to sanity-check the implementation before merging or shipping.

## 1. Set up `NODE_POOLS` configuration

Pick **either** the env-var form **or** the `application.yml` form. (FR-022 — both are supported; this quickstart shows env-var because that's the production deployment shape.)

### Env var form

```bash
export NODE_POOLS=$(cat <<'YAML'
- name: cpu_node_pool
  description: General-purpose CPU pool
  nodeSelector:
    workload: cpu
- name: gpu_node_pool
  description: GPU pool for inference & fine-tuning
  affinity:
    nodeAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
        nodeSelectorTerms:
        - matchExpressions:
          - key: accelerator-type
            operator: In
            values:
            - nvidia-a100
            - nvidia-h100
  tolerations:
  - key: dedicated
    operator: Equal
    value: gpu
    effect: NoSchedule
- name: unconstrained
  description: No scheduling constraints — operator escape hatch
YAML
)

export NODE_POOL_DEFAULT=cpu_node_pool
export NODE_POOL_DEFAULT_MODEL=gpu_node_pool
```

### `application.yml` form (equivalent)

```yaml
app:
  node-pools:
    default: cpu_node_pool
    default-model: gpu_node_pool
    pools:
      - name: cpu_node_pool
        description: General-purpose CPU pool
        nodeSelector:
          workload: cpu
      - name: gpu_node_pool
        description: GPU pool for inference & fine-tuning
        affinity:
          nodeAffinity:
            requiredDuringSchedulingIgnoredDuringExecution:
              nodeSelectorTerms:
              - matchExpressions:
                - key: accelerator-type
                  operator: In
                  values: [nvidia-a100, nvidia-h100]
        tolerations:
        - key: dedicated
          operator: Equal
          value: gpu
          effect: NoSchedule
      - name: unconstrained
        description: No scheduling constraints — operator escape hatch
```

## 2. Start the application

```bash
./gradlew bootRun
```

In the logs you should see:

```text
[NodePoolConfiguration] Loaded 3 node pool configurations
[NodePoolConfiguration] Validated defaults: NODE_POOL_DEFAULT=cpu_node_pool, NODE_POOL_DEFAULT_MODEL=gpu_node_pool
```

### Smoke-test the startup-validation failure modes

Each of the following should cause the application to refuse to start with a clear error:

| Cause | Expected error fragment |
|---|---|
| `maxNodes: 10` on a pool entry | `field 'maxNodes' is removed` |
| `NODE_POOL_LABEL_KEY=node-pool` set | `NODE_POOL_LABEL_KEY is no longer supported` |
| Two pools with identical `name` | `Duplicate node pool name` |
| `NODE_POOL_DEFAULT_MODEL=ghost` (not in `NODE_POOLS`) | `NODE_POOL_DEFAULT_MODEL references node pool 'ghost'` |
| `affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions[0].operator: NotARealOperator` | `invalid Affinity field` (or Jackson schema error naming the path) |

## 3. Verify the listing API

```bash
curl -s http://localhost:8080/api/v1/node-pools | jq .
```

Expected response (matches the contract in `contracts/node-pools-api.md` §1):

```json
{
  "pools": [
    {
      "name": "cpu_node_pool",
      "description": "General-purpose CPU pool",
      "nodeSelector": { "workload": "cpu" }
    },
    {
      "name": "gpu_node_pool",
      "description": "GPU pool for inference & fine-tuning",
      "affinity": { "nodeAffinity": { "...": "..." } },
      "tolerations": [ { "key": "dedicated", "operator": "Equal", "value": "gpu", "effect": "NoSchedule" } ]
    },
    {
      "name": "unconstrained",
      "description": "No scheduling constraints — operator escape hatch"
    }
  ],
  "defaults": {
    "default": "cpu_node_pool",
    "model": "gpu_node_pool"
  }
}
```

Note that `pools[0]` carries only `nodeSelector` (no `affinity` or `tolerations` keys), `pools[2]` carries neither nodeSelector nor affinity nor tolerations — fields are **omitted**, not returned as `null` or `{}`.

## 4. Verify the create-time cascade

### MCP deployment, no explicit pool → catch-all stamped

```bash
curl -s -X POST http://localhost:8080/api/v1/deployments \
  -H 'Content-Type: application/json' \
  -d '{"type":"mcp","name":"hello-mcp", /* ... other required fields ... */ }'
```

Expected: response carries `"nodePool": "cpu_node_pool"` (the catch-all). Re-fetch the deployment by id; same value.

### NIM deployment, no explicit pool → model override stamped

```bash
curl -s -X POST http://localhost:8080/api/v1/deployments \
  -H 'Content-Type: application/json' \
  -d '{"type":"nim","name":"hello-nim", /* ... other required fields ... */ }'
```

Expected: response carries `"nodePool": "gpu_node_pool"`.

### MCP deployment, explicit null → stored as null

```bash
curl -s -X POST http://localhost:8080/api/v1/deployments \
  -H 'Content-Type: application/json' \
  -d '{"type":"mcp","name":"hello-mcp-any","nodePool": null, /* ... */ }'
```

Expected: response carries `"nodePool": null` ("Any"). The cascade did not run; the explicit null was honoured.

### Create with unknown pool name → 400

```bash
curl -s -i -X POST http://localhost:8080/api/v1/deployments \
  -H 'Content-Type: application/json' \
  -d '{"type":"mcp","name":"hello-fail","nodePool":"ghost_pool", /* ... */ }'
```

Expected: HTTP 400 with `ErrorView` body whose `message` names `'ghost_pool'`.

## 5. Verify the deploy-time projection

Take the NIM deployment created in §4 (`nodePool: gpu_node_pool`), trigger a deploy (per the existing deployment workflow), and inspect the resulting `NIMService` custom resource:

```bash
kubectl get nimservice hello-nim -o yaml | yq '.spec.template'
```

Expected: the pod template carries `affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions[0]` with `accelerator-type In [nvidia-a100, nvidia-h100]`, and `tolerations` includes the `dedicated=gpu:NoSchedule` entry. The `nodeSelector` is empty / absent on the pod template (pool didn't declare one).

Now take the MCP deployment with `nodePool: cpu_node_pool` and deploy it. Inspect the resulting Knative Service:

```bash
kubectl get ksvc hello-mcp -o yaml | yq '.spec.template.spec'
```

Expected: `nodeSelector: { workload: cpu }`; no affinity; no tolerations beyond the deployment-type defaults.

Take the unpooled deployment (`nodePool: null`) and deploy it:

```bash
kubectl get ksvc hello-mcp-any -o yaml | yq '.spec.template.spec'
```

Expected: no pool-derived nodeSelector / affinity / tolerations — only whatever the deployment type's defaults supply.

## 6. Verify admin changes do not retroactively migrate

```bash
# Reconfigure: swap the model default
export NODE_POOL_DEFAULT_MODEL=cpu_node_pool

# Restart the app
```

Re-fetch the NIM deployment from §4:

```bash
curl -s http://localhost:8080/api/v1/deployments/<nim-id> | jq '.nodePool'
```

Expected: still `"gpu_node_pool"`. The admin change did NOT migrate the existing deployment (FR-019).

Now create a fresh NIM deployment without a pool:

```bash
curl -s -X POST http://localhost:8080/api/v1/deployments \
  -H 'Content-Type: application/json' \
  -d '{"type":"nim","name":"hello-nim-2", /* ... */ }' | jq '.nodePool'
```

Expected: `"cpu_node_pool"` (the new default).

## 7. Verify duplicate copies verbatim

```bash
curl -s -X POST http://localhost:8080/api/v1/deployments/<original-id>/duplicate \
  -d '{"name":"hello-mcp-copy"}' | jq '.nodePool'
```

Expected: same value as the source's `nodePool` (no cascade re-run).

If the source's pool no longer exists (because the operator removed it from `NODE_POOLS`), the duplicate fails with 400 — the user supplies an override:

```bash
curl -s -X POST http://localhost:8080/api/v1/deployments/<dangling-source-id>/duplicate \
  -d '{"name":"hello-mcp-copy","nodePool":"unconstrained"}' | jq '.nodePool'
```

Expected: `"unconstrained"`.

## 8. Verify export strips `nodePool`

```bash
curl -s http://localhost:8080/api/v1/deployments/<id>/export | jq 'has("nodePool")'
```

Expected: `false`. The `nodePool` field is not in the exported payload at all (not `null`, not present).

Re-importing the exported file into the same environment:

```bash
curl -s -X POST http://localhost:8080/api/v1/deployments/import \
  -H 'Content-Type: application/json' \
  -d "$(< exported-deployment.json)" | jq '.nodePool'
```

Expected: the imported deployment's `nodePool` reflects the **target environment's current defaults** (cascade ran on import) — possibly different from the source's original value.

## 9. Verify the "feature off" mode (`NODE_POOLS` empty)

Restart with `NODE_POOLS` unset:

```bash
unset NODE_POOLS NODE_POOL_DEFAULT NODE_POOL_DEFAULT_MODEL
./gradlew bootRun
```

```bash
curl -s http://localhost:8080/api/v1/node-pools | jq .
```

Expected: `{ "pools": [] }` (or `{}` after `NON_EMPTY` filtering — both are acceptable). Defaults block absent.

Create a deployment without a pool:

```bash
curl -s -X POST http://localhost:8080/api/v1/deployments \
  -d '{"type":"mcp","name":"hello-no-pools", /* ... */ }' | jq '.nodePool'
```

Expected: `null`. No primitives applied at deploy time.
