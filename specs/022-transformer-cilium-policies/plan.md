# Implementation Plan: Cilium Network Policy Adjustments for Chained Predictor + Transformer

**Branch**: `022-transformer-cilium-policies` | **Date**: 2026-05-27 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/022-transformer-cilium-policies/spec.md`

## Summary

When `InferenceDeploymentManager.prepareServiceSpec(...)` produces a chained `InferenceService` (predictor + transformer per spec 021), the chained boolean SHALL flow alongside the manifest into `AbstractDeploymentManager.deploy(...)` and be passed into the `CiliumNetworkPolicyCreator.create(...)` call so the generated `CiliumNetworkPolicy` carries three additional rules: (1) an egress block to same-`InferenceService` pods + `istio-system` + `knative-serving` namespaces, (2) a same-`InferenceService` entry appended to the existing ingress `fromEndpoints` rule, and (3) `8080/TCP` deduplicated into the ingress `toPorts` list. Knative / NIM / image-build code paths continue to call the existing unchained policy shape — verified by byte-equivalence assertions against today's policy output.

The implementation refactors the abstract `prepareServiceSpec` return contract to carry a side-channel flag (a small record `PreparedServiceSpec<S>{ S spec; boolean chainedTransformer }`), so the chained signal never leaks back through manifest re-parsing or a persisted column (per [spec FR-005](./spec.md#functional-requirements)). Inference is the only manager that ever returns `chainedTransformer=true`; every other manager returns `false` via the default factory method.

## Technical Context

**Language/Version**: Java 21 (constitution §Tech Stack)
**Primary Dependencies**: Spring Boot 3.5.10, Lombok 8.10 plugin, Fabric8-generated `io.cilium.v2.*` model types (already in the project; data classes only — no K8s CRUD added in service layer), Fabric8 KServe generated types via existing `service/manifest/` code path.
**Storage**: N/A — feature introduces no schema changes, no persisted columns, no Flyway migration. The chained signal is transient.
**Testing**: JUnit 5 + AssertJ; `./gradlew testFast` (H2) during dev, `./gradlew test` (full suite incl. testcontainers) for PR gate; ArchUnit `ArchitectureTest` for layering.
**Target Platform**: Kubernetes cluster running KServe (`app.kserve.enabled=true`) **with Cilium CNI and `app.cilium-network-policies-enabled=true`**. The feature has no effect when the master switch is off.
**Project Type**: Web service (Spring Boot backend, single Gradle project).
**Performance Goals**: No measurable change — policy generation is in-memory object construction on a path that runs once per deploy.
**Constraints**: Constitution rules apply unchanged — strict layering (cilium creator stays in `service/pipeline/specification/`; K8s CRUD remains in `kubernetes/K8sClient`), `@LogExecution` preserved, 180-char lines, Apache Commons emptiness helpers, no wildcard imports. **No new configuration property, env var, DTO, REST endpoint, or migration is introduced (per spec SC-005).** Forbidden: inspecting the partial `InferenceService` manifest to recover the chained flag (per spec FR-005).
**Scale/Scope**: Inference deployments are operator-created, low cardinality; the feature touches one creator class, one abstract base class, one inference manager, and adds ~3 unit-test classes plus byte-equivalence baseline tests for the unchanged paths.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Status | Notes |
|---|---|---|
| I. Strict Layered Architecture | ✅ Pass | All changes confined to `service/` (pipeline/specification + deployment). No new `web/` or `dao/` code. K8s CRUD (`k8sClient.createCiliumNetworkPolicy(...)`) is unchanged and continues to be invoked from `service/deployment/` via the existing `kubernetes/` abstraction. |
| II. Transactional Discipline | ✅ Pass | No new `@Transactional` boundaries. The cilium policy apply remains inside the existing `afterCommit` callback in `AbstractDeploymentManager.deploy(...)`. |
| III. Kubernetes Isolation | ✅ Pass | `CiliumNetworkPolicyCreator` builds Fabric8-generated CRD **model types** (`io.cilium.v2.*`) — pure POJOs, no API calls. The actual `createCiliumNetworkPolicy` API call stays in `kubernetes/K8sClient`. No new polling. |
| IV. Observability First | ✅ Pass | `@LogExecution` preserved on `CiliumNetworkPolicyCreator`. No new Spring components introduced — only methods on existing components. |
| V. Security by Configuration | ✅ Pass | The augmentation respects the existing master switch `app.cilium-network-policies-enabled` (per FR-009). No new secret, no new env var. |
| Naming Conventions | ✅ Pass | New record `PreparedServiceSpec<S>` (no naming-table pattern applies to records); no new `*Service` / `*Repository` / `*Controller` classes. |
| Code Style | ✅ Pass | New code follows Google Java Style + 180-char lines; Commons `CollectionUtils` for collection emptiness; no wildcard imports; `var` for obvious local types. |
| API Conventions | ✅ Pass | No new endpoints, no DTO changes, no OpenAPI annotation changes. |
| Testing Conventions | ✅ Pass | New `*Test` unit tests for the chained-mode branches of `CiliumNetworkPolicyCreator` and `InferenceDeploymentManager`. Functional vendor parity tests are **not required** — feature has no SQL behavior. Existing `H2/Postgres/SqlServer` functional suites continue to pass unchanged (verified by SC-002 byte-equivalence). |
| Multi-Vendor Database | ✅ Pass | No migration. No `ddl-auto` changes. |
| Anti-Patterns (1-10) | ✅ Pass | No business logic added to entities, no silent exception swallowing, no generic `Exception` catch, no `@Transactional` on controllers, no K8s API call moved into service layer, no wildcard imports, no `System.out.println`, no hard-coded secrets, no polling, `ddl-auto: validate` preserved. |
| Config docs | ✅ Pass | `docs/configuration.md` unchanged — no new properties. |
| Spec-kit Workflow | ✅ Pass | Numbered spec carries `**Capability**: inference-deployments, kubernetes-manifests` line; on `Implemented` flip, both capability specs will gain an `Implemented via 022-transformer-cilium-policies` cross-reference at the relevant Requirement. No per-feature `checklists/` committed (the `requirements.md` checklist is workflow-internal and will be deleted before the PR per constitution §spec-kit Workflow Rules). |

**Constitution Check verdict**: ✅ All gates pass with no violations. Complexity Tracking is intentionally empty.

## Project Structure

### Documentation (this feature)

```text
specs/022-transformer-cilium-policies/
├── plan.md              # This file
├── research.md          # Phase 0 — design decisions (signal threading, dedup semantics, baseline-equivalence strategy)
├── data-model.md        # Phase 1 — minimal; transient signal only
├── quickstart.md        # Phase 1 — operator-and-developer walkthrough
└── spec.md              # Existing — feature specification
```

No `contracts/` directory — the feature introduces no operator-facing API surface.

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/service/
├── deployment/
│   ├── AbstractDeploymentManager.java
│   │     # Signature change: prepareServiceSpec(D) returns PreparedServiceSpec<S>
│   │     #   instead of raw S. deploy() reads .chainedTransformer() and threads it
│   │     #   into createCiliumNetworkPolicy(...). Other managers default the flag to false.
│   ├── PreparedServiceSpec.java
│   │     # NEW: record PreparedServiceSpec<S>(S spec, boolean chainedTransformer) {
│   │     #   public static <S> PreparedServiceSpec<S> unchained(S spec) { ... }
│   │     # }
│   ├── InferenceDeploymentManager.java
│   │     # prepareServiceSpec(...) now returns PreparedServiceSpec.chained(spec)
│   │     # when TextClassificationTransformerSection emitted a transformer block,
│   │     # unchained(spec) otherwise. The chained boolean comes from the spec-021
│   │     # detection result already computed in this method — no second HF fetch,
│   │     # no manifest re-parse.
│   ├── KnativeDeploymentManager.java         # prepareServiceSpec wraps with .unchained(...)
│   ├── NimDeploymentManager.java             # prepareServiceSpec wraps with .unchained(...)
│   └── AbstractModelDeploymentManager.java   # Pass-through; no behavior change
└── pipeline/specification/
    └── CiliumNetworkPolicyCreator.java
          # New signature variant:
          #   create(namespace, matchLabelName, matchLabelValue, allowedDomains, ports, chainedTransformer)
          # When chainedTransformer=true, additionally emit:
          #   - egress.toEndpoints: [same-InferenceService, istio-system, knative-serving]
          #     (no toPorts → unrestricted ports for intra-cluster control-plane traffic)
          #   - ingress[0].fromEndpoints += same-InferenceService matchLabel
          #   - ingress[1].toPorts += 8080/TCP (deduped against existing port set)
          # Existing 5-arg create(...) kept as a delegate calling the new one with
          # chainedTransformer=false → preserves byte-equivalence for Knative / NIM /
          # JobRunner / non-chained-inference call sites.

src/test/java/com/epam/aidial/deployment/manager/service/
├── deployment/
│   ├── InferenceDeploymentManagerTest.java
│   │     # +shouldPassChainedFlagToCiliumPolicyCreator_whenTransformerEmitted()
│   │     # +shouldPassUnchainedFlag_whenPredictorOnly()
│   │     # +shouldPassUnchainedFlag_whenNonHuggingfaceSource()
│   └── (KnativeDeploymentManagerTest, NimDeploymentManagerTest, JobRunnerTest)
│         # Verify .create(...) is called via the 5-arg overload (chainedTransformer
│         # implicitly false) — confirms call sites unaffected by the new parameter.
└── pipeline/specification/
    └── CiliumNetworkPolicyCreatorTest.java
          # NEW (or extend existing tests):
          # +shouldEmitChainedEgressBlock_whenChainedTrue()
          # +shouldAppendSameInferenceServiceFromEndpoint_whenChainedTrue()
          # +shouldAppend8080TcpToIngressPorts_whenChainedTrue()
          # +shouldDeduplicatePort8080_whenAlreadyResolvedFromContainerPort()
          # +shouldNotEmitChainedAdditions_whenChainedFalse() (baseline byte-equivalence)
          # +shouldOmitKubeDnsEgress_whenAllowedDomainsEmpty_evenInChainedMode (per spec edge-case)

src/test/java/com/epam/aidial/deployment/manager/functional/tests/
└── (existing inference functional tests pick up the new cilium shape automatically
    when manifest generation is chained; no new functional test class is added —
    SC-002 byte-equivalence is asserted in CiliumNetworkPolicyCreatorTest.)
```

**Structure Decision**: Single Gradle project (existing layout). No new top-level directories or subpackages. The new `PreparedServiceSpec<S>` record lives next to the abstract base class it's used by, in `service/deployment/`. The signal-threading refactor touches every deployment manager because `prepareServiceSpec`'s return type changes — but each non-inference site is a one-line wrap with `PreparedServiceSpec.unchained(...)`. The change is type-checked: forgetting to wrap a return is a compile error, not a runtime miss.

## Complexity Tracking

> No constitution violations to justify. Table intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
