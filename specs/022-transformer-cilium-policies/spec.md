# Feature Specification: Cilium Network Policy Adjustments for Chained Predictor + Transformer

**Feature Branch**: `022-transformer-cilium-policies`
**Created**: 2026-05-27
**Status**: Implemented (reworked post-initial-flip: HF Hub re-fetch dropped from update path, rolling-update CNP refresh added, kube-dns emitted for chained pairs under locked-down `allowedDomains`, fail-fast on absent cluster service; signal-threading reworked to a subclass-owned `buildCiliumNetworkPolicy` hook so the abstract base no longer carries any inference-specific concept)
**Capability**: inference-deployments, kubernetes-manifests
**Input**: User description: "We have added text classification logic in previous spec. Now to need to modify cilium policy when the transformer is being created. Here is example how should it look like [...] CiliumNetworkPolicy yaml with intra-InferenceService egress, an extra ingress fromEndpoint matching the same InferenceService, and ingress port 8080."

## Clarifications

### Session 2026-05-27

- Q: Should the Cilium augmentation be tied to today's specific `task = TEXT_CLASSIFICATION` signal, or generalized to "any chained inference deployment"? → A: **Generalize.** The augmentation fires whenever manifest generation emits a `transformer` block alongside the predictor — regardless of which task triggered the chained topology. Future chained tasks inherit the rule without re-opening this spec. Today the only chained task is `TEXT_CLASSIFICATION` (per spec 021), so the practical scope is unchanged; the wording is forward-compatible.
- Q: Port 8080 sourcing — hardcoded chained-mode literal, derived from container-port resolution, or config-driven? → A: **Hardcoded literal, deduplicated.** The chained-mode rule adds `8080/TCP` to the ingress port set as a literal, deduplicated against any container-port-derived ports the existing call site already supplies. If the deployment's resolved container port already includes 8080, the result is unchanged; if it does not, 8080 is still admitted. No new configuration property is introduced. This matches the user-supplied reference YAML verbatim and keeps the rule self-contained for tests.
- Q: Should chained-mode augmentation force kube-dns egress even when `allowedDomains = []`? → A: **Cluster-local DNS only.** (Reversed post-initial-flip — see Status line; the original answer assumed cluster-local IPs were sufficient, but implementation surfaced that the transformer resolves `<svc>-predictor.<ns>.svc.cluster.local` via kube-dns before reaching the istio mesh.) When `chainedTransformer=true` and `allowedDomains` is empty, the policy emits a **cluster-DNS-only** kube-dns egress block with `matchPattern: *.svc.cluster.local` (see `createClusterDnsOnlyEgress()` in `CiliumNetworkPolicyCreator.java`). External DNS resolution stays blocked — the lockdown intent is preserved on the FQDN axis. The chained-mode intra-cluster egress block (same-`InferenceService` + `istio-system` + `knative-serving`) is also emitted because it does not depend on `allowedDomains`. See Risks for the tradeoff against the narrowest possible DNS surface.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Chained inference deployment gets an intra-InferenceService Cilium policy (Priority: P1)

An operator deploys an inference deployment whose manifest generation produces a chained topology — both a `predictor` and a `transformer` block in the resulting `InferenceService`. Today, the only signal that triggers this is HuggingFace text-classification detection per spec 021, but the rule below applies to **any** chained inference deployment, present or future. At the same time, the Cilium network policy generated for the deployment is **augmented** so that traffic actually flows between the two chained components inside the cluster:

- The transformer pod can reach the predictor pod (same InferenceService label) over the in-cluster network.
- The transformer pod can reach the Istio sidecar / control plane (`istio-system` namespace) and Knative components (`knative-serving` namespace), which both sit on the chained data path.
- The predictor pod accepts ingress traffic from the transformer pod (same InferenceService label) in addition to the existing istio-ingressgateway / activator / autoscaler sources.
- Ingress on the predictor/transformer pods accepts the KServe model-server port `8080` in addition to the existing Knative queue-proxy ports (`8012`, `8022`).

Without these rules, the Cilium policy blocks the transformer → predictor hop, the chained deployment can never reach `RUNNING`, and the operator sees the deployment hang in `PENDING` until the policy is hand-edited. The whole point of spec 021 — "operator picks a text-classification model, gets a working endpoint" — fails on clusters with Cilium policies enabled.

