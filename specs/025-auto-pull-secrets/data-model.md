# Phase 1 Data Model: Automatic Pull Secrets for Trusted-Registry Images

**No database schema changes.** This feature introduces no JPA entity, no table, and no Flyway migration. The model below is the *conceptual / in-memory* model plus the one reused persistence record.

## In-memory / domain concepts

### Narrowed deploy-time docker config (value object, transient)
`RegistryService.dockerConfigForImages(Collection<String> imageReferences)` → `Optional<String>`.

| Aspect | Notes |
|---|---|
| Content | `{"auths":{…}}` (sensitive, never logged) carrying credentials ONLY for the configured **credentialed** registries (primary or trusted, `authScheme == BASIC`, non-null user+password) whose host matches one of the in-scope images, via `DockerHubAliases.sameRegistry(...)`. First match wins per image (primary before trusted). |
| Empty | `Optional.empty()` when no in-scope image matches a credentialed registry (→ no secret, no injection). |
| Derivation | From `RegistryProperties` (`configuration/RegistryProperties.java`); host parsed via Jib `ImageReference.parse(...)`; unparseable refs skipped. |
| Distinct from | `RegistryService.dockerConfig()` (the build-pipeline aggregate of **all** configured registries) — unchanged. |
| Lifetime | Computed per deploy; not persisted. |

### ImagePullSecretReference (manifest fragment, transient)
The `imagePullSecrets` entry injected into a generated CRD.

| Field | Type | Notes |
|---|---|---|
| `name` | String | The provisioned secret's name: `K8sNamingUtils.generateUniqueName(deploymentId, "pull")`. |

- Rendered as `LocalObjectReference{name}` into `RevisionSpec.imagePullSecrets` (Knative) or `Transformer.imagePullSecrets` (KServe).
- Injected **only** when `dockerConfigForImages(...)` is present for the relevant in-scope image.

### Auto-provisioned pull Secret (Kubernetes object, transient cluster resource)
| Aspect | Value |
|---|---|
| Kind | `Secret` |
| Type | `kubernetes.io/dockerconfigjson` |
| Data key | `.dockerconfigjson` → the narrowed `{"auths":{matched registries only}}` document |
| Name | `generateUniqueName(deploymentId, "pull")` |
| Namespace | The deployment's namespace (`AbstractDeploymentManager.namespace`) |
| Owner/lifecycle | Tracked as a `DisposableResource` (below); created via `K8sClient.createSecret`, removed on undeploy via `DisposableResourceManager.deleteAll` |

## Reused persistence record (no change)

### DisposableResource (existing table — reused, not modified)
The pull secret is registered exactly like the sensitive-env secret:

| Field | Value for this feature |
|---|---|
| `groupId` | the `deploymentId` |
| `reference.kind` | `K8sResourceKind.SECRET` (existing enum value) |
| `reference.namespace` | deployment namespace |
| `reference.name` | the pull-secret name |
| `lifecycleState` | `TEMPORARY` → `STABLE` after successful create (mirrors `provisionSecrets`) |

No new columns, no new `K8sResourceKind` value, no migration.

## State / flow

```
deploy(deployment):                                          # in the deployment manager (D4)
  manifest = serviceConfig(...)                              # generators UNCHANGED
  if auto-pull-secret-enabled AND deployment type in {Knative-image-based, Inference-with-transformer}:
     images = in-scope image(s) for this deployment          # D5
     dockerConfig = registryService.dockerConfigForImages(images)   # D1/D2 — narrowed, Optional
     if dockerConfig present:
        secret = pullSecretConfig(name, dockerConfig)        # D2 (type=dockerconfigjson)
        disposableResourceManager.saveK8sResources([secret], SECRET, deploymentId, namespace)  # TEMPORARY
        k8sClient.createSecret(namespace, secret)            # D3 (kubernetes/ only)
        disposableResourceManager.changeResourceLifecycle(... STABLE)
        inject imagePullSecrets=[name] onto the built CRD    # D4 (manager, not generator)
     # else (empty): nothing injected — manifest unchanged (FR-006)
  apply(manifest)

undeploy(deployment):
  disposableResourceManager.deleteAll(resourcesForGroup(deploymentId))   # removes the pull secret (FR-011)
```

## Validation rules (from spec requirements)

- A pull secret is created **iff** a credentialed configured registry matches an in-scope image (FR-003, FR-005, FR-006).
- Anonymous/no-auth registries never yield a credential-bearing secret (FR-005).
- Docker Hub aliases are normalized during matching (FR-007).
- NIM deployments are never processed by this path (FR-012).
- The build/copy pipeline's existing credential handling is untouched (FR-013).

## Forward note (not in scope)

If a future feature lets users supply a **custom predictor serving image** from a private registry, the same resolver + injection seam extends to `Predictor`/`Model.imagePullSecrets` (`InferenceMappers.PREDICTOR_MODEL_FIELD`) with no change to the provisioning model. Out of scope here (D5).
