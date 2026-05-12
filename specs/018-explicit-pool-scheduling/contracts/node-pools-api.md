# API Contract — Node Pools & Deployment `nodePoolId` Field

Phase 1 output. Pins the wire-format shape for `GET /api/v1/node-pools` (FR-011, FR-017) and the deployment create / update / duplicate / export behaviour for the `nodePoolId` field (FR-013, FR-014, FR-018, FR-019, FR-020, FR-021).

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
      "id": "gpu-pool",
      "name": "GPU pool",
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
      "id": "cpu-pool",
      "name": "CPU pool",
      "description": "CPU workloads"
    }
  ]
}
```

**Response field semantics**:

- `pools[]` — **always present**, serialised as `[]` when `NODE_POOLS` is empty so FE consumers can rely on its presence without null-checking.
- `pools[].id` — required, non-nullable (`requiredMode: REQUIRED`, `nullable: false`).
- `pools[].name` — required, non-nullable (`requiredMode: REQUIRED`, `nullable: false`).
- `pools[].description` — omitted when not declared.
- `pools[].nodeSelector` — omitted when not declared (no `null`, no `{}`).
- `pools[].affinity` — omitted when not declared.
- `pools[].tolerations` — omitted when not declared.

**Defaults are not surfaced via this endpoint.** The values of `NODE_POOL_DEFAULT` and `NODE_POOL_DEFAULT_MODEL` are admin-only configuration and are not part of any public read response. A FE that needs to pre-fill a deployment-creation picker does not read the defaults off this endpoint — it relies on the create response, which carries the cascade-resolved `nodePoolId` after the user submits with `nodePoolId: null`.

**Removed from Feature 016 response shape**: `instance`, `minNodes`, `maxNodes`, `gpu`, `cpu`, `memory` — none of these fields appear on a pool entry. Capacity numbers are now sourced from `?includeUtilization=true` (Feature 020).

**Error — 500 Internal Server Error**: any unexpected exception. Schema per Constitution API conventions (`ErrorView`).

## 2. Deployment create — `POST /api/v1/deployments`

**Request body**: existing deployment-create payload extended with an optional `nodePoolId` field.

```jsonc
{
  // ... existing fields ...
  "nodePoolId": "gpu-pool"   // OR null (= triggers cascade), OR field omitted entirely
}
```

**Semantics**:

| Input form | Behaviour |
|---|---|
| Field omitted | Run the create-time cascade (FR-018): `NODE_POOL_DEFAULT_MODEL` if model + set → `NODE_POOL_DEFAULT` if set → null. Stamped value persisted onto the deployment record. |
| `"nodePoolId": null` | Same as omitted — null payload value triggers the cascade. Selecting "Any" at create time is not directly expressible; users create first, then update `nodePoolId` to null (see § 3). |
| `"nodePoolId": "<id>"` | Skip cascade. Validate against `NODE_POOLS` (must match a pool's `id`, not its `name`). Store verbatim. |

**Validation errors**:

| Cause | Status | Body (`ErrorView`) |
|---|---|---|
| `nodePoolId` is a non-null id not in `NODE_POOLS` | 400 | `message: "Unknown node pool id: '<id>'. Configured pool ids: [<list>]."` |
| Any other field validation (existing) | per existing convention | existing body |

**Success — 201 Created**: response is the persisted deployment with `nodePoolId` reflecting the **stored** value (the cascade-resolved id, the explicit id, or null). The caller sees the stamped result without a second fetch. The response also carries `nodePoolName` resolved at read time from the current `NODE_POOLS`.

## 3. Deployment update — `PUT /api/v1/deployments/{id}`

**Request body**: existing update payload, with `nodePoolId` semantics as below (PUT-style — the payload value is authoritative).

| Input form | Behaviour |
|---|---|
| Field omitted | Stored value cleared to null (absent == null on update). Cascade does **not** run. |
| `"nodePoolId": null` | Store null verbatim. Cascade does **not** run. |
| `"nodePoolId": "<id>"` | Validate against `NODE_POOLS`. Store verbatim. |

**Caller contract**: clients PATCHing unrelated fields MUST include the current `nodePoolId` value in the payload — omitting it is equivalent to sending null and will clear the stored pool.

**Validation error**: same as create — unknown id → 400.

**Success — 200 OK**: response carries the updated deployment with `nodePoolId` reflecting the (now updated or cleared) stored value, plus the resolved `nodePoolName`.

## 4. Deployment retrieval — `GET /api/v1/deployments/{id}` and list

Response carries the stored `nodePoolId` value verbatim (id string or null) **and** a resolved `nodePoolName` (the current display label, or null when `nodePoolId` is null or when the id is dangling — pool removed from `NODE_POOLS`). No derived `nodePoolStatus`, no `effectiveNodePool`, no validation against current `NODE_POOLS`. Dangling-reference detection is the FE's responsibility (Assumption "Backend stays dumb about dangling references"); the FE compares the deployment's `nodePoolId` against the list returned by `GET /api/v1/node-pools` — or simply notices `nodePoolName: null` while `nodePoolId` is non-null.

## 5. Deployment duplicate — `POST /api/v1/deployments/duplicate`

**Behaviour** (FR-020):

- The duplicate's `nodePoolId` is copied verbatim from the source.
- The create-time cascade does **not** run for duplicates.
- The duplicated record is still subject to the same validation as direct create:
  - If source's `nodePoolId` is null → duplicate is created with `nodePoolId` null. Always valid.
  - If source's `nodePoolId` is an id still in current `NODE_POOLS` → duplicate created with that id.
  - If source's `nodePoolId` is an id no longer in current `NODE_POOLS` → 400 with same error body as create. User must first update the source deployment to a current pool (or to null), then re-issue the duplicate.

## 6. Deployment export — `GET /api/v1/deployments/export` (or equivalent)

**Behaviour** (FR-021): the export payload does NOT include the `nodePoolId` field for any deployment. The field is omitted from the export DTO entirely — it is not serialized as `null`, not serialized as the stored value, not included with a "stripped" marker.

## 7. Deployment import — `POST /api/v1/deployments/import` (or equivalent)

**Behaviour** (FR-021):

- The import flow consumes the import DTO; the DTO does NOT declare `nodePoolId`. Legacy exports that carry `nodePool` or `nodePoolId` are silently dropped by Jackson on the import side (the import-side `ObjectMapper` uses default `FAIL_ON_UNKNOWN_PROPERTIES = false`).
- Each imported deployment goes through the target environment's normal `create(...)` flow with `nodePoolId` absent → cascade runs per FR-018 → record is stamped with the target environment's appropriate default (or null).
- No cross-environment pool validation; no partial-success rejection; import succeeds regardless of source/target pool naming differences.

## 8. Startup-time validation errors

Configuration errors fail application startup (FR-003, FR-005, FR-015). Sample error messages the implementation MUST emit (exact wording may differ but MUST identify the offending input):

| Cause | Sample message |
|---|---|
| Deprecated field `maxNodes` (or `cpu`, `memory`, `gpu`) on a pool entry | `Node pool 'gpu_pool': field 'maxNodes' is removed in this version of the deployment-manager. See docs/configuration.md for the new schema.` |
| Duplicate pool id | `Duplicate node pool id: 'gpu-pool'` |
| Duplicate pool name | `Duplicate node pool name: 'GPU pool'` |
| Pool's `affinity` or `tolerations` violates Kubernetes schema | `Node pool 'gpu-pool': invalid Affinity field 'foo' at path .nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.foo` |
| `NODE_POOL_DEFAULT_MODEL` references a missing pool id | `NODE_POOL_DEFAULT_MODEL references node pool id 'ghost-pool' which is not present in NODE_POOLS.` |
| `NODE_POOL_DEFAULT` references a missing pool id | `NODE_POOL_DEFAULT references node pool id 'ghost-pool' which is not present in NODE_POOLS.` |

A stray `NODE_POOL_LABEL_KEY` env var is **not** rejected — see FR-004. Nothing in the application reads it after this feature ships.

## 9. OpenAPI surface obligations

- `GET /api/v1/node-pools` keeps `@Operation` summary; `@ApiResponse(200, NodePoolListResponseDto.class)` with `pools` always present.
- `NodePoolDto.id` and `NodePoolDto.name` are documented as `requiredMode = REQUIRED`, `nullable = false`.
- Create / update endpoints declare the `nodePoolId` field with `@Schema` documentation describing the omit / null / value semantics.
- Removed schemas: `DefaultsDto`, `GpuSpecDto`, `CpuSpecDto`, `MemorySpecDto` deleted from the OpenAPI surface.
