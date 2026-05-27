# Quickstart: Cilium Network Policy Adjustments for Chained Predictor + Transformer

**Feature**: 022-transformer-cilium-policies | **Date**: 2026-05-27

This document is a short walkthrough for the two audiences who will touch this feature: the operator who deploys a chained inference model and wants to see the policy work, and the developer who is about to implement / review the change.

---

## For the operator

### Preconditions

- Manager is running with `app.cilium-network-policies-enabled=true`.
- Manager is on a release that includes both spec 021 (chained predictor + transformer) and this feature (022).
- Cluster has Cilium installed and enforcing `CiliumNetworkPolicy` resources, plus Istio and Knative serving the existing KServe stack.

### Deploy a chained model

```bash
# 1. Create the inference deployment (HF text-classification model)
curl -X POST $MANAGER_URL/api/v1/deployments \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "INFERENCE",
    "name": "dm-deberta",
    "imageDefinitionId": "<hf-image-def-id>",
    "source": {
      "$type": "huggingface",
      "modelName": "MoritzLaurer/DeBERTa-v3-base-mnli-fever-anli",
      "modelFormat": "huggingface"
    },
    "scaling": { "$type": "PENDING_REQUESTS", "min": 0, "max": 1 }
  }'

# 2. Deploy it
curl -X POST $MANAGER_URL/api/v1/deployments/dm-deberta/deploy
```

### Verify the augmented policy was emitted

```bash
kubectl -n kserve-models get ciliumnetworkpolicy dm-deberta -o yaml
```

Expect to see (highlighted: chained-mode additions vs. predictor-only baseline):

- `spec.egress[]` contains a block whose `toEndpoints` has three entries:
  - `serving.kserve.io/inferenceservice: dm-deberta`
  - `k8s:io.kubernetes.pod.namespace: istio-system`
  - `k8s:io.kubernetes.pod.namespace: knative-serving`

  with no `toPorts` constraint.

- The first ingress rule's `fromEndpoints` list contains four entries — the existing istio-ingressgateway, activator, autoscaler, **plus** `serving.kserve.io/inferenceservice: dm-deberta`.

- The second ingress rule's `toPorts.ports` list contains `8080/TCP` in addition to `8012/TCP` and `8022/TCP`.

### Verify the chained data path actually works

```bash
# The deployment's public URL resolves to the transformer (per spec 021 FR-018)
URL=$(curl -s $MANAGER_URL/api/v1/deployments/dm-deberta | jq -r .url)

# HF-API-shaped request → HF-API-shaped response with the model's own labels
curl -X POST "$URL" \
  -H 'Content-Type: application/json' \
  -d '{"inputs": "I love this film."}'
# → [[{"label":"entailment","score":0.92},{"label":"neutral","score":0.06}, ...]]
```

If you receive a 5xx or a hang, check the manager log for the line:

```
createCiliumNetworkPolicy. serviceNameLabel='serving.kserve.io/inferenceservice', serviceName='dm-deberta', chainedTransformer=true
```

Absence of `chainedTransformer=true` on a chained deployment is the signal that the augmentation didn't fire — file an issue with the deployment ID.

### Verify predictor-only deployments are untouched

Deploy any non-classification model (e.g. a translation model) the same way and diff its `CiliumNetworkPolicy` against the predictor-only baseline from the previous release:

```bash
kubectl -n kserve-models get ciliumnetworkpolicy <translation-deployment> -o yaml | diff -u - baseline-predictor-only.yaml
```

The diff should be empty (modulo `metadata.uid` / `metadata.resourceVersion` / timestamps).

---

## For the developer / reviewer

### What changes (files to read in this order)

1. **`spec.md`** — read the user stories and FR-001 / FR-004 / FR-005 carefully. The augmentation is gated on a task-agnostic "chained" boolean, not on `task = TEXT_CLASSIFICATION`.
2. **`plan.md`** — see the file map. The change is narrow: one record type, three managers touched (every override of `prepareServiceSpec`), one creator method extended.
3. **`research.md`** — R-001 (why a return-type wrapper, not a hook), R-002 (overload vs. wider signature), R-003 (dedup), R-005 (append to existing ingress rule).
4. **`data-model.md`** — visualizes the augmented `CiliumNetworkPolicy` shape side-by-side with the baseline.

### What to look for in code review

- Every override of `AbstractDeploymentManager.prepareServiceSpec(...)` returns `PreparedServiceSpec.unchained(...)` unless it's `InferenceDeploymentManager` — verify no manager silently regressed to bare-spec returns.
- `InferenceDeploymentManager.prepareServiceSpec(...)` reads the chained flag from the spec-021 detection result it already computes — **not** from re-inspecting the built `InferenceService` (FR-005). A test (`shouldPassUnchainedFlag_whenPredictorOnly`) asserts this.
- `CiliumNetworkPolicyCreator.create(...)` 5-arg overload still exists and still produces byte-identical output to today's behavior. The new 6-arg form with `chainedTransformer=false` produces output equal to the 5-arg overload (verified by a parity unit test).
- Port-8080 dedup is by `(port-string, protocol)` tuple; a chained deployment whose container port resolution already includes 8080 produces a single `8080/TCP` entry, not two.
- The chained-mode egress block carries **no** `toPorts` constraint (intra-cluster control-plane traffic is unrestricted on port axis). Reviewers verify this matches the reference YAML from the user.
- `@LogExecution` annotation preserved on `CiliumNetworkPolicyCreator`.

### Running tests

```bash
# During development:
./gradlew testFast --tests "*CiliumNetworkPolicyCreatorTest*" \
                   --tests "*InferenceDeploymentManagerTest*"

# Full PR gate:
./gradlew checkstyleMain checkstyleTest
./gradlew test
```

There are no new functional tests — the feature has no SQL behavior, so vendor-parity (H2/Postgres/SqlServer) suites do not gain new cases. Existing inference functional tests pick up the chained-mode cilium shape automatically (their inference deploys already exercise the chained manifest path).

### Try it locally without a cluster

`./gradlew bootRun` will start the service, but Cilium is a CNI plugin and only meaningfully enforces policy on a real cluster. For local validation, rely on the unit-test suite — it asserts the constructed `CiliumNetworkPolicy` object shape directly, which is the only invariant under our control (Cilium's enforcement of that object is its problem, not ours).

### Updating capability specs after `/speckit.implement`

Per root `CLAUDE.md` § "Numbered-spec hygiene":

1. Flip `**Status**:` in `spec.md` from `Draft` to `Implemented`.
2. Update `specs/inference-deployments/spec.md` and `specs/kubernetes-manifests/spec.md` to describe the augmented policy shape; add `Implemented via 022-transformer-cilium-policies` near the relevant requirement.
3. Add the row to `specs/README.md`'s in-flight table; flip the row's status to `Implemented` once merged.
4. Delete `specs/022-transformer-cilium-policies/checklists/` before committing the PR (constitution §spec-kit Workflow Rules — per-feature checklists are workflow-internal).
