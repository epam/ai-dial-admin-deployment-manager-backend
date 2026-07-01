# Phase 0 Research: Automatic Pull Secrets for Trusted-Registry Images

All "NEEDS CLARIFICATION" items from Technical Context are resolved below. Each entry records the decision, the rationale, and the alternatives rejected. Anchors are given as `path:line` against the state of the codebase on branch `025-auto-pull-secrets`.

## D1 — Where does credential resolution live, and how is matching done?

**Decision**: Add credential-resolution-by-image-host to the service layer, reusing the existing matching primitives. A method (on `RegistryService`, or a thin collaborator it owns) takes a fully-qualified image reference, parses its registry host with Jib `ImageReference.parse(...)`, and returns the matching credentialed registry (primary or trusted) or "no match". Matching reuses `DockerHubAliases.sameRegistry(...)` for Docker Hub alias normalization and the exact predicate already used in `DockerRegistryClient.getRegistryClient()`.

**Rationale**: The matching logic already exists and is tested implicitly via image inspection — `DockerRegistryClient.getRegistryClient()` (`docker/DockerRegistryClient.java:111-154`) matches against the primary registry (`registryProperties.getAuth()==BASIC && DockerHubAliases.sameRegistry(...)`) and iterates `trustedPrivateRegistries` checking `authScheme=="BASIC"` plus non-null user/password. Reusing it guarantees the deploy-time match is identical to the inspect-time match, and keeps pure-config logic in `service/` (constitution I/III — no K8s here).

**Alternatives rejected**:
- *Re-implement matching in a manifest generator* — would duplicate alias logic and risk drift; also manifest generators must stay free of credential decisions.
- *Resolve in the `docker/` package* — `docker/` is about registry I/O for inspection; deploy-time provisioning is orchestration that belongs above it.

## D2 — Secret type, content, and builder

**Decision**: Provision a Kubernetes `Secret` of type `kubernetes.io/dockerconfigjson` whose single data key is `.dockerconfigjson`, holding the standard `{"auths": {...}}` document. Add a dedicated builder `ManifestGenerator.pullSecretConfig(name, dockerConfigJson)` rather than reusing `dialRegistryAuthSecretConfig`.

**Rationale**: kubelet only honors `imagePullSecrets` that reference a `kubernetes.io/dockerconfigjson` secret with the `.dockerconfigjson` key. The existing build-time secret produced by `ManifestGenerator.dialRegistryAuthSecretConfig` (`service/manifest/ManifestGenerator.java:29-37`) is an Opaque secret keyed `config.json` mounted into build jobs — wrong type and key for a pull secret. The `{"auths":...}` payload is built by a dedicated **deploy-time** method `RegistryService.dockerConfigForImages(Collection<String>)`, which is **narrowed** to carry credentials ONLY for the configured registries that serve the deployment's own in-scope images (least-privilege). The existing `RegistryService.dockerConfig()` — which aggregates ALL configured registries — is left untouched because the **build pipeline** legitimately needs every registry's auth mounted (a Dockerfile can pull base images from several registries). So the two paths are deliberately separate: `dockerConfig()` (build, all) vs `dockerConfigForImages(...)` (deploy, matched-only).

**Alternatives rejected**:
- *Reuse `dialRegistryAuthSecretConfig` as-is* — produces the wrong secret type/key; pods would not authenticate.
- *Set credentials on a ServiceAccount instead of the pod* — rejected per spec Assumptions (mutating shared/default service accounts is a cluster-wide side effect; the user explicitly asked to "add this secret to the CRD").

## D3 — Secret granularity and lifecycle (per-deployment vs shared)

**Decision**: One pull secret **per deployment**, named deterministically via `K8sNamingUtils.generateUniqueName(deploymentId, "pull")`, created in the deployment's namespace, and registered with `DisposableResourceManager` as `K8sResourceKind.SECRET` so it is cleaned up on undeploy — exactly mirroring `AbstractDeploymentManager.provisionSecrets()` (`service/deployment/AbstractDeploymentManager.java:382-401`).

**Rationale**: This is the established deploy-time-secret pattern in the codebase and gives every spec invariant for free:
- **Idempotency (FR-008)** — create-or-replace under a deterministic per-deployment name; redeploy/duplicate/change-image just rewrites it.
- **Rotation (FR-009)** — the secret is rebuilt from current config on every deploy, so rotated credentials take effect on the next deploy/redeploy; no stale secret survives.
- **Concurrency (FR-010)** — the name is namespaced by `deploymentId`, so two different deployments never contend for the same object; a single deployment's own redeploy is serialized by the existing deploy flow.
- **Cleanup (FR-011)** — `DisposableResourceManager.saveK8sResources(...)` + undeploy's `deleteAll(...)` already remove per-deployment resources; reusing it means no accumulation and no risk of deleting a secret another live workload depends on (each is private to one deployment).

**Alternatives rejected**:
- *Single shared per-namespace aggregated secret* — cross-deployment ownership complicates cleanup (when is it safe to delete?) and rotation, and introduces the very accumulation/ownership hazards FR-011 warns about. The per-deployment pattern is simpler and already battle-tested here.

## D4 — How the secret reference reaches the CRD (injection seam)

**Decision** (as implemented): The deployment manager resolves the in-scope image(s), provisions the pull secret (D3), then injects `imagePullSecrets` directly onto the **already-built** CRD object that `serviceConfig(...)` returns — *not* by threading a parameter through `serviceConfig`. The manifest generators keep their existing signatures unchanged (which is why their unit tests need no edits).

