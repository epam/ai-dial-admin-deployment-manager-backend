# API Contract — Node Pools & Deployment `nodePool` Field

Phase 1 output. Pins the wire-format shape for `GET /api/v1/node-pools` (FR-011, FR-017) and the deployment create / update / duplicate / export behaviour for the `nodePool` field (FR-013, FR-014, FR-018, FR-019, FR-020, FR-021).

## 1. `GET /api/v1/node-pools`

**Auth**: same as deployment endpoints.

**Query parameters**:

| Param | Type | Required | Description |
|---|---|---|---|
| `includeUtilization` | `boolean` | No, default `false` | Owned by Feature 020 — when `true`, every pool entry additionally carries a `utilization` block. This feature does not change Feature 020's contract. |

**Success — 200 OK** (response body when `?includeUtilization` is absent or `false`):

```jsonc
{
  "pools": [
    {
      "name": "gpu_node_pool",
      "description": "General-purpose GPU pool",
      "nodeSelector": {
        "accelerator-type": "nvidia-a100"
      },
      "affinity": {
        "nodeAffinity": {
          "requiredDuringSchedulingIgnoredDuringExecution": {
            "nodeSelectorTerms": [
              {
                "matchExpressions": [
                  { "key": "accelerator-type", "operator": "In", "values": ["nvidia-a100", "nvidia-h100"] }
                ]
              }
            ]
          }
        }
      },
      "tolerations": [
        { "key": "dedicated", "operator": "Equal", "value": "gpu", "effect": "NoSchedule" }
      ]
    },
    {
      "name": "cpu_node_pool",
      "description": "CPU workloads"
    }
  ],
  "defaults": {
    "default": "cpu_node_pool",
    "model": "gpu_node_pool"
  }
}
```

**Response field semantics**:

- `pools[]` — present always; may be empty when `NODE_POOLS` is empty.
- `pools[].name` — required.
- `pools[].description` — omitted when not declared.
- `pools[].nodeSelector` — omitted when not declared (no `null`, no `{}`).
- `pools[].affinity` — omitted when not declared.
- `pools[].tolerations` — omitted when not declared.
- `defaults` — omitted when neither env var is set.
- `defaults.default` — omitted when `NODE_POOL_DEFAULT` is unset.
- `defaults.model` — omitted when `NODE_POOL_DEFAULT_MODEL` is unset.

**Removed from Feature 016 response shape**: `instance`, `minNodes`, `maxNodes`, `gpu`, `cpu`, `memory` — none of these fields appear on a pool entry. Capacity numbers are now sourced from `?includeUtilization=true` (Feature 020).

**Error — 500 Internal Server Error**: any unexpected exception. Schema per Constitution API conventions (`ErrorView`).

## 2. Deployment create — `POST /api/v1/deployments`

**Request body**: existing deployment-create payload extended with an optional `nodePool` field.

```jsonc
{
  // ... existing fields ...
  "nodePool": "gpu_node_pool"   // OR null (= "Any"), OR field omitted entirely
}
```

**Semantics**:

| Input form | Behaviour |
|---|---|
| Field omitted | Run the create-time cascade (FR-018): `NODE_POOL_DEFAULT_MODEL` if model + set → `NODE_POOL_DEFAULT` if set → null. Stamped value persisted onto the deployment record. |
| `"nodePool": null` | Skip cascade. Store null verbatim ("Any"). |
| `"nodePool": "<name>"` | Skip cascade. Validate against `NODE_POOLS`. Store verbatim. |

**Validation errors**:

| Cause | Status | Body (`ErrorView`) |
|---|---|---|
| `nodePool` is a non-null name not in `NODE_POOLS` | 400 | `message: "Unknown node pool: '<name>'. Configured pools: [<list>]."` |
| Any other field validation (existing) | per existing convention | existing body |

**Success — 201 Created**: response is the persisted deployment with `nodePool` reflecting the **stored** value (the cascade-resolved name, the explicit null, or the explicit name — whichever applied). The caller sees the stamped result without a second fetch.

## 3. Deployment update — `PUT /api/v1/deployments/{id}`

**Request body**: existing update payload, with `nodePool` semantics as below.

