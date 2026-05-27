---
description: "Task breakdown for 022-transformer-cilium-policies"
---

# Tasks: Cilium Network Policy Adjustments for Chained Predictor + Transformer

**Input**: Design documents from `/specs/022-transformer-cilium-policies/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅ (no `contracts/` — feature has no API surface)

**Tests**: Required. Unit-test coverage for `CiliumNetworkPolicyCreator` chained-mode emission and `InferenceDeploymentManager` signal propagation is the only viable verification — there's no SQL behavior, so no vendor-parity functional suites. Baseline byte-equivalence (SC-002) is asserted by re-running existing Knative / NIM / JobRunner tests unchanged.

**Organization**: Tasks are grouped by user story. The MVP is US1 alone; US2 is verification of the baseline-equivalence promise; US3 covers the domain-whitelist-update path so chained augmentation survives `allowedDomains` edits.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelizable (different files, no dependency on incomplete tasks)
- **[Story]**: US1 / US2 / US3 — maps to user stories in spec.md

## Path Conventions

Single Gradle project. Sources under `src/main/java/com/epam/aidial/deployment/manager/`, tests under `src/test/java/...`.

---

## Phase 1: Setup

No tasks — single Gradle project already exists; no new dependencies, no new properties, no new build wiring required (plan.md §Technical Context).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Introduce the type-level vehicle for the chained signal (`PreparedServiceSpec<S>`) and cascade the signature change through every override of `prepareServiceSpec`. This phase MUST land before any user story phase because it changes the abstract method's return type — every deployment manager has to compile.

**⚠️ CRITICAL**: At the end of Phase 2, the codebase compiles and ALL existing tests pass with `chainedTransformer=false` everywhere. No behavior change is visible to operators yet.

- [X] T001 Create the record `PreparedServiceSpec<S>(S spec, boolean chainedTransformer)` with static factories `unchained(S)` and `chained(S)` in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/PreparedServiceSpec.java`. Javadoc references spec FR-005 / R-001 so future readers understand why this exists.
- [X] T002 Change the abstract method signature in `src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractDeploymentManager.java`: `protected abstract S prepareServiceSpec(D deployment);` → `protected abstract PreparedServiceSpec<S> prepareServiceSpec(D deployment);`. Update the two call sites in `deploy(...)` (line 130) and the recreation path (line 218) to capture the wrapper into `var prepared = prepareServiceSpec(deployment);` and pass `prepared.spec()` to existing downstream calls; keep `prepared.chainedTransformer()` in a local variable for use by T010.
- [X] T003 [P] Update `src/main/java/com/epam/aidial/deployment/manager/service/deployment/KnativeDeploymentManager.java` — wrap the existing `prepareServiceSpec(...)` return in `PreparedServiceSpec.unchained(...)`. No other behavior change.
- [X] T004 [P] Update `src/main/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManager.java` — wrap the existing `prepareServiceSpec(...)` return in `PreparedServiceSpec.unchained(...)`. No other behavior change.
- [X] T005 Update `src/main/java/com/epam/aidial/deployment/manager/service/deployment/InferenceDeploymentManager.java` — wrap the existing `prepareServiceSpec(...)` return in `PreparedServiceSpec.unchained(...)` for now. The chained-mode branch lands in T008; this commit must not yet flip the flag, so US2's byte-equivalence holds across Phase 2.
- [X] T006 Extend `src/main/java/com/epam/aidial/deployment/manager/service/pipeline/specification/CiliumNetworkPolicyCreator.java` with the new 6-arg method `create(namespace, matchLabelName, matchLabelValue, allowedDomains, ports, chainedTransformer)`. **At this task, the 6-arg implementation simply delegates to the existing 5-arg path** — it does NOT yet emit the chained-mode rules. The 5-arg `create(...)` is rewritten to delegate to the new 6-arg form with `chainedTransformer=false`. This split lets US1's tests in T011 land cleanly when the augmentation is added in T008.

**Checkpoint**: `./gradlew testFast` passes. No new behavior visible. Phase 3 (User Story 1) can begin.

---

## Phase 3: User Story 1 - Chained inference deployment gets an intra-InferenceService Cilium policy (Priority: P1) 🎯 MVP

**Goal**: For any inference deployment whose `prepareServiceSpec` produces a chained `InferenceService`, the regenerated `CiliumNetworkPolicy` carries the three augmentations from spec FR-001 (intra-cluster egress block, same-`InferenceService` ingress `fromEndpoint`, `8080/TCP` deduped into ingress `toPorts`).