- **Knative**: `KnativeDeploymentManager.prepareServiceSpec` calls `provisionForDeployment(deploymentId, namespace, [imageName])`, then a private `applyImagePullSecret(Service, name)` sets `service.getSpec().getTemplate().getSpec().setImagePullSecrets([LocalObjectReference(name)])` when a name was returned (`service/deployment/KnativeDeploymentManager.java`). `RevisionSpec.setImagePullSecrets(List<io.fabric8.kubernetes.api.model.LocalObjectReference>)` confirmed on the Knative 7.5.2 model.
- **KServe transformer**: `InferenceDeploymentManager.prepareServiceSpec` resolves the operator-configured transformer image (`TextClassificationTransformerSection.transformerImage()`) only when `detectedTask == TEXT_CLASSIFICATION`, provisions, then `applyTransformerImagePullSecret(InferenceService, name)` sets `service.getSpec().getTransformer().setImagePullSecrets([ImagePullSecrets(name)])` (`io.kserve.serving.v1beta1.inferenceservicespec.transformer.ImagePullSecrets`). Predictor is never touched (D5).

**Rationale**: Post-hoc injection on the returned CRD keeps the "should I inject?" decision (credential match) in the orchestrating deployment manager — which already imports and manipulates the Fabric8/KServe CRD types — and leaves the manifest generators (and their large test suites) untouched. This is strictly less invasive than threading a `pullSecretName` parameter through every `serviceConfig(...)` overload and its callers, while producing an identical manifest. FR-006 holds because injection is skipped when the provisioner returns empty.

**Alternatives rejected**:
- *Thread `pullSecretName` into `serviceConfig(...)` and inject inside the generator* — the originally-planned seam; rejected during implementation because it churns ~40 manifest-generator unit-test call sites for no manifest-output difference.
- *Have the manifest generator create the secret itself* — violates constitution III (K8s mutation outside `kubernetes/`) and couples object-assembly to side effects.

## D5 — Scope of in-scope images per deployment type

**Decision**:
- **Knative image-based** (MCP, interceptor, adapter, application): in-scope image = the deployment's container image.
- **Inference (KServe)**: in-scope image = the **transformer** container image, and only when transformer chaining is triggered. The **predictor** is *not* in scope — its model is a HuggingFace artifact loaded via `storageUri`, and the serving-runtime container image is cluster/runtime-managed, not a user/operator-supplied private-registry image.
- **NIM**: out of scope — retains its existing NGC pull-secret mechanism (`app.nim-service-config.spec.image.pullSecrets`), and the new path must not touch it (FR-012).

**Rationale**: Matches the actual image sources discovered in the manifest generators. The predictor never pulls a private *container* image in the current design, so injecting `imagePullSecrets` there would be inert; the transformer genuinely can come from a trusted private registry. Keeping NIM untouched avoids duplicate/conflicting pull secrets.

**Alternatives rejected**:
- *Also inject on the predictor "just in case"* — inert today and adds noise; can be revisited if a future feature lets users supply a custom predictor serving image from a private registry (flagged as a forward note in data-model.md).

## D6 — Feature flag and default

**Decision**: Add `app.registry.auto-pull-secret-enabled`, bound from `${AUTO_PULL_SECRET_ENABLED:true}` in `application.yml`, read via the existing `@Value` flag pattern (as `app.cilium-network-policies-enabled` is read in `CiliumNetworkPolicyCreator`). Default **true**.

**Rationale**: The feature's whole point (per the request) is that admins "only need to configure trusted registries and it would work" — so the value should be available without an extra opt-in step. It is safe to default on because it is strictly additive and only acts when an image matches a registry the admin *explicitly configured with credentials*; unmatched/public images are untouched (FR-006). The flag exists purely as an operator escape hatch.

**Alternatives rejected / open for review**:
- *Default false (opt-in), matching `cilium`/`kserve`/`nim` flags* — more conservative on upgrade and consistent with sibling flags, but contradicts the "just works" intent. This is the single decision most worth confirming in review or `/speckit.clarify`; flipping the default is a one-line change with no structural impact.
- *No flag at all* — rejected; an escape hatch is cheap and prudent for a behavior that injects secrets into every matched workload.

## D7 — Behavior when a private image's registry is unconfigured (FR-014)

**Decision**: Do nothing extra — no secret is created, no `imagePullSecrets` injected, and the deploy proceeds. If the image is genuinely private, the pull fails at the Kubernetes level and surfaces through the existing deployment-status/event mechanisms unchanged. No false "succeeded" signal is emitted on the basis of credential provisioning.

**Rationale**: The system cannot fabricate credentials it was never given. Preserving today's failure surface (rather than swallowing or masking it) keeps the outcome diagnosable and avoids over-engineering. A clearer pre-deploy warning is a possible enhancement noted in the spec's checklist follow-ups, not part of this scope.

**Alternatives rejected**:
- *Fail the deploy early with a custom error* — changes existing behavior for a case the system genuinely cannot resolve, and risks rejecting deployments that would succeed via node-level/imagePullSecrets configured outside this manager. Out of scope.

## D8 — Persistence / migrations

**Decision**: No new entity, no Flyway migration, no `generateDbSchema` run. The pull secret is tracked using the existing `DisposableResource` table and the existing `K8sResourceKind.SECRET` enum value.

**Rationale**: The lifecycle reuses `DisposableResourceManager` wholesale; nothing new is persisted beyond rows that table already models. This keeps the multi-vendor migration surface at zero.