**Why this priority**: Spec 021 already declared chained deployments must reach `RUNNING` when both components are healthy (FR-019 / SC-007). On any cluster with `app.cilium-network-policies-enabled=true`, that contract cannot hold without this change — the chained path is severed at the network layer. This is the gating fix that makes spec 021 actually usable end-to-end on the canonical target environment.

**Independent Test**: On a cluster with Cilium policies enabled, deploy a HuggingFace text-classification model (e.g. `distilbert-base-uncased-finetuned-sst-2-english`). Inspect the generated `CiliumNetworkPolicy` for the deployment: confirm the additional egress block (toEndpoints: same-InferenceService, istio-system, knative-serving), the additional ingress fromEndpoint matching the same InferenceService, and ingress port `8080`. Hit the deployment's public URL with a HuggingFace-style classification request; receive `[{label, score}, ...]` — proving the transformer reached the predictor through the policy.

**Acceptance Scenarios**:

1. **Given** an inference deployment whose manifest generation emits a chained topology (a `transformer` block alongside the predictor — today triggered by spec 021's `TEXT_CLASSIFICATION` detection), **When** the deployment is deployed and Cilium policies are enabled, **Then** the generated `CiliumNetworkPolicy` for that deployment contains all of the following on top of the baseline rules:
   - An additional `egress` block with `toEndpoints` listing three match-label entries (OR-semantics):
     - `serving.kserve.io/inferenceservice: <deploymentName>`
     - `k8s:io.kubernetes.pod.namespace: istio-system`
     - `k8s:io.kubernetes.pod.namespace: knative-serving`
   - The first `ingress` rule's `fromEndpoints` list includes an additional match-label entry `serving.kserve.io/inferenceservice: <deploymentName>` alongside the existing istio-ingressgateway / activator / autoscaler entries.
   - The `ingress` `toPorts` list includes port `8080/TCP` in addition to the existing `8012/TCP` and `8022/TCP`.
2. **Given** that policy is applied, **When** a request reaches the deployment's public URL, **Then** the transformer pod successfully forwards to the predictor pod and returns a classification response (end-to-end chained path works under Cilium enforcement).
3. **Given** the deployment is deleted, **When** Kubernetes resources are cleaned up, **Then** the augmented Cilium policy is removed together with the InferenceService — no orphaned chained-mode rules left behind.

---

### User Story 2 - Predictor-only inference deployments keep the existing Cilium policy unchanged (Priority: P1)

An operator deploys an inference deployment whose manifest generation produces a predictor-only `InferenceService` (no `transformer` block) — today, every non-text-classification HF model and every non-HF inference source. The generated `CiliumNetworkPolicy` is **byte-identical** to today's predictor-only policy — same egress, same ingress fromEndpoints, same ingress ports. No new same-InferenceService egress block, no extra fromEndpoint, no port `8080` injection. This preserves the existing behavior for every predictor-only inference deployment and for every other deployment type (Knative, NIM, image-build jobs).

**Why this priority**: Spec 021 explicitly preserved predictor-only manifests as the backward-compatibility lane (Story 2 of 021, SC-002). The Cilium policy must respect that same lane — any policy change that widens access for non-chained deployments is a security regression. The chained additions are gated on the same `task = TEXT_CLASSIFICATION` signal that gates the transformer block.

**Independent Test**: Deploy a non-classification model (e.g. a translation model). Diff its `CiliumNetworkPolicy` against the policy generated by the same deployment configuration on the previous release — they must be identical. Repeat for a NIM deployment and a Knative deployment to confirm those code paths are untouched.

**Acceptance Scenarios**:

1. **Given** an inference deployment whose manifest generation produces a predictor-only `InferenceService` (no `transformer` block), **When** the deployment is deployed with Cilium policies enabled, **Then** the generated `CiliumNetworkPolicy` is identical (modulo metadata.uid / resourceVersion) to the pre-feature predictor-only policy: no same-InferenceService egress block, no `serving.kserve.io/inferenceservice` entry in ingress fromEndpoints, no `8080/TCP` in ingress toPorts (unless the deployment's container port resolution already includes it as today).
2. **Given** a Knative, NIM, or image-build pipeline path that creates Cilium policies, **When** that code path runs, **Then** the generated policy is unchanged from today's behavior — the chained-mode augmentation does not bleed into any non-inference or non-chained code path.
3. **Given** the global feature flag `app.cilium-network-policies-enabled=false`, **When** any deployment (chained or not) is deployed, **Then** no `CiliumNetworkPolicy` is created — the flag remains the master switch (unchanged from today).

---

### User Story 3 - Topology transition flips the Cilium policy on next deploy (Priority: P2)

An operator updates the `modelName` of an existing inference deployment from a text-classification model to a non-classification model (or vice versa). On the next deploy, spec 021's detection re-runs and the manifest topology flips. The Cilium policy regenerated alongside the InferenceService **flips with it**: chained → predictor-only loses the augmented rules; predictor-only → chained gains them. No stale rules linger from the previous topology.

**Why this priority**: Spec 021 Story 5 / SC-008 already guaranteed that a model swap correctly switches manifest topology on the next deploy. The Cilium policy regeneration must follow the same signal so that a chained-to-predictor-only swap doesn't leave the transformer ingress fromEndpoint hanging (mild security concern) and a predictor-only-to-chained swap actually wires up the new path (correctness concern — without this, the new chained deployment hangs in `PENDING`).

**Independent Test**: Create and deploy a chained deployment; inspect the Cilium policy (augmented). Update the modelName to a translation model; redeploy. Inspect the Cilium policy — augmented rules are gone. Reverse the flow — augmented rules reappear. No manual cleanup required between transitions.

**Acceptance Scenarios**:

1. **Given** a chained deployment with the augmented Cilium policy, **When** the deployment's modelName is updated to a non-classification model and the deployment is redeployed, **Then** the regenerated Cilium policy is the baseline predictor-only policy with the chained-mode rules removed.
2. **Given** a predictor-only deployment with the baseline Cilium policy, **When** the deployment's modelName is updated to a text-classification model with valid metadata (per spec 021 Story 3) and the deployment is redeployed, **Then** the regenerated Cilium policy is the augmented chained-mode policy.
3. **Given** an update that re-runs `applyDomainWhitelist` to change the `allowedDomains` set on a chained deployment, **When** the policy is regenerated, **Then** the chained-mode augmentation is still present (the topology signal is independent of the domain whitelist signal).

---

### Edge Cases

- **Cilium policies disabled cluster-wide (`app.cilium-network-policies-enabled=false`)**: No policy is created for any deployment, chained or not. Nothing changes from today — the master switch wins.
- **`allowedDomains` empty on a chained deployment**: The world egress and the FQDN-style kube-dns block (which would otherwise allow external DNS) are omitted as today — external egress stays locked down. The chained-mode rule **does** emit a cluster-DNS-only kube-dns egress (`matchPattern: *.svc.cluster.local`) so the predictor's cluster-internal DNS name resolves under lockdown; external DNS exfil remains blocked. The chained-mode intra-cluster egress block (same-InferenceService / istio-system / knative-serving) is also emitted because it does not depend on `allowedDomains`. See Risks for the DNS-surface tradeoff.
- **`allowedDomains = ["*"]` on a chained deployment**: World egress is permitted as today, and the chained-mode egress block is additionally present. The two egress blocks coexist.
- **Container-port resolution already includes 8080**: The chained-mode logic adds 8080 as a literal but deduplicates against existing ingress port entries (matched by port string + protocol). Net result: a single `8080/TCP` entry, regardless of whether it arrived via container-port resolution, the chained-mode literal, or both.
- **Chained-mode signal unavailable (HF Hub unreachable at deploy)**: Spec 021 already rejects the deploy with 5xx before any cluster mutation (FR-007). No `CiliumNetworkPolicy` is created — same gating as the InferenceService itself.
- **Deployment redeployed without model change but with `app.cilium-network-policies-enabled` flipped from true to false**: Existing policy is deleted as today; no policy is created. No special handling for the chained flavor.
- **Non-inference deployment types (Knative, NIM, image-build jobs)**: Their call sites pass non-`TEXT_CLASSIFICATION`-tracked labels (`serving.knative.dev/service`, job labels, etc.). The chained-mode augmentation is keyed off the inference-deployment chained signal only; these call sites must continue to produce today's policy verbatim.

## Requirements *(mandatory)*

### Functional Requirements

#### Policy generation — chained-mode augmentation

- **FR-001**: The generated `CiliumNetworkPolicy` for a **chained inference deployment** — defined as an inference deployment whose manifest generation emits a `transformer` block alongside the predictor — SHALL contain the following rules in addition to the baseline policy:

  - An additional `egress` rule whose `toEndpoints` list has three match-label entries (OR-semantics, separate selectors):
    - `serving.kserve.io/inferenceservice: <deploymentServiceName>`
    - `k8s:io.kubernetes.pod.namespace: istio-system`
    - `k8s:io.kubernetes.pod.namespace: knative-serving`
  - An entry `serving.kserve.io/inferenceservice: <deploymentServiceName>` appended to the existing `ingress[*].fromEndpoints` list that currently contains istio-ingressgateway / activator / autoscaler.
  - The port `8080/TCP` included in the `ingress.toPorts` list as a hardcoded chained-mode literal, in addition to the existing `8012/TCP` and `8022/TCP` and the deployment's resolved container port(s). If `8080` is already present from container-port resolution, it SHALL be deduplicated (single entry, not two). No new configuration property is introduced to source this port.

  The `<deploymentServiceName>` SHALL be the same value used as the policy's endpoint-selector match-label value (the existing service-name label value the inference deployment passes today).

- **FR-002**: The new `egress` rule contributed by FR-001 SHALL NOT carry a `toPorts` constraint — same-InferenceService and intra-namespace control-plane traffic is permitted on any port, mirroring the reference YAML supplied by the user.

- **FR-003**: The chained-mode augmentation in FR-001 SHALL be additive — it MUST NOT remove, replace, or reorder any existing egress entity (world / kube-dns), ingress fromEndpoints entry (istio-ingressgateway / activator / autoscaler), or ingress port (8012, 8022, container ports). The augmented policy is a strict superset of the baseline policy.

- **FR-004**: When manifest generation produces a predictor-only `InferenceService` (no `transformer` block), or the deployment is not an inference deployment (Knative, NIM, image-build job, etc.), the generated `CiliumNetworkPolicy` SHALL be identical to today's baseline — no chained-mode rules emitted. Byte-equivalence with the pre-feature output is the acceptance criterion.

- **FR-005**: The chained-mode signal SHALL be the same fact that decided whether a `transformer` block was emitted in the manifest — derived in the same deploy operation and never read from a persisted column, never sourced by re-running upstream detection (HuggingFace Hub), and never sourced by parsing a serialized manifest blob. Inspecting the strongly-typed in-memory `InferenceService` object the subclass *just built* (a one-line object-graph check on `spec.transformer`) is permitted and is the preferred approach — that object is already in scope at the policy-creation site. On the **deploy** and **rollingUpdate** paths the signal is sourced from the just-built `InferenceService`. On the **`updateCiliumNetworkPolicy(id)`** path (operator edits `allowedDomains` without changing topology) there is no in-flight manifest, so the signal is sourced from the live KServe `InferenceService` resource — a Kubernetes API read, not an external upstream fetch. This adds one cluster read per `allowedDomains` edit and a new failure mode if the cluster resource is unexpectedly absent; the endpoint refuses the update rather than silently downgrading the policy (see Risks). The signal is task-agnostic: any current or future chained inference task that emits a `transformer` block triggers the augmentation without further spec changes. The chained-mode reachability logic SHALL live entirely inside the inference deployment manager — the abstract deployment base and the Cilium policy creator MUST NOT carry any inference-specific concept (transformer / chained / KServe topology). Implementation: a `buildCiliumNetworkPolicy(D, S, name, allowedDomains, ports)` hook on `AbstractDeploymentManager` with a baseline-policy default; `InferenceDeploymentManager` overrides it and reads chained-ness from the supplied `serviceSpec` (or fetches the live one when `serviceSpec` is null).

#### Lifecycle and idempotency

- **FR-006**: When an existing chained deployment is redeployed without topology change, the regenerated `CiliumNetworkPolicy` SHALL be functionally equivalent to the previously applied policy (idempotent). Server-side resourceVersion / uid differences are expected and ignored.

- **FR-007**: When an existing inference deployment's topology flips (chained → predictor-only or vice versa) per spec 021 Story 5, the regenerated `CiliumNetworkPolicy` SHALL match the new topology — chained-mode rules are added on `predictor-only → chained` and removed on `chained → predictor-only`. No stale rules remain after a transition.

- **FR-008**: On deletion of the deployment, the `CiliumNetworkPolicy` SHALL be removed via the existing cleanup path (per `deployments` spec and `kubernetes-cleanup` spec). The chained-mode augmentation introduces no new resource, no new finalizer, no new cleanup hook.

- **FR-009**: The chained-mode augmentation SHALL respect the existing master switch `app.cilium-network-policies-enabled`. When the flag is false, no policy is created — chained or not.

#### Cross-cutting

- **FR-010**: The change MUST NOT require any new application property, new env var, new Flyway migration, new API field, or new DTO. The chained signal already flows through spec 021's deploy path; the cilium policy creator becomes one more consumer of the same signal.

- **FR-011**: The augmented policy SHALL be regenerated atomically with the InferenceService manifest during the same deploy operation. If the InferenceService apply is rejected, the policy MUST NOT remain in the augmented state without a matching chained InferenceService.

### Key Entities

- **CiliumNetworkPolicy (augmented form)**: The existing per-deployment `CiliumNetworkPolicy` resource, now conditionally bearing the chained-mode egress block, the same-InferenceService ingress fromEndpoint, and ingress port `8080`. Same kind, same name (= deployment service name), same namespace, same endpoint selector — only the spec contents are extended.
- **Chained-mode signal**: A boolean fact computed during manifest generation — "this deployment's `InferenceService` carries a `transformer` block alongside the predictor." Not persisted; flows inline from manifest generation into Cilium policy generation. Today the only producer is spec 021's `task = TEXT_CLASSIFICATION` detection (sourced from HuggingFace Hub on every deploy); any future chained inference task that emits a transformer block becomes a producer automatically.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a cluster with `app.cilium-network-policies-enabled=true`, a freshly deployed HuggingFace text-classification deployment reaches `RUNNING` state and serves classification responses end-to-end via its public URL — proving the transformer → predictor hop traverses the Cilium policy correctly. Today (without this change), the same deployment hangs in `PENDING`.
- **SC-002**: For every non-chained deployment (predictor-only inference, Knative, NIM, image-build job), the generated `CiliumNetworkPolicy` is byte-identical to the policy generated by the previous release. Verified by an automated diff in functional tests against captured baseline policies.
- **SC-003**: A topology flip (chained ↔ predictor-only) via modelName update produces the correct policy on the next deploy, with no manual cleanup. Verified by deploy / update / redeploy sequences in a functional test that asserts the resulting policy shape after each step.
- **SC-004**: The augmented policy is removed alongside the InferenceService when the deployment is deleted. Verified by the existing cleanup tests — no new orphan-resource cases introduced.
- **SC-005**: The change ships with zero new application properties, zero new env vars, zero new DTO fields, zero Flyway migrations. Verified by reviewing the diff against `configuration/`, `web/dto/`, and `src/main/resources/db/migration/`.
- **SC-006**: When `app.cilium-network-policies-enabled=false`, no policy is created for any deployment, chained or not. Existing test coverage continues to pass without modification.

## Assumptions

- **Cilium signal flow**: Policy customization is owned by a subclass-overridable `buildCiliumNetworkPolicy(D, S, name, allowedDomains, ports)` hook on `AbstractDeploymentManager`. The default returns the baseline (non-augmented) policy via the existing 5-arg creator overload; `InferenceDeploymentManager` overrides it to read chained-ness from the supplied `serviceSpec` and call the 6-arg overload. All other call sites (Knative / NIM / image-build pipeline) inherit the default and produce today's baseline policy byte-for-byte. The abstract base never sees `chainedTransformer` and the policy creator stays free of inference-specific reachability logic.
- **Update-path signal source**: `updateCiliumNetworkPolicy(id)` (invoked on `allowedDomains` / `containerPort` edits) invokes the hook with `serviceSpec = null` because there is no in-flight manifest. `InferenceDeploymentManager`'s hook override responds by reading the live cluster `InferenceService` and checking for a `transformer` block — not by re-running HuggingFace detection. Topology flips reach the policy via `rollingUpdate`, which passes the freshly-built spec into the hook (no cluster read needed). The cluster read adds one Kubernetes API call per `allowedDomains` edit (a new dependency vs. the pre-feature path that derived no signal at all); if the live `InferenceService` is absent the hook throws `IllegalStateException`, wrapped by the base as `DeploymentException`, rather than emitting a downgraded policy. See Risks for the failure-mode tradeoff.
- **Service-name label is reused**: The same-InferenceService match-label value re-uses the deployment's existing service-name (the value already used as the policy's endpoint selector and resource name today). No new naming convention.
- **Port 8080 is the KServe model server port**: The KServe predictor and transformer containers both listen on `8080` for the model-serving protocol; admitting it on ingress is required for both the external-ingress hop and the transformer → predictor in-cluster hop. If a future KServe version pins a different port, this assumption is revisited.
- **Istio + Knative are mandatory infra**: Chained KServe deployments run on top of Istio (`istio-system`) and Knative (`knative-serving`). These namespaces are present on every cluster that supports the existing inference-deployments capability — adding egress to them does not require any new infrastructure precondition.
- **No bidirectional ingress symmetry needed**: The reference YAML grants ingress from `serving.kserve.io/inferenceservice: <name>` and egress to the same label set. Cilium evaluates ingress/egress separately on each pod; with a single policy targeting both predictor and transformer pods (same endpoint selector), the two-way same-InferenceService traffic is admitted symmetrically without further entries.
- **`allowedDomains` semantics unchanged**: The chained-mode egress block is independent of `allowedDomains`. Operators can continue to manage runtime domain access via the existing whitelist (per `domain-whitelist` spec) without affecting chained-mode connectivity.
- **Reference YAML is authoritative**: The user-supplied YAML (with Russian inline annotations marking the two new sections) is the authoritative shape of the augmented policy for v1. Any divergence required by Cilium policy validation surfaces in implementation and is folded back into the spec.

## Risks

- **New Kubernetes API dependency on the `allowedDomains`-edit path**: The pre-feature `updateCiliumNetworkPolicy(id)` flow needed no live cluster read — it regenerated the policy from local deployment state alone. The post-feature flow reads the `InferenceService` resource to derive the chained signal, which introduces a new external failure mode on that endpoint: transient API-server unavailability, RBAC drift, or a delete race with the operator's edit. The endpoint surfaces these as `DeploymentException` (5xx); operators must retry. Alternative considered and rejected: silently default to `chainedTransformer=false` when the read fails. That preserves endpoint availability but recreates bug #87 (chained pair loses transformer→predictor reachability) on every transient read failure. Failing the update is the safer default.
- **Cluster-local DNS resolution on locked-down `allowedDomains`**: Chained pairs use `*-predictor.<ns>.svc.cluster.local` to reach the predictor. When `allowedDomains` is empty (security lockdown), the policy emits a cluster-DNS-only kube-dns egress block (`matchPattern: *.svc.cluster.local`) — kube-dns is reachable for cluster-internal names but external DNS resolution remains blocked. This is the minimum DNS surface required for chained mode to function under lockdown.
- **Layering of inference-specific logic**: the chained-transformer concept is meaningful only to KServe `InferenceService` topology — Knative and NIM deployments cannot have transformers. Earlier implementations of this spec threaded a `chainedTransformer` boolean through the abstract base class's `prepareServiceSpec` return type and a `Predicate`-based update-path seam, forcing Knative and NIM to wrap their specs in ceremonial `DeployContext.unchained(...)` calls. The shipping implementation instead exposes a single `buildCiliumNetworkPolicy(D, S, name, allowedDomains, ports)` protected hook with a baseline default; only `InferenceDeploymentManager` overrides it. The abstract base contains zero references to `transformer`, `chained`, or `InferenceService` — verified by `grep` in CI.
- **DB connection held across the CNP refresh on the `allowedDomains` / `containerPort` edit path**: `DeploymentService.updateDeployment` calls `deploymentManager.updateCiliumNetworkPolicy(id)` synchronously inside the `@Transactional` method (matching the pre-feature shape). The inference-path override fetches the live `InferenceService` from the cluster and writes the regenerated CNP — both K8s calls happen while the pooled DB connection is still held. On slow kube-apiserver this widens the connection-hold window vs. the pre-feature path (which had no cluster read). In exchange the operator gets a loud 5xx if the K8s write fails, instead of a post-commit drift that Spring's `TransactionSynchronizationUtils.invokeAfterCommit` would have swallowed. An `afterCommit`-based deferral was tried in PR #346 and reverted — the silent-failure cost outweighed the connection-pool savings for the actual call volumes seen on this endpoint. If connection-pool pressure surfaces in production, reconsider as a follow-up rather than as part of spec 022.