**Independent Test** (from spec.md): On a Cilium-enabled cluster, deploy `MoritzLaurer/DeBERTa-v3-base-mnli-fever-anli` (or any HF text-classification model). `kubectl get ciliumnetworkpolicy <name> -o yaml` shows the three augmentations; `curl <deployment-url>` returns a classification response — proving the transformer→predictor hop traverses the policy.

- [X] T007 [US1] In `src/main/java/com/epam/aidial/deployment/manager/service/pipeline/specification/CiliumNetworkPolicyCreator.java`, implement the chained-mode emission inside the new 6-arg `create(...)`. When `chainedTransformer=true`:
  - Add a new `Egress` to the existing egress list with `toEndpoints` carrying three separate `ToEndpoints` entries (matchLabels for `serving.kserve.io/inferenceservice: <serviceName>`, `k8s:io.kubernetes.pod.namespace: istio-system`, `k8s:io.kubernetes.pod.namespace: knative-serving`). Do **not** set `toPorts` on this block (per spec R-004).
  - In `createIngress(...)`, append a fourth `FromEndpoints` entry to the existing `fromEndpoints` list (matchLabel `serving.kserve.io/inferenceservice: <serviceName>`).
  - In `createIngress(...)`, append `8080/TCP` to the ingress ports list, deduplicating by `(port-string, protocol)` against the existing entries (per spec R-003). Introduce a `CHAINED_INGRESS_PORT_8080 = "8080"` constant alongside the existing `INGRESS_PORT_8012` / `INGRESS_PORT_8022` constants.
  Preserve `@LogExecution` on the class; no new logging needed at the creator level.
- [X] T008 [US1] In `src/main/java/com/epam/aidial/deployment/manager/service/deployment/InferenceDeploymentManager.java`, change `prepareServiceSpec(...)` to return `PreparedServiceSpec.chained(spec)` when manifest generation emitted a transformer block (read directly from the spec-021 `InferenceTaskDetectionResult` already in scope — do **not** re-inspect the built `InferenceService` per spec FR-005), and `.unchained(spec)` otherwise.
- [X] T009 [US1] In `src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractDeploymentManager.java`, thread the chained flag captured in T002 into the `createCiliumNetworkPolicy(...)` call: change the private method's signature to `createCiliumNetworkPolicy(String groupId, List<String> allowedDomains, Set<Integer> ports, String name, boolean chainedTransformer)` and pass `chainedTransformer` to `ciliumNetworkPolicyCreator.create(...)`. Add a single structured log line at `INFO` reporting `chainedTransformer=...` (per research R-008) — extend the existing `log.trace("createCiliumNetworkPolicy. serviceNameLabel='{}', serviceName='{}'", ...)` line.
- [X] T010 [P] [US1] In `src/test/java/com/epam/aidial/deployment/manager/service/pipeline/specification/CiliumNetworkPolicyCreatorTest.java` (create the file if it does not yet exist), add unit tests:
  - `shouldEmitChainedEgressBlock_whenChainedTrue` — asserts the new egress entry's three `toEndpoints` entries and absence of `toPorts`.
  - `shouldAppendSameInferenceServiceFromEndpoint_whenChainedTrue` — asserts ingress rule 1 has four `fromEndpoints` entries, the fourth being the same-`InferenceService` matchLabel.
  - `shouldAppend8080TcpToIngressPorts_whenChainedTrue` — asserts `8080/TCP` is present in the ingress `toPorts` list.
  - `shouldDeduplicatePort8080_whenAlreadyResolvedFromContainerPort` — call with `ports=Set.of(8080)` and `chainedTransformer=true`; assert exactly one `8080/TCP` entry.
  - `shouldOmitKubeDnsEgress_whenAllowedDomainsEmpty_andChainedTrue` — assert chained egress block present, kube-dns egress block absent (per spec edge case + clarification Q3).
  Use AssertJ; no YAML snapshot files.
- [X] T011 [P] [US1] In `src/test/java/com/epam/aidial/deployment/manager/service/deployment/InferenceDeploymentManagerTest.java`, add:
  - `shouldPassChainedFlagToCiliumPolicyCreator_whenTransformerEmitted` — wire detection to return `TEXT_CLASSIFICATION` with valid id2label; verify `ciliumNetworkPolicyCreator.create(...)` is invoked with `chainedTransformer=true`.
  - `shouldPassUnchainedFlag_whenPredictorOnly` — detection returns `NONE`; verify `chainedTransformer=false`.
  - `shouldPassUnchainedFlag_whenNonHuggingfaceSource` — source is not `huggingface`; verify `chainedTransformer=false` and no second HF Hub fetch occurred (assert the HF client mock was never called from the cilium path).

**Checkpoint**: Phase 3 complete — a fresh chained deployment on a Cilium-enabled cluster reaches `RUNNING` and serves classification requests. This is the deliverable MVP slice.

