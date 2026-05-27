# Data Model: Cilium Network Policy Adjustments for Chained Predictor + Transformer

**Feature**: 022-transformer-cilium-policies | **Date**: 2026-05-27

## Persistence

**None.** This feature introduces no entities, no columns, no migrations, no audit-table changes. The chained-mode signal is transient and lives only on the call stack between `prepareServiceSpec(...)` and `createCiliumNetworkPolicy(...)`.

The existing `inference_deployment` table, its Envers audit table, and every other persistence artifact remain unchanged.

## In-memory types

### `PreparedServiceSpec<S>`

Tiny record introduced in `service/deployment/` to carry the chained-mode signal alongside the prepared K8s manifest without leaking inference-specific types into the abstract base class.

| Field | Type | Description |
|---|---|---|
| `spec` | `S` (generic) | The prepared service spec the abstract deployment manager will hand to `createService(...)`. For inference, this is a KServe `InferenceService`; for Knative, a `Service`; for NIM, a `KServeInferenceService` (existing types — no change). |
| `chainedTransformer` | `boolean` | `true` iff manifest generation emitted a `transformer` block alongside the predictor. Today only `InferenceDeploymentManager` ever returns `true`, and only when spec 021's `InferenceTaskDetector` produced `task = TEXT_CLASSIFICATION`. Any future chained inference task that emits a transformer block flips this flag automatically (per spec 022 clarification Q1 — task-agnostic signal). |

Static factories:

- `PreparedServiceSpec.unchained(S spec)` — convenience for predictor-only / non-inference managers. Sets `chainedTransformer = false`.
- `PreparedServiceSpec.chained(S spec)` — convenience for chained-mode emission. Sets `chainedTransformer = true`.

**Lifetime**: created inside `prepareServiceSpec(...)`, consumed within the same `deploy(...)` invocation, eligible for GC after `createCiliumNetworkPolicy(...)` and `createService(...)` return.

**Mutability**: immutable record. Java's record semantics give equals/hashCode/toString for free.

### `CiliumNetworkPolicy` (augmented shape — no new Java type)

The existing Fabric8-generated `io.cilium.v2.CiliumNetworkPolicy` model is unchanged. What changes is the **shape** of the policy object that `CiliumNetworkPolicyCreator` constructs when `chainedTransformer=true`. The augmented shape is documented in [spec.md §FR-001](./spec.md#functional-requirements) and is summarized here for the developer view:

```text
spec.endpointSelector.matchLabels = { <serviceNameLabel>: <serviceName> }   # unchanged

spec.egress[]:
  # — Existing: external world / FQDN allow-list (omitted when allowedDomains is empty)
  # — Existing: kube-dns egress (omitted when allowedDomains is empty)
  # — NEW (chained-only): intra-cluster control-plane + same-InferenceService
    toEndpoints:
      - matchLabels: { serving.kserve.io/inferenceservice: <serviceName> }
      - matchLabels: { k8s:io.kubernetes.pod.namespace: istio-system }
      - matchLabels: { k8s:io.kubernetes.pod.namespace: knative-serving }
    # NO toPorts → any port permitted

spec.ingress[]:
  # — ingress rule 1: fromEndpoints
    fromEndpoints:
      - { app: istio-ingressgateway, k8s:io.kubernetes.pod.namespace: istio-system }   # existing
      - { app: activator, k8s:io.kubernetes.pod.namespace: knative-serving }            # existing
      - { app: autoscaler, k8s:io.kubernetes.pod.namespace: knative-serving }           # existing
      - { serving.kserve.io/inferenceservice: <serviceName> }                           # NEW (chained-only)
  # — ingress rule 2: toPorts
    toPorts:
      ports:
        - { port: '8012', protocol: TCP }   # existing
        - { port: '8022', protocol: TCP }   # existing
        - { port: '<containerPort>', protocol: TCP } * N   # existing (caller-supplied)
        - { port: '8080', protocol: TCP }   # NEW (chained-only; deduped if container port already 8080)
```

When `chainedTransformer=false` (predictor-only inference, Knative, NIM, image-build jobs), none of the "NEW" lines above are emitted — the policy is byte-identical to the pre-feature output (per spec SC-002).

## Relationships

```text
InferenceDeploymentManager.prepareServiceSpec(deployment)
  └─ uses spec-021's InferenceTaskDetector → InferenceTaskDetectionResult (existing, transient)
  └─ returns PreparedServiceSpec<InferenceService>(
         spec = built InferenceService,
         chainedTransformer = detection.task() == TEXT_CLASSIFICATION
     )

AbstractDeploymentManager.deploy(id)
  ├─ var prepared = prepareServiceSpec(deployment)
  └─ afterCommit:
       createCiliumNetworkPolicy(
           id,
           getEffectiveDeploymentAllowedDomains(deployment),
           getCiliumIngressPorts(deployment),
           deployment.getServiceName(),
           prepared.chainedTransformer()              # NEW arg threaded through
       )
       createService(namespace, prepared.spec())
```

For non-inference managers (`KnativeDeploymentManager`, `NimDeploymentManager`):

```text
prepareServiceSpec(deployment) → PreparedServiceSpec.unchained(builtSpec)
                                          ^
                                          chainedTransformer = false (always)
```

## Validation rules

None at the data layer — `PreparedServiceSpec` is a pure carrier. Validation of operator-supplied predictor args (per spec 021 FR-014a) continues to live where it does today (inside the inference manifest generation path); this feature does not change that surface.
