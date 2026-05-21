# Contract — Inference Deployment REST API

Documents the API-surface changes for feature `021-inference-task-transformer`.

> **Revision 2 (2026-05-20)**: Detection moved to deploy time only. There are **no** new request fields, **no** new response fields, and **no** new request-level validation. All previously-documented error paths still apply, but they surface at deploy (`POST /api/v1/deployments/{id}/deploy`) rather than at create/update.

## Endpoints affected

| Method | Path | Changed |
|---|---|---|
| `POST` | `/api/v1/deployments` | Unchanged for inference type. No detection runs. |
| `GET` | `/api/v1/deployments/{id}` | Unchanged. No new response fields. |
| `GET` | `/api/v1/deployments` (list) | Unchanged. |
| `PATCH` / `PUT` | `/api/v1/deployments/{id}` | Unchanged. No detection runs at update. |
| `POST` | `/api/v1/deployments/{id}/deploy` | New error paths surface here when detection or the chained-args contract fails. See the error table below. |

Export/import endpoints are unchanged — no per-deployment fields participate in detection.

## Request shape (create / update)

**Unchanged.** No new operator-input fields. `task` and `id2label` are not accepted at any layer.

The existing `command` and `args` fields carry **no** new request-level validation. Operators may submit any predictor arg at create/update time. Conflicting args (e.g. `--return_probabilities`, `--task=<non-sequence_classification>`) are detected and rejected later, at deploy time, when the manifest generator knows whether the model is text-classification.

## Response shape

**Unchanged.** `InferenceDeploymentDto` has no new fields. Clients that need to preview a model's labels can query HuggingFace Hub directly.

## New error paths (at deploy time)

| Trigger | HTTP | Error message (canonical form) |
|---|---|---|
| HF API returns 404 for the requested model | 400 | "HuggingFace model '{modelName}' not found. Verify the model identifier and re-submit." |
| HF API returns 401/403 (private model without token) | 400 | "Access to HuggingFace model '{modelName}' was denied. If this is a private model, configure HUGGINGFACE_API_TOKEN." |
| HF API returns 5xx, times out, DNS/network failure | 502/503 (per `DefaultExceptionHandler` conventions) | "HuggingFace Hub is currently unreachable. The deploy was not started; retry." |
| Model detected as text-classification but `config.json` is missing `id2label` | 400 | "HuggingFace model '{modelName}' is a sequence-classification model but its config.json does not contain a usable id2label. Provide a model whose config.json includes a complete label mapping, or fork the model and add one." |
| `config.json` `id2label` has non-integer keys, sparse keys, empty values, or only `LABEL_n` stubs | 400 | "HuggingFace model '{modelName}' has an unusable id2label (reason: '<specific>'). Required: dense non-negative integer keys with non-stub string values." |
| Detection returns `TEXT_CLASSIFICATION` and `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE` is unset | 500 | "Cannot deploy a text-classification inference deployment: required configuration property 'app.text-classification-transformer-container-config.image' (env INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE) is not set." |
| Operator-supplied `args` contain `--return_probabilities` and detection returns `TEXT_CLASSIFICATION` | 400 | "Inference deployment '{name}' is a text-classification model and cannot use predictor arg '--return_probabilities' — the chained transformer requires raw logits. Remove this arg." |
| Operator-supplied `args` contain `--task=<other>` and detection returns `TEXT_CLASSIFICATION` | 400 | "Inference deployment '{name}' is a text-classification model and cannot override '--task' to '<value>' — the chained transformer requires '--task=sequence_classification'. Remove this arg or set it to the required value." |

All errors flow through `DefaultExceptionHandler` and serialize as the standard `ErrorView` with `traceparent` populated from the active OpenTelemetry span. The error is raised **before** any K8s mutation; no `InferenceService` is created or modified when validation fails.

## OpenAPI annotation updates

On the deploy endpoint (`POST /api/v1/deployments/{id}/deploy`):

- Add `@ApiResponse(responseCode = "400", description = "HuggingFace model not found, unauthorized, has unusable metadata, or supplied predictor arg conflicts with the chained transformer contract.")`
- Add `@ApiResponse(responseCode = "500", description = "Required transformer-image configuration is not set; deploy aborted before cluster mutation.")`
- Add `@ApiResponse(responseCode = "502", description = "HuggingFace Hub unreachable; deploy not started, safe to retry.")` (status code subject to alignment with `DefaultExceptionHandler` conventions).

Create / update / get / list endpoints are unchanged.

## Backward compatibility

- All existing inference deployment requests remain valid byte-for-byte.
- Response shape is unchanged.
- Pre-feature inference deployments are unaffected at the API layer. Their next deploy invokes the new detection code path — non-classification models continue to deploy with a predictor-only manifest, classification models start to chain with a transformer (the intended behavior).
- Operators who previously supplied `--return_probabilities` on a sequence-classification model would have been hit by the same constraint anyway; the deploy will now fail with a clear message instead of producing a chained manifest that silently misbehaves at runtime.