---

## Phase 4: User Story 2 - Predictor-only and non-inference deployments keep the existing Cilium policy unchanged (Priority: P1)

**Goal**: Knative, NIM, image-build pipeline, and predictor-only inference deployments produce byte-identical Cilium policies to the pre-feature release. Spec SC-002 enforces this.

**Independent Test**: Re-run the existing `KnativeDeploymentManagerTest`, `NimDeploymentManagerTest`, `JobRunnerTest`, and `InferenceDeploymentManagerTest` cases for predictor-only models — all pass without modification. Add one explicit parity test that asserts the new 6-arg `create(..., chainedTransformer=false)` produces an object equal to the 5-arg `create(...)`.

- [X] T012 [P] [US2] In `src/test/java/com/epam/aidial/deployment/manager/service/pipeline/specification/CiliumNetworkPolicyCreatorTest.java`, add `shouldProduceIdenticalPolicyTo5ArgOverload_whenChainedFalse`. **Already landed as part of T010** — see `CiliumNetworkPolicyCreatorTest:137`, asserting deep equality between 5-arg and 6-arg(chained=false) outputs.
- [X] T013 [P] [US2] Verify the 5-arg `create(...)` overload is still the call shape in `KnativeDeploymentManagerTest`, `NimDeploymentManagerTest`, and `JobRunnerTest`. **Empirically confirmed** — every `./gradlew testFast` run since Phase 2 (4 of them: post-Phase 2, post-Phase 3 chained branch, post-Phase 3 tests, post-Phase 5) has shown these three test classes passing unmodified. T013's protective property — "failing tests indicate an accidental call-site regression" — is intact.
- [X] T014 [P] [US2] Add `shouldEmitBaselinePolicy_whenDetectionReturnsNone`. **Already covered** by two tests landed in Phase 3: `shouldNotEmitChainedAdditions_whenChainedFalse` (`CiliumNetworkPolicyCreatorTest:120`) asserts the policy shape carries no chained-mode additions when `chainedTransformer=false`; `deploy_shouldPassUnchainedFlag_whenPredictorOnly` (`InferenceDeploymentManagerTest:590`) asserts the inference path passes `chained=false` to the creator when detection returns `NONE`. Combined, they back the spec FR-004 / SC-002 invariant. (The non-HF source case is a separate path — spec 021 makes `prepareServiceSpec` throw `IllegalArgumentException` before any cilium logic is reached — so no cilium-policy assertion applies there.)

**Checkpoint**: Phase 4 complete — SC-002 is mechanically defended by a parity test and three regression tests. Reviewers see a single explicit invariant rather than implicit "trust the existing tests."

---

## Phase 5: User Story 3 - Topology transitions and domain-whitelist updates preserve / regenerate correctly (Priority: P2)

**Goal**: (a) When an operator changes `modelName` and redeploys, the Cilium policy regenerates with the new topology (chained ↔ predictor-only). (b) When an operator updates `allowedDomains` on a running chained deployment via the existing `updateCiliumNetworkPolicy(id)` path, the chained-mode augmentation is preserved (spec FR-007 acceptance scenario 3).

**Independent Test**: Create and deploy a chained deployment; verify augmented policy. Change modelName to a non-classification model; redeploy; verify baseline policy. Reverse. Separately: on a chained deployment, update `allowedDomains`; verify the policy still has the chained-mode rules.

- [X] T015 [US3] In `src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractDeploymentManager.java`, update `updateCiliumNetworkPolicy(String id)` (the `@Transactional` public method at line 786) to re-invoke `prepareServiceSpec(deployment)` solely to read `prepared.chainedTransformer()`, then thread it into the private `updateCiliumNetworkPolicy(...)` (line 798). Change the private method's signature to accept the boolean and pass it to `ciliumNetworkPolicyCreator.create(...)`. Add a `chainedTransformer=...` field to the existing `log.trace("updateCiliumNetworkPolicy. serviceNameLabel='{}', serviceName='{}', cnpName='{}'", ...)` line. NOTE: the regenerated `prepared.spec()` is intentionally discarded — only the boolean is needed; the InferenceService K8s resource itself is not re-applied here.
- [X] T016 [P] [US3] In `src/test/java/com/epam/aidial/deployment/manager/service/deployment/InferenceDeploymentManagerTest.java`, add `shouldPreserveChainedAugmentation_onAllowedDomainsUpdate` — invoke `updateCiliumNetworkPolicy(id)` on a chained deployment fixture; verify `ciliumNetworkPolicyCreator.create(...)` is called with `chainedTransformer=true` and that `prepareServiceSpec` was re-invoked.
- [X] T017 [P] [US3] In `src/test/java/com/epam/aidial/deployment/manager/service/deployment/InferenceDeploymentManagerTest.java`, add `shouldRegeneratePolicyOnTopologyFlip` — covers (a) chained-then-predictor-only redeploy: subsequent `deploy(...)` invocation passes `chainedTransformer=false`; (b) predictor-only-then-chained redeploy: subsequent `deploy(...)` passes `chainedTransformer=true`. Asserts no stale augmentation lingers across topology flips.