| Input form | Behaviour |
|---|---|
| Field omitted | Stored value unchanged. Cascade does **not** run. |
| `"nodePool": null` | Store null verbatim. |
| `"nodePool": "<name>"` | Validate against `NODE_POOLS`. Store verbatim. |

**Validation error**: same as create — unknown name → 400.

**Success — 200 OK**: response carries the updated deployment with `nodePool` reflecting the (now updated or unchanged) stored value.

## 4. Deployment retrieval — `GET /api/v1/deployments/{id}` and list

Response carries the stored `nodePool` value verbatim (string name or null). No derived `nodePoolStatus`, no `effectiveNodePool`, no validation against current `NODE_POOLS`. Dangling-reference detection is the FE's responsibility (Assumption "Backend stays dumb about dangling references"); the FE compares the deployment's `nodePool` against the list returned by `GET /api/v1/node-pools`.

## 5. Deployment duplicate — `POST /api/v1/deployments/{id}/duplicate` (or equivalent)

**Behaviour** (FR-020):

- The duplicate's `nodePool` is copied verbatim from the source.
- The create-time cascade does **not** run for duplicates.
- The duplicated record is still subject to the same validation as direct create:
  - If source's `nodePool` is null → duplicate is created with `nodePool` null. Always valid.
  - If source's `nodePool` is a name still in current `NODE_POOLS` → duplicate created with that name.
  - If source's `nodePool` is a name no longer in current `NODE_POOLS` → 400 with same error body as create. User must reissue the duplicate with a `nodePool` override (any current name or explicit null).

## 6. Deployment export — `GET /api/v1/deployments/export` (or equivalent)

**Behaviour** (FR-021): the export payload does NOT include the `nodePool` field for any deployment. The field is omitted from the export DTO entirely — it is not serialized as `null`, not serialized as the stored value, not included with a "stripped" marker.

## 7. Deployment import — `POST /api/v1/deployments/import` (or equivalent)

**Behaviour** (FR-021):

- The import flow consumes the import DTO; the DTO does NOT declare `nodePool`. Legacy exports that carry `nodePool` are silently dropped by Jackson (the import-side `ObjectMapper` uses default behaviour, `FAIL_ON_UNKNOWN_PROPERTIES = false`).
- Each imported deployment goes through the target environment's normal `create(...)` flow with `nodePool` absent → cascade runs per FR-018 → record is stamped with the target environment's appropriate default (or null).
- No cross-environment pool validation; no partial-success rejection; import succeeds regardless of source/target pool naming differences.

## 8. Startup-time validation errors

Configuration errors fail application startup (FR-003, FR-005, FR-015). Sample error messages the implementation MUST emit (exact wording may differ but MUST identify the offending input):

| Cause | Sample message |
|---|---|
| Deprecated field `maxNodes` (or `cpu`, `memory`, `gpu`) on a pool entry | `Node pool 'gpu_pool': field 'maxNodes' is removed in this version of the deployment-manager. See docs/configuration.md for the new schema.` |
| `NODE_POOL_LABEL_KEY` env var set | `NODE_POOL_LABEL_KEY is no longer supported. Pool selection is now expressed via per-pool nodeSelector/affinity/tolerations. See docs/configuration.md.` |
| Duplicate pool name | `Duplicate node pool name: 'gpu_pool'` |
| Pool's `affinity` or `tolerations` violates Kubernetes schema | `Node pool 'gpu_pool': invalid Affinity field 'foo' at path .nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.foo` |
| `NODE_POOL_DEFAULT_MODEL` references a missing pool | `NODE_POOL_DEFAULT_MODEL references node pool 'ghost_pool' which is not present in NODE_POOLS.` |
| `NODE_POOL_DEFAULT` references a missing pool | `NODE_POOL_DEFAULT references node pool 'ghost_pool' which is not present in NODE_POOLS.` |

## 9. OpenAPI surface obligations

- `GET /api/v1/node-pools` keeps `@Operation` summary; updates `@ApiResponse(200, NodePoolListResponseDto.class)`; new DTO classes registered.
- Create / update endpoints declare the new `JsonNullable<String> nodePool` field with `@Schema` documentation describing the omit / null / value semantics.
- Removed schemas: `GpuSpecDto`, `CpuSpecDto`, `MemorySpecDto` deleted from the OpenAPI surface.
