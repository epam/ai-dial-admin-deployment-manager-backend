# Contracts: Automatic Pull Secrets

This feature adds **no new REST API** and changes **no existing HTTP contract**. There are no new endpoints, request/response DTOs, or OpenAPI changes. The meaningful contracts here are (1) the generated-CRD manifest contract and (2) the internal service contract for provisioning. Both are documented so they can be asserted in tests.

## Contract 1 — Generated CRD `imagePullSecrets` (the observable manifest contract)

### Knative Service (image-based: MCP / interceptor / adapter / application)

**When** the deployment's image host matches a credentialed configured registry **and** the feature is enabled:

```yaml
# spec.template.spec  (RevisionSpec)
imagePullSecrets:
  - name: <prefix>-<deploymentId>-pull-<random>   # generateUniqueName(deploymentId, "pull")
```

**When** unmatched / anonymous / feature disabled: the `imagePullSecrets` field is **absent / empty** — the manifest is identical to the pre-feature output (FR-006).

### KServe InferenceService (chained transformer)

**When** the transformer image host matches a credentialed configured registry **and** chaining is triggered **and** the feature is enabled:

```yaml
# spec.transformer  (Transformer)
imagePullSecrets:
  - name: <prefix>-<deploymentId>-pull-<random>
```

**When** predictor-only, unmatched, or feature disabled: `spec.transformer.imagePullSecrets` is absent/empty; predictor is never given an auto pull secret (D5).

### NIM (NIMService) — unchanged

`spec.image.pullSecrets` continues to be `[${NIM_SERVICE_NGC_SECRET:ngc-secret}]`. This feature does not read, add to, or modify it (FR-012).

## Contract 2 — Provisioned Secret object

| Property | Required value |
|---|---|
| `kind` | `Secret` |
| `type` | `kubernetes.io/dockerconfigjson` |
| `data`/`stringData` key | `.dockerconfigjson` |
| value | well-formed `{"auths": { … }}` containing ONLY the matched registry/registries serving the deployment's in-scope images (narrowed — not the full build-time aggregate) |
| `metadata.name` | `generateUniqueName(deploymentId, "pull")` |
| `metadata.namespace` | the deployment's namespace |

Created via `K8sClient.createSecret(namespace, secret)`; registered as a `DisposableResource` of kind `SECRET` under `groupId = deploymentId`.

## Contract 3 — Internal provisioner service (Java seam)

```text
RegistryService (service/)
  Optional<String> dockerConfigForImages(Collection<String> imageReferences)   // NEW (deploy-time, narrowed)
    // {"auths":{…}} containing ONLY the credentialed registries that serve the given images
    // (primary/trusted BASIC, DockerHubAliases.sameRegistry-aware); Optional.empty() when none match
    // dockerConfig() (build-time, ALL registries) is left UNCHANGED

RegistryPullSecretProvisioner (service/)   // NEW
  Optional<String> provisionForDeployment(String deploymentId, String namespace, Collection<String> inScopeImages)
    // returns the created secret name when at least one in-scope image matched a
    // credentialed registry; Optional.empty() otherwise (→ no imagePullSecrets injected)
    // no-op returning empty when app.registry.auto-pull-secret-enabled == false

ManifestGenerator (service/manifest/)
  Secret pullSecretConfig(String name, String dockerConfigJson)   // NEW, type=kubernetes.io/dockerconfigjson

TextClassificationTransformerSection (service/manifest/)
  String transformerImage()   // NEW accessor: operator-configured transformer image (or null)

KnativeDeploymentManager / InferenceDeploymentManager (service/deployment/)
  // As implemented: provision, then inject imagePullSecrets directly onto the CRD object
  // returned by serviceConfig(...) — manifest-generator signatures are UNCHANGED.
  //   Knative:   service.getSpec().getTemplate().getSpec().setImagePullSecrets([LocalObjectReference(name)])
  //   Inference: service.getSpec().getTransformer().setImagePullSecrets([ImagePullSecrets(name)])  (transformer only)
  // Injection is skipped when provisionForDeployment(...) returns Optional.empty().
```

## Test obligations (acceptance → assertions)

| Spec scenario | Assertion against contract |
|---|---|
| US1.1 | Knative manifest contains `spec.template.spec.imagePullSecrets[0].name` matching `<prefix>-<deploymentId>-pull-<random>`; a `dockerconfigjson` Secret of that name exists in the namespace. |
| US1.2 | Deploy → undeploy → redeploy: no error; secret name stable; secret present after redeploy, absent after undeploy. |
| US1.3 | Change image to a different trusted registry → secret's `.dockerconfigjson` contains an `auths` entry for the new registry. |
| US2.1 | InferenceService `spec.transformer.imagePullSecrets[0].name` set; predictor has none. |
| US2.2 | Predictor-only inference → no transformer block / no auto pull secret. |
| US3.1 | Public/unmatched image → manifest has no `imagePullSecrets`; byte-identical to pre-feature output. |
| US3.2 | Configured-but-anonymous registry match → no credential-bearing secret injected. |
| FR-007 | Image expressed as `index.docker.io/...` matches a `docker.io` credential and vice-versa. |
| FR-012 | NIM manifest's `spec.image.pullSecrets` unchanged; no extra secret created. |
| FR-009 | Rotate registry password in config → redeploy → secret value reflects new credentials. |
| D6 | With `AUTO_PULL_SECRET_ENABLED=false`, no secret created and no `imagePullSecrets` injected even when an image matches. |