**Checkpoint**: Phase 5 complete — every spec acceptance scenario has at least one test backing it. Topology transitions and domain-whitelist edits are covered.

---

## Phase 6: Polish & Cross-Cutting

- [X] T018 [P] `./gradlew checkstyleMain checkstyleTest` clean.
- [X] T019 [P] `./gradlew test` (full suite incl. Postgres / SQL Server testcontainers) green — BUILD SUCCESSFUL in 7m 7s, no inference-deployment functional regressions.
- [X] T020 [P] Update `specs/inference-deployments/spec.md`: added cilium augmentation note + cross-link to `022-transformer-cilium-policies` in Implementation Notes, plus a `PreparedServiceSpec` signal-threading note.
- [X] T021 [P] Update `specs/kubernetes-manifests/spec.md`: added a chained-mode cilium policy sub-bullet to the existing "Chained predictor + transformer" requirement, a new "Chained-mode Cilium policy augmentation" scenario, and extended the Status line to reference both `021-inference-task-transformer` and `022-transformer-cilium-policies`.
- [X] T022 Flipped `**Status**:` in `specs/022-transformer-cilium-policies/spec.md` from `Draft` to `Implemented`.
- [X] T023 Added a row for `022-transformer-cilium-policies` to the in-flight features table in `specs/README.md`.
- [X] T024 Deleted `specs/022-transformer-cilium-policies/checklists/`.

---

## Dependencies

```text
Phase 2 (T001 → T002 → T003,T004,T005 [P] → T006)
  │
  ▼
Phase 3 (T007 → T008 → T009 → T010,T011 [P])     ← MVP slice (US1)
  │
  ▼
Phase 4 (T012,T013,T014 [P])                      ← byte-equivalence canary (US2)
  │
  ▼
Phase 5 (T015 → T016,T017 [P])                    ← topology & domain-whitelist (US3)
  │
  ▼
Phase 6 (T018,T019,T020,T021 [P] → T022 → T023 → T024)
```

- T002 blocks T003 / T004 / T005 (they need the new abstract signature). T003 / T004 / T005 / T006 can land in any interleaving once T002 is committed.
- T007 / T008 / T009 are sequential within US1 (each builds on the previous: T007 enables the chained branch in the creator, T008 produces the signal, T009 threads it).
- T010 and T011 are parallelizable — they touch different test classes.
- Phase 4 tasks are fully parallel — they touch three different test classes and assert independent invariants.
- Phase 5 must wait for US1 because the chained-mode emission needs to exist for the topology-flip and domain-whitelist tests to assert anything meaningful.
- Polish phase: T020, T021 are independent of each other and the code phases (they touch capability specs); they can land in the same commit as the implementation or in a follow-up doc-only commit. T022 / T023 / T024 are sequential at the very end.

## Parallel execution examples

After T002 lands:
```bash
# Three concurrent edits, three different files:
T003: edit KnativeDeploymentManager.java   (wrap in .unchained)
T004: edit NimDeploymentManager.java        (wrap in .unchained)
T005: edit InferenceDeploymentManager.java  (wrap in .unchained — chained logic in T009)
```

After T009 lands:
```bash
# Two test-only commits, two different files:
T010: edit CiliumNetworkPolicyCreatorTest.java
T011: edit InferenceDeploymentManagerTest.java
```

## Implementation strategy

- **MVP scope = Phase 2 + Phase 3** (US1 only). At the end of US1 the chained data path works end-to-end on a Cilium-enabled cluster; everything else is verification, edge-case coverage, and docs.
- **Incremental delivery**: Phase 2 is a behavior-preserving refactor; it can land independently and be merged before any of the user stories ship. Phase 3 is the focused user-visible delta. Phases 4 / 5 add safety nets without changing the user-visible behavior.
- **Rollback story**: if the change is reverted, the 5-arg `create(...)` overload was preserved unchanged throughout — every non-inference call site is byte-equivalent and unaffected.

## Format validation

All 17 implementation tasks (T001–T017) and 7 polish tasks (T018–T024) follow the required format: `- [ ] [TaskID] [P?] [Story?] Description with file path`. Story labels appear only on Phase 3–5 tasks. File paths are absolute relative to the project root.
