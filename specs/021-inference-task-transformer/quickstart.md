# Quickstart — Auto-Detected Text-Classification Inference Deployment

Operator-facing walkthrough for verifying the feature end-to-end against a real cluster (or any test environment that has KServe installed and reachable HuggingFace Hub access).

> **Revision 2 (2026-05-20)**: Detection runs only at deploy time. The create/update API does not expose `detectedTask` / `detectedId2Label`. The verification steps that previously read those fields from a GET response are dropped; verify topology by inspecting the live `InferenceService` instead.

## Prerequisites

- Backend built from this branch (`021-inference-task-transformer`) and deployed to the cluster.
- `CONFIG_KSERVE_ENABLED=true` on the manager pod.
- Manager pod has network egress to `https://huggingface.co` (or whatever `HUGGINGFACE_BASE_URL` is set to) **at deploy time**.
- Transformer image published and reachable by the cluster's pod network:
  ```bash
  export INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE=<your-registry>/hf-text-classification-transformer:<tag>
  ```
  Build from `C:\Users\Oleksii_Donets\IdeaProjects\kserve-text-classification-transformer` (see that repo's `README.md`).

Optionally tune the transformer's container resources:
```bash
export INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_CPU_REQUEST=100m
export INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_CPU_LIMIT=500m
export INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_MEMORY_REQUEST=256Mi
export INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_MEMORY_LIMIT=512Mi
```

## 1. Happy path — create, deploy, call

Pick a well-known sentiment model on the Hub; it has `pipeline_tag = "text-classification"` and a complete `id2label` in its config.

```bash
# Create the inference deployment — note: NO 'task' or 'id2label' fields in the body
curl -sS -X POST http://<manager>/api/v1/deployments \
  -H "content-type: application/json" \
  -d '{
        "type": "INFERENCE",
        "name": "sentiment-quickstart",
        "modelFormat": "huggingface",
        "source": {
          "$type": "huggingface",
          "modelName": "distilbert/distilbert-base-uncased-finetuned-sst-2-english"
        },
        "resources": {
          "requests": {"cpu": "500m", "memory": "2Gi"},
          "limits":   {"cpu": "2",    "memory": "4Gi"}
        }
      }' | jq
```

Expected: `201 Created`. No detection runs at this stage; the response shape is identical to any other inference-deployment create response.

Deploy it:

```bash
DEPLOY_ID=$(curl -sS http://<manager>/api/v1/deployments | jq -r '.[]|select(.name=="sentiment-quickstart")|.id')
curl -sS -X POST "http://<manager>/api/v1/deployments/${DEPLOY_ID}/deploy"
```

Watch the InferenceService in the cluster:

```bash
kubectl get isvc -n <kserve-ns> -w
# Wait for both predictor + transformer pods to become ready
```

Inspect the generated `InferenceService` to verify the chained shape:

```bash
kubectl get isvc -n <kserve-ns> <service-name> -o yaml
# spec.predictor.model.args must include --return_raw_logits and --task=sequence_classification
# spec.transformer.containers[0].image must equal $INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE
# spec.transformer.containers[0].env must contain ID2LABEL = {"0":"NEGATIVE","1":"POSITIVE"}
```

Smoke-test the public endpoint:

```bash
URL=$(kubectl get isvc -n <kserve-ns> <service-name> -o jsonpath='{.status.url}')
curl -sS -X POST "${URL}/v1/models/<service-name>:predict" \
  -H "content-type: application/json" \
  -d '{"inputs":"I love it"}'
# Expect: [{"label":"POSITIVE","score":~0.999},{"label":"NEGATIVE","score":~0.001}]
```

## 2. Negative path — non-classification model emits predictor-only manifest

```bash
curl -sS -X POST http://<manager>/api/v1/deployments \
  -H "content-type: application/json" \
  -d '{
        "type": "INFERENCE",
        "name": "translation-quickstart",
        "modelFormat": "huggingface",
        "source": {"$type": "huggingface", "modelName": "Helsinki-NLP/opus-mt-en-de"},
        "resources": {"requests": {"cpu":"500m","memory":"2Gi"}, "limits":{"cpu":"2","memory":"4Gi"}}
      }'
```

Deploy and confirm the manifest has only the `predictor` block (no `spec.transformer`).

## 3. Negative path — unusable model metadata (at deploy time)

```bash
# Create with a model whose config.json only has LABEL_0 / LABEL_1 stubs (or omits id2label entirely).
# Create succeeds — detection has not yet run.
curl -sS -X POST http://<manager>/api/v1/deployments \
  -H "content-type: application/json" \
  -d '{
        "type": "INFERENCE",
        "name": "unusable-quickstart",
        "modelFormat": "huggingface",
        "source": {"$type": "huggingface", "modelName": "<some-bare-bert-base>"},
        "resources": {"requests": {"cpu":"500m","memory":"2Gi"}, "limits":{"cpu":"2","memory":"4Gi"}}
      }'

# Deploy fails: detection runs and finds unusable id2label.
curl -sS -X POST "http://<manager>/api/v1/deployments/<unusable-id>/deploy"
# Expect: 400 with message naming the model and the missing/unusable field. No InferenceService is created.
```

## 4. Negative path — forbidden predictor arg (at deploy time)

```bash
# Create with a forbidden predictor arg — succeeds (no validation at API boundary).
curl -sS -X POST http://<manager>/api/v1/deployments \
  -H "content-type: application/json" \
  -d '{
        "type": "INFERENCE",
        "name": "forbidden-args-quickstart",
        "modelFormat": "huggingface",
        "source": {"$type": "huggingface", "modelName": "distilbert/distilbert-base-uncased-finetuned-sst-2-english"},
        "resources": {"requests": {"cpu":"500m","memory":"2Gi"}, "limits":{"cpu":"2","memory":"4Gi"}},
        "args": ["--return_probabilities"]
      }'

# Deploy fails: model is detected as TEXT_CLASSIFICATION, and the arg conflicts with the chained contract.
curl -sS -X POST "http://<manager>/api/v1/deployments/<forbidden-args-id>/deploy"
# Expect: 400 with message naming --return_probabilities as the forbidden flag. No InferenceService is created.
```

## 5. Negative path — transformer image not configured

Unset the env var (or set to empty) on the manager pod, restart it, then try to deploy an existing text-classification deployment:

```bash
curl -sS -X POST "http://<manager>/api/v1/deployments/${DEPLOY_ID}/deploy"
# Expect: 500 with message naming the missing INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE / app.inference.text-classification-transformer.image
# No InferenceService is created or modified in the cluster.
```

## 6. Update model name → re-detection on next deploy

```bash
# Update an existing chained deployment to point to a non-classification model
curl -sS -X PATCH "http://<manager>/api/v1/deployments/${DEPLOY_ID}" \
  -H "content-type: application/json" \
  -d '{"source": {"$type": "huggingface", "modelName": "Helsinki-NLP/opus-mt-en-de"}}'

# Redeploy — the new manifest reflects the new task category.
curl -sS -X POST "http://<manager>/api/v1/deployments/${DEPLOY_ID}/deploy"
kubectl get isvc -n <kserve-ns> <service-name> -o yaml
# Expect: spec.transformer block is gone; predictor block is unchanged save for the new modelName.
```

## 7. Pre-feature deployment

Boot the manager against a database containing inference-deployment rows created before this feature. No migration is required. On the next deploy, detection runs against the live HF metadata exactly as it would for a freshly-created deployment.

```bash
curl -sS -X POST "http://<manager>/api/v1/deployments/<legacy-id>/deploy"
# If the model is text-classification → chained manifest; otherwise predictor-only.
```

## Cleanup

```bash
curl -sS -X POST "http://<manager>/api/v1/deployments/${DEPLOY_ID}/undeploy"
curl -sS -X DELETE "http://<manager>/api/v1/deployments/${DEPLOY_ID}"
```

## What this verifies

- **SC-001**: zero-ceremony chained deployment from any text-classification HF model.
- **SC-002**: predictor-only path remains unchanged for non-classification models; no migration is required for pre-feature rows.
- **SC-003**: unusable metadata is rejected at deploy time before any cluster mutation.
- **SC-004**: HF unreachable / API failures (test by killing the manager's egress) → 5xx, no `InferenceService` created.
- **SC-005 & SC-006**: configuration-driven image + resources; missing image → fast deploy-time failure.
- **SC-007**: chained deployment status only flips to RUNNING when both predictor + transformer are healthy.
- **SC-008**: model swap → next deploy automatically reflects the new task category.
- **SC-010**: forbidden predictor arg → 400 with named flag at deploy time.
