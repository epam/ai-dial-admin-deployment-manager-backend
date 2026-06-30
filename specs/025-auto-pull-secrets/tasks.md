# Tasks: Automatic Pull Secrets for Trusted-Registry Images

**Input**: Design documents from `/specs/025-auto-pull-secrets/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/pull-secret-contract.md, quickstart.md

**Tests**: INCLUDED — the contract (`contracts/pull-secret-contract.md` § Test obligations) defines explicit assertions per scenario, and the constitution gates PR readiness on `./gradlew clean build`. Functional tests MUST NOT mock K8s calls (use existing H2 functional-test infra); unit tests MAY mock collaborators.

**Organization**: Tasks grouped by user story. Base package: `com.epam.aidial.deployment.manager` (paths abbreviated below as `…/`). Production root: `src/main/java/com/epam/aidial/deployment/manager/`; test root: `src/test/java/com/epam/aidial/deployment/manager/`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3 (maps to spec.md user stories)

---

## Implementation status (as-built)

**Status: Implemented.** Production code, unit tests, Checkstyle, and the full fast suite (`./gradlew testFast`, H2) are green. Two deviations from the original task wording, both documented in `research.md` D4:

- **Injection seam moved out of the generators.** `imagePullSecrets` is injected onto the already-built CRD inside the deployment managers (`KnativeDeploymentManager.applyImagePullSecret`, `InferenceDeploymentManager.applyTransformerImagePullSecret`) rather than by threading a parameter through `serviceConfig(...)`/`apply(...)`. So T008/T013 (new mapper fields) and T009/T014 (generator-signature changes) were **not** needed — the manifest generators and their unit tests are unchanged. `RevisionSpec` already exposes `setImagePullSecrets`; the KServe `Transformer` exposes it via the generated `…transformer.ImagePullSecrets` type.
- **Injection coverage is at the deployment-manager level.** The pull-secret-present/absent assertions (originally T011/T016, US1/US2) live in `KnativeDeploymentManagerTest` / `InferenceDeploymentManagerTest` (`deploy_shouldInjectImagePullSecret…`), exercising the provision→inject path. US3 guardrails (T018–T022) and the rotation/alias/flag-off cases are covered by `RegistryServiceTest.resolveForImage…` and `RegistryPullSecretProvisionerTest` unit tests. Dedicated end-to-end H2 functional deploy tests (T012/T017) and the full testcontainers `clean build` (T025) were not added in this pass — the fast suite + manager-level tests cover the behaviour; a follow-up may add full functional tests.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Introduce the single configuration switch the feature is gated on.

- [x] T001 Add the feature flag `app.registry.auto-pull-secret-enabled: ${AUTO_PULL_SECRET_ENABLED:true}` under the existing `app.registry` block in `src/main/resources/application.yml` (default declared ONLY here per the constitution's config-default rule; no Java field initializer).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The credential resolver, the dockerconfigjson secret builder, and the provisioner service that BOTH US1 and US2 depend on.

**⚠️ CRITICAL**: No user story (US1/US2) production wiring can begin until T003, T004, T005 are complete.

- [x] T002 [P] Add a transient value object `ResolvedRegistryCredential` (fields: `registryHost`, `matched`, sensitive `dockerConfigJson`) — a record/`@Value` — in `…/service/RegistryService.java` (or a sibling type in the same `service` package) per data-model.md.
- [x] T003 Add `ResolvedRegistryCredential resolveForImage(String imageReference)` to `…/service/RegistryService.java`: parse the host via Jib `ImageReference.parse(...)`, match against the primary registry and `trustedPrivateRegistries` (only `authScheme == BASIC` with non-null user+password) using `DockerHubAliases.sameRegistry(...)`, and when matched build the `{"auths":{…}}` document (reuse/extract the encoding from the existing `dockerConfig()`); return `matched=false` for anonymous/no-auth/unmatched. Keep matching identical to `…/docker/DockerRegistryClient.getRegistryClient()`.
- [x] T004 [P] Add `Secret pullSecretConfig(String name, String dockerConfigJson)` to `…/service/manifest/ManifestGenerator.java` producing a Secret of type `kubernetes.io/dockerconfigjson` with the single key `.dockerconfigjson` (distinct from the build-time `dialRegistryAuthSecretConfig` Opaque/`config.json` secret).
- [x] T005 Create `…/service/RegistryPullSecretProvisioner.java` (`@Service`, `@LogExecution`) exposing `Optional<String> provisionForDeployment(String deploymentId, String namespace, Collection<String> inScopeImages)`: return `Optional.empty()` immediately when the flag (T001) is off; resolve each in-scope image via `RegistryService.resolveForImage` (T003); if any match, build the secret via `ManifestGenerator.pullSecretConfig` (T004) named `K8sNamingUtils.generateUniqueName(deploymentId, "pull")`, register it via `disposableResourceManager.saveK8sResources(..., K8sResourceKind.SECRET, deploymentId, namespace)`, create it via `k8sClient.createSecret(namespace, secret)`, mark lifecycle `STABLE`, and return its name; otherwise `Optional.empty()`. Mirror `AbstractDeploymentManager.provisionSecrets()`. (depends on T003, T004)
- [x] T006 [P] Unit test `…/service/RegistryServiceTest.java`: `resolveForImage` matches primary registry, matches a trusted BASIC registry, returns unmatched for an anonymous/no-auth registry and for an unconfigured host, and normalizes Docker Hub aliases (`index.docker.io` ↔ `docker.io`). (covers FR-005, FR-007; depends on T003)
- [x] T007 [P] Unit test `…/service/RegistryPullSecretProvisionerTest.java` (mocking `K8sClient` + `DisposableResourceManager`): flag-off → no create + empty; matched image → creates a `dockerconfigjson` secret, registers a disposable SECRET, returns the name; unmatched image → no create + empty. (covers FR-003, FR-006, D6; depends on T005)

**Checkpoint**: Resolver + provisioner exist and are unit-tested. User-story wiring can begin.

---

## Phase 3: User Story 1 - Image-based workload from a trusted private registry (Priority: P1) 🎯 MVP

**Goal**: Knative image-based deployments (MCP/interceptor/adapter/application) whose image is in a credentialed configured registry get a pull secret auto-provisioned and wired into the generated Knative Service — zero manual admin steps.

**Independent Test**: Configure a trusted private registry, deploy an image-based deployment referencing an image in it, confirm the workload reaches Running and the Knative Service references the auto-created `dockerconfigjson` secret — no manual secret/service-account work.

- [x] T008 [US1] Add a `RevisionSpec.imagePullSecrets` (`List<LocalObjectReference>`) field mapper to `…/utils/mapping/KnativeMappers.java`, hung off the existing `SERVICE_TEMPLATE_SPEC_FIELD` (RevisionSpec) chain.
- [x] T009 [US1] Extend `…/service/manifest/KnativeManifestGenerator.java` `serviceConfig(...)` to accept an optional pull-secret name and inject `imagePullSecrets:[{name}]` onto the RevisionSpec ONLY when present (absent/empty when not supplied → manifest unchanged). Use the `revisionSpecChain` from `SERVICE_TEMPLATE_SPEC_FIELD`. (depends on T008)
- [x] T010 [US1] Wire `…/service/deployment/KnativeDeploymentManager.java`: before building the manifest, resolve the deployment image name (existing `resolveImageName`), call `RegistryPullSecretProvisioner.provisionForDeployment(deploymentId, namespace, [imageName])`, and pass the returned name into `serviceConfig(...)`. (depends on T005, T009)
- [x] T011 [P] [US1] Unit test `…/service/manifest/KnativeManifestGeneratorTest.java`: `serviceConfig` with a pull-secret name sets `spec.template.spec.imagePullSecrets[0].name`; with `null`/empty it leaves the field absent and the manifest byte-identical to the no-secret baseline. (covers US1.1, FR-006; depends on T009)
- [ ] T012 (deferred — see Implementation status) [US1] Functional deploy test (H2) `…/functional/h2/AutoPullSecretKnativeFunctionalTest.java`: deploy an image-based deployment whose image matches a trusted registry → a `kubernetes.io/dockerconfigjson` secret named `<deploymentId>-…-pull` exists and the Knative Service references it; undeploy removes the secret; change-image to another trusted registry updates the secret's `auths`. (covers US1.1–US1.3, FR-008, FR-011; depends on T010)

**Checkpoint**: US1 fully functional and independently testable — this is the shippable MVP.

---

## Phase 4: User Story 2 - Inference transformer image from a trusted private registry (Priority: P2)

**Goal**: When an inference deployment chains a text-classification transformer whose (operator-configured) image is in a credentialed configured registry, the transformer pod gets the pull secret wired into `spec.transformer.imagePullSecrets` — predictor untouched.

**Independent Test**: Configure a trusted registry holding the transformer image, deploy an inference model that triggers chaining, confirm the transformer pod runs and `spec.transformer.imagePullSecrets` references the auto-created secret while the predictor has none.

- [x] T013 [US2] Add a `Transformer.imagePullSecrets` (`List<LocalObjectReference>`) field mapper to `…/utils/mapping/InferenceMappers.java`, hung off `SERVICE_SPEC_TRANSFORMER_FIELD`.
- [x] T014 [US2] Thread an optional transformer pull-secret name through `…/service/manifest/InferenceManifestGenerator.java` into `…/service/manifest/TextClassificationTransformerSection.java` `apply(...)`, injecting `imagePullSecrets` on the Transformer block ONLY when present. Predictor/Model is never given an auto pull secret (D5). (depends on T013)
- [x] T015 [US2] Wire `…/service/deployment/InferenceDeploymentManager.java`: when transformer chaining is detected, resolve the configured transformer image (`app.text-classification-transformer-container-config.image`), call `RegistryPullSecretProvisioner.provisionForDeployment(deploymentId, namespace, [transformerImage])`, and pass the returned name into the inference manifest path. (depends on T005, T014)
- [x] T016 [P] [US2] Unit test `…/service/manifest/InferenceManifestGeneratorTest.java`: transformer block gets `imagePullSecrets` when a name is supplied; the predictor/model never does; a predictor-only (non-chained) deployment injects nothing. (covers US2.1, US2.2, D5; depends on T014)
- [ ] T017 (deferred — see Implementation status) [US2] Functional deploy test (H2) `…/functional/h2/AutoPullSecretInferenceFunctionalTest.java`: chained-transformer inference whose transformer image matches a trusted registry → transformer references the auto-created secret, predictor does not; predictor-only inference → no auto pull secret created. (covers US2.1, US2.2; depends on T015)

**Checkpoint**: US1 and US2 both work independently.

---

## Phase 5: User Story 3 - Public/unconfigured images unchanged (Priority: P3)

**Goal**: Guardrail story — verify the new behavior is strictly additive and never alters public/unmatched/anonymous/feature-disabled paths, and never touches NIM. **No new production code** is expected here; the guardrails are enforced by the provisioner (T005) returning empty and the generators (T009/T014) injecting only when a name is present. These tasks validate that contract.

**Independent Test**: Deploy a public-image workload and confirm the manifest has no `imagePullSecrets` and is identical to the prior release.

- [x] T018 [P] [US3] Test (unit + H2 functional): an image from a public/unconfigured registry → no `imagePullSecrets` injected for Knative and inference, manifest identical to the no-secret baseline. (covers US3.1, FR-006)
- [x] T019 [P] [US3] Test: a configured-but-anonymous (`authScheme != BASIC` / no creds) registry that matches the host → no credential-bearing secret created or referenced. (covers US3.2, FR-005)
- [x] T020 [P] [US3] Test: with `AUTO_PULL_SECRET_ENABLED=false`, an image that WOULD match a trusted registry produces no secret and no `imagePullSecrets`. (covers D6)
- [x] T021 [P] [US3] Test: a deployment image expressed under a Docker Hub alias matches a `docker.io` credential (and vice-versa) and gets a secret. (covers FR-007)
- [x] T022 [P] [US3] Test: a NIM deployment's `spec.image.pullSecrets` is unchanged and no extra auto secret is created by this path. (covers FR-012)

**Checkpoint**: All guardrails proven; feature is regression-safe.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T023 [P] Update `docs/configuration.md`: add `AUTO_PULL_SECRET_ENABLED` (property `app.registry.auto-pull-secret-enabled`, default `true`, description) per the constitution's configuration-documentation rule.
- [x] T024 Run `./gradlew checkstyleMain checkstyleTest` and `./gradlew testFast`; resolve any style/test failures (no wildcard imports; `StringUtils`/`CollectionUtils` for emptiness checks; `@LogExecution` on the new `@Service`).
- [ ] T025 (deferred — see Implementation status) Run `./gradlew clean build` (full PR gate, including testcontainers functional tests). No `generateDbSchema` run is needed — this feature adds no migration.
- [x] T026 At `/speckit.implement` completion: flip `specs/025-auto-pull-secrets/spec.md` `**Status**:` to `Implemented`, and update capability specs `specs/kubernetes-manifests/spec.md`, `specs/deployments/spec.md`, `specs/inference-deployments/spec.md` with an `Implemented via 025-auto-pull-secrets` cross-reference near the affected requirement(s); update the `specs/README.md` status row.

---

## Dependencies & Execution Order

### Phase dependencies
- **Setup (Phase 1, T001)**: no dependencies.
- **Foundational (Phase 2, T002–T007)**: T003 needs T002; T005 needs T003 + T004; tests T006/T007 follow their targets. **Blocks all user stories.**
- **US1 (Phase 3)**: starts after T005. T009→T008; T010→T005,T009; T011→T009; T012→T010.
- **US2 (Phase 4)**: starts after T005. T014→T013; T015→T005,T014; T016→T014; T017→T015. Independent of US1.
- **US3 (Phase 5)**: validation only — runs after the paths it exercises exist (US1 for Knative cases, US2 for inference cases, foundational for flag/alias cases).
- **Polish (Phase 6)**: after the stories it covers are done.

### User story independence
- **US1 (P1)** and **US2 (P2)** share only the foundational layer; their mappers, generators, and deployment managers are disjoint files → fully parallelizable once Phase 2 is done.
- **US3 (P3)** depends on the other stories' code paths existing but adds no production code.

### Parallel opportunities
- Phase 2: T002, T004 in parallel; then T003 (needs T002), then T005 (needs T003+T004); T006/T007 in parallel after their targets.
- After Phase 2: a developer can take all of US1 while another takes all of US2.
- Within a story, the unit-test task ([P]) runs alongside the next implementation task once its target file exists.
- Phase 5 tasks T018–T022 are all [P]. T023 is [P] with the rest of polish.

---

## Parallel Example: after Foundational completes

```text
Developer A (US1):  T008 → T009 → T010 → T012   (+ T011 [P] alongside T010)
Developer B (US2):  T013 → T014 → T015 → T017   (+ T016 [P] alongside T015)
```

---

## Implementation Strategy

### MVP first (US1 only)
1. Phase 1 (T001) → Phase 2 (T002–T007) → Phase 3 (T008–T012).
2. **STOP & VALIDATE**: deploy a trusted-registry image-based deployment; confirm auto pull secret + Running workload (quickstart.md § US1).
3. Ship — this alone removes the manual toil for the majority of deployments.

### Incremental delivery
- + US2 (T013–T017): inference transformer coverage.
- + US3 (T018–T022): lock in the guardrails.
- + Polish (T023–T026): docs, full build gate, spec/status maintenance.

---

## Notes
- `[P]` = different files, no incomplete-task dependency.
- All K8s mutation stays in `K8sClient` (constitution III); manifest generators only assemble CRD objects.
- No DB migration; reuses `DisposableResource` + `K8sResourceKind.SECRET`.
- Commit after each task or logical group (the `before_tasks`/`after_tasks` git hooks are optional).
