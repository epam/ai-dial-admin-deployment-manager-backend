# Contract: Inference Deployment Capability Field

No new endpoint. The capability is exposed as an additional **read-only** field on the existing
inference deployment responses.

## Affected responses

- `GET /api/v1/deployments/{id}` — when the deployment is an inference deployment
- `GET /api/v1/deployments` (and type-filtered list variants) — each inference deployment item

(Exact existing paths per `specs/deployments/spec.md`; this feature does not change routing.)

## Field

| Field | Type | Nullable | Direction | Description |
|---|---|---|---|---|
| `inferenceTask` | enum string | No | response only | One of `TEXT_GENERATION`, `TEXT_CLASSIFICATION`, `NONE`. System-computed. Null persisted value is returned as `NONE`. |

### Example (inference deployment response, fields trimmed)

```json
{
  "id": "my-llama-chat",
  "deploymentType": "INFERENCE",
  "modelFormat": "huggingface",
  "source": { "...": "..." },
  "inferenceTask": "TEXT_GENERATION"
}
```

### Frontend mapping (informative — owned by FE)

| `inferenceTask` | Consumption surface |
|---|---|
| `TEXT_GENERATION` | chat completion endpoint |
| `TEXT_CLASSIFICATION` | MCP toolset |
| `NONE` | neither |

## Request contract (unchanged)

- `CreateInferenceDeploymentRequestDto` does **not** include `inferenceTask`. Any client-supplied value
  is ignored — the field is server-computed only (FR-008).

## Error behaviour (unchanged)

- Errors still serialise as `ErrorView` via `DefaultExceptionHandler`.
- Detection failures (model not found, metadata missing/unusable, HF Hub unreachable) surface at
  **create/update** time exactly as today; they do not affect the read contract.

## OpenAPI

- The field is documented on the inference deployment response schema (SpringDoc picks it up from the
  DTO). Existing `@Operation` / `@ApiResponse` annotations on the endpoints are unchanged.
