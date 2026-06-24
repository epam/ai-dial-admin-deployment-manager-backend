# Research: Cilium Network Policy Adjustments for Chained Predictor + Transformer

**Feature**: 022-transformer-cilium-policies | **Date**: 2026-05-27

Phase 0 records the design decisions that resolve the open questions from `plan.md` and `spec.md`. The [Clarifications session in spec.md](./spec.md#clarifications) settled three product-level questions (signal generality, port-8080 sourcing, kube-dns egress); this document captures the implementation-level choices the plan depends on.

---

## R-001 — Signal threading: subclass-owned policy hook (chosen) vs. return-type wrapper vs. detection re-run

**Decision**: Expose a protected hook `buildCiliumNetworkPolicy(D deployment, S serviceSpec, String serviceName, List<String> allowedDomains, Set<Integer> ports)` on `AbstractDeploymentManager`. The default implementation returns the baseline (non-augmented) policy via the existing 5-arg creator overload. `InferenceDeploymentManager` overrides it: when `serviceSpec` is non-null (deploy / rollingUpdate path), it reads chained-ness off `serviceSpec.getSpec().getTransformer()`; when `serviceSpec` is null (the `updateCiliumNetworkPolicy(id)` path, which has no in-flight manifest), it reads the live `InferenceService` from the cluster — see R-005.

**Rationale**:
- The chained-transformer concept is meaningful only to KServe `InferenceService` topology. Confining the logic to `InferenceDeploymentManager` keeps the abstract base, Knative, and NIM free of any inference-specific concept. Verified by `grep` over `AbstractDeploymentManager.java` for `chained`, `transformer`, `InferenceService`, `DeployContext` — should return zero hits.
- Satisfies spec FR-005: the deploy / rollingUpdate paths derive the signal from the strongly-typed in-memory `InferenceService` the subclass just built (a single object-graph check, not a manifest re-parse). The update path goes to the cluster (R-005). No persisted column, no second HF Hub fetch.
- Keeps the signal task-agnostic — the override reads `spec.getTransformer() != null`, not "is text-classification". Any future chained inference task that emits a transformer block triggers the augmentation without further spec changes.

**Alternatives considered (and rejected)**:
- **Wrap the `prepareServiceSpec` return in a `DeployContext<S>(S spec, boolean chainedTransformer)` record and thread the boolean through `createCiliumNetworkPolicy`** — the initial implementation chosen for this spec. Forces Knative and NIM to wrap their specs in ceremonial `DeployContext.unchained(...)` calls, leaks a KServe-only concept into the abstract base's signature, and adds a `Predicate<D>` template seam on the update path. The hook approach gives the same behavior without the leakage. Reverted in commit `<TBD-this-PR>`.
- **Re-run `prepareServiceSpec(...)` on the update path** to recompute the chained bit: re-runs `inferenceTaskDetector.detect(...)`, which calls the HuggingFace Hub. Rejected — see R-005.
- **Persist the chained bit on the domain model (`InferenceDeployment.isChained()`)**: pollutes the domain model with deployment-time state, risks accidental persistence if ever mapped, and adds a backfill problem for pre-feature deployments. Rejected.
- **Inspect the partial `InferenceService` for `transformer != null`**: this was forbidden under an over-broad reading of FR-005 in the initial implementation. The clarified FR-005 (post-rework) explicitly permits inspecting the in-memory typed object — what was forbidden was *re-running upstream detection* and *parsing a serialized manifest blob*, not reading a field on the object the caller just built. The hook approach is the materialization of this clarification.

---

## R-002 — Policy creator API shape: new overload vs. wider single method

**Decision**: Add a new 6-arg overload `create(namespace, matchLabelName, matchLabelValue, allowedDomains, ports, chainedTransformer)`. Keep the existing 5-arg `create(...)` as a thin delegate that calls the new method with `chainedTransformer=false`.

**Rationale**:
- Every non-inference call site (`JobRunner`, `KnativeDeploymentManager`-default-hook, `NimDeploymentManager`-default-hook, `AbstractDeploymentManager.applyDomainWhitelist(...)` re-creation path) keeps compiling without modification and produces byte-identical output — backing spec SC-002.
- Inference is the only deployment manager that calls the 6-arg form. The call site is now confined to one place: the `buildCiliumNetworkPolicy` override in `InferenceDeploymentManager`. No threading through the abstract base.
- Future deployment types (e.g. NIM-with-transformer, if/when introduced) opt in by overriding the same hook in their own manager — no global change needed.

**Alternatives considered**:
- **Single 6-arg signature, update every caller**: forces churn in `JobRunnerTest`, `NimDeploymentManagerTest`, `KnativeDeploymentManagerTest` for no behavioral reason. Rejected.
- **Builder pattern (`CiliumNetworkPolicyCreator.builder().namespace(...).chained(true).build()`)**: over-engineered for a method with at most 6 parameters used in 4 call sites.

---

## R-003 — Port-8080 deduplication strategy

**Decision**: When `chainedTransformer=true`, append `8080/TCP` to the working list **after** the call-site-supplied `ports` set has been folded in, then deduplicate by `(port-string, protocol)` tuple. The existing port set (`8012`, `8022`, plus any container-port-derived entries) is preserved in order; the chained-mode literal is appended only if not already present.

**Rationale**:
- Honors [clarification Q2](./spec.md#clarifications): "hardcoded literal, deduplicated."
- Idempotent: redeploy of the same chained deployment produces an identical port list regardless of whether container-port resolution has been changed in the interim to also include 8080.
- Test coverage: `shouldDeduplicatePort8080_whenAlreadyResolvedFromContainerPort()` confirms the dedup behavior; `shouldAppend8080TcpToIngressPorts_whenChainedTrue()` confirms the addition when the supplied set lacks it.

**Alternatives considered**:
- **Prepend instead of append**: would reorder the existing ports, breaking byte-equivalence for the predictor-only baseline test (SC-002). Rejected.
- **Source 8080 from a new property `app.kserve-model-server-port`**: explicitly rejected in [clarification Q2 option C](./spec.md#clarifications) — violates the zero-new-config promise (SC-005).

---

## R-004 — Egress block construction: free-standing vs. attached to existing egress entries

**Decision**: Build the chained-mode intra-cluster egress block as a **separate** `Egress` entry appended to the existing egress list. It carries only `toEndpoints` (three matchLabel selectors, OR-semantics via separate list entries) and **no** `toPorts` — same shape as the user-supplied reference YAML.

**Rationale**:
- Matches the reference YAML exactly: the user's example shows the intra-cluster rule as its own egress block, not folded into the external (`toEntities: world`) or kube-dns (`toEndpoints: kube-dns`) blocks.
- Independent of `allowedDomains`: the chained-mode block is emitted even when `allowedDomains` is empty (per [clarification Q3](./spec.md#clarifications) and the edge-case note in `spec.md`). Building it separately means it does not get pruned alongside the world/kube-dns blocks when `allowedDomains` is empty.
- Omitting `toPorts` means Cilium permits any port on the matched endpoints — required because intra-namespace istio-control-plane and same-InferenceService traffic flows on a variety of ports (sidecar admin, knative metrics, etc.) and pinning them is brittle.

**Alternatives considered**:
- **Fold same-InferenceService into the external `world` block as a `toFQDNs` entry**: wrong Cilium semantics — FQDNs are resolved via DNS and don't match pod selectors.
- **Pin specific ports in the chained-mode egress block**: enumerating every Istio sidecar / Knative queue-proxy port the transformer→predictor hop traverses is a constant maintenance burden. The reference YAML deliberately leaves it unrestricted.

---

## R-005 — `updateCiliumNetworkPolicy(id)` signal source: cluster read vs. re-run detection vs. cache

**Decision**: When the operator edits `allowedDomains` (or another non-topology field) on an existing deployment, the policy is regenerated via `updateCiliumNetworkPolicy(id)`. The chained flag is sourced by reading the live `InferenceService` from the API server and checking for a `spec.transformer` block. The deployment manager refuses to proceed (throws `DeploymentException`) if the cluster resource is unexpectedly absent, rather than silently downgrading to `chainedTransformer=false`.

**Rationale**:
- The previous approach re-ran `prepareServiceSpec(...)` solely to derive the flag, which re-issued two HuggingFace Hub HTTP calls per `allowedDomains` edit — directly contradicting FR-005 and adding HF Hub as a hard dependency on a domain-whitelist endpoint that has no business touching HF.
- A Kubernetes API read is bounded, in-cluster, and already required for many other operator endpoints — comparable risk surface, no external dependency.
- Failing fast (vs. silent degradation) is the safer default: a silently-stripped chained augmentation recreates bug #87 (transformer→predictor blocked), which is exactly what spec 022 was opened to fix. Operators retrying a 5xx is a recoverable outcome; running a wedged deployment under a downgraded policy is not.

**Alternatives considered**:
- **Cache the chained bit in the `disposableResourceManager` metadata alongside the CNP itself**: removes the cluster read entirely on the update path. Rejected for v1 because it introduces a new persistence concern (`disposableResourceManager` is a cleanup/lifecycle store, not a domain cache) and adds a fork in the failure-mode tree. Worth revisiting if the API read shows up as a bottleneck.
- **Silently default to `chainedTransformer=false` on read failure**: rejected — see Risks in spec.md.

---

## R-006 — Test strategy: baseline byte-equivalence vs. full snapshot

**Decision**: For non-chained call sites (predictor-only inference, Knative, NIM, JobRunner, applyDomainWhitelist), assert byte-equivalence by re-running the existing test cases unchanged — they call the 5-arg overload and the assertions remain valid because the delegate produces identical output. For chained call sites, assert the augmented shape via targeted AssertJ assertions on the constructed `CiliumNetworkPolicy` object (no YAML snapshot files).

**Rationale**:
- Existing tests already encode the baseline policy shape implicitly. Re-running them is the cheapest, most reliable byte-equivalence check — they fail loudly if the delegate accidentally changes behavior.
- Targeted AssertJ assertions for the chained-mode delta are readable in code review and easy to update if Cilium model types evolve. YAML snapshot files create churn whenever Fabric8 regenerates the model with new optional fields.
- A new `shouldNotEmitChainedAdditions_whenChainedFalse()` test explicitly asserts that a fresh call to the new 6-arg method with `chainedTransformer=false` produces the same object the 5-arg overload produces — defends against future regressions.

**Alternatives considered**:
- **Golden-file YAML snapshots**: brittle to Fabric8 model regeneration; rejected.
- **Functional vendor-parity tests (`H2/Postgres/SqlServerInferenceChainedCiliumFunctionalTest`)**: feature has no SQL behavior; vendor parity is irrelevant. Adding them would burn test runtime for zero coverage gain. Skipped.

---

## R-007 — Existing chained deployments deployed pre-feature: migration story

**Decision**: No migration is required. Spec 021 shipped immediately before this feature, and no production cluster runs chained inference deployments with Cilium enforcement today (such a deployment would be stuck in `PENDING` per User Story 1's failure mode). On the next deploy of any chained deployment after this feature ships, the augmented policy is regenerated automatically by the same code path that creates the InferenceService. Operators with stuck chained deployments redeploy them to recover.

**Rationale**:
- The Cilium policy is a "disposable resource" tracked via the existing cleanup pipeline (`DisposableResourceManager`); each redeploy recreates it from scratch. No reconciliation loop is needed.
- A one-shot startup reconciler would add complexity (a new `@SchedulerLock`-guarded job) for a transient migration concern affecting ~0 deployments. Rejected.

**Alternatives considered**:
- **Startup reconciler that re-applies the augmented policy to every running chained InferenceService**: out of proportion to the actual migration surface (none, in practice).

---

## R-008 — Logging the chained-mode signal

**Decision**: Add a single structured log line at `INFO` in `AbstractDeploymentManager.createCiliumNetworkPolicy(...)` (or its replacement) reporting `chainedTransformer={true|false}` alongside the existing `serviceNameLabel='...'` / `serviceName='...'` trace lines. No new metric, no new span attribute beyond what `@LogExecution` already provides.

**Rationale**:
- The chained-mode signal is operationally important — a chained deployment whose cilium policy did NOT get the augmentation is a debuggable failure mode. The log line gives ops an immediate signal in `grep chainedTransformer=true` to confirm the policy was emitted in the right shape.
- Reuses existing `@LogExecution` plumbing — no new aspect, no new logger configuration.

**Alternatives considered**:
- **Add a new Micrometer counter `cilium_policy_chained_total`**: low operational value compared to a log line; rejected to keep the scope tight.

---

## R-009 — Ingress fromEndpoint placement: append to existing rule vs. new ingress rule

**Decision**: Append the same-`InferenceService` matchLabel entry to the **existing** ingress rule's `fromEndpoints` list (the one that already contains istio-ingressgateway / activator / autoscaler). Do **not** create a new ingress rule.

**Rationale**:
- Matches the reference YAML: the example shows the same-InferenceService entry as the fourth entry in the existing `fromEndpoints` list, not as a separate ingress block.
- Semantically equivalent: Cilium ingress rules with the same `toPorts` constraint combine via OR on `fromEndpoints`. Adding a new ingress rule with only the chained entry and no port constraint would actually be **more** permissive (would admit traffic on any port from the matched source). Appending to the existing list keeps the port-constraint coupling intact.

**Alternatives considered**:
- **Separate ingress rule for chained-mode source**: more permissive than intended; rejected on security grounds.
