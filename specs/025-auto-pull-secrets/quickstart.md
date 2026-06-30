# Quickstart: Automatic Pull Secrets for Trusted-Registry Images

## What changes for an administrator

**Before**: deploying an image from a private registry required, per deployment, manually creating a `dockerconfigjson` secret and patching a service account.

**After**: configure trusted registries **once**; matching images get a pull secret auto-provisioned and wired into the workload. Nothing else to do.

## Configuration

Trusted-registry configuration is unchanged (existing env vars):

| Env var | Meaning |
|---|---|
| `DOCKER_REGISTRY`, `DOCKER_REGISTRY_USER`, `DOCKER_REGISTRY_PASSWORD`, `DOCKER_REGISTRY_AUTH=BASIC` | Primary registry credentials. |
| `TRUSTED_PRIVATE_REGISTRIES` | JSON array of `{registry, authScheme, protocol, user, password}`. |

One new flag:

| Env var | Default | Meaning |
|---|---|---|
| `AUTO_PULL_SECRET_ENABLED` | `true` | When `true`, the manager auto-provisions a `dockerconfigjson` pull secret and injects `imagePullSecrets` for deployments whose in-scope image matches a credentialed configured registry. Set `false` to restore the prior manual behavior. |

Example `TRUSTED_PRIVATE_REGISTRIES`:

```json
[
  { "registry": "my.private.registry", "authScheme": "BASIC", "user": "user1", "password": "pass1" }
]
```

## Manual verification

### Knative image-based deployment (US1)
1. Configure a trusted private registry with valid credentials; ensure `AUTO_PULL_SECRET_ENABLED` is unset or `true`.
2. Create an MCP/interceptor/adapter/application deployment whose image is `my.private.registry/...`.
3. Deploy it. **Expect**: the workload reaches Running with no manual secret/service-account work.
4. Inspect the generated Knative Service: `spec.template.spec.imagePullSecrets` references `<deploymentId>-…-pull`.
5. `kubectl get secret <deploymentId>-…-pull -n <ns> -o yaml` → `type: kubernetes.io/dockerconfigjson`, key `.dockerconfigjson`.
6. Undeploy. **Expect**: the secret is removed (no accumulation).

### Inference with transformer (US2)
1. Configure a trusted private registry that holds the transformer image; set `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE` to that image.
2. Deploy an inference model that triggers text-classification transformer chaining.
3. **Expect**: the transformer pod pulls and runs; `spec.transformer.imagePullSecrets` references the pull secret; the predictor has none.

### Regression — public/unmatched image (US3)
1. Deploy a workload using a public image (e.g. `docker.io/library/...` with no configured credentials).
2. **Expect**: the generated manifest has **no** `imagePullSecrets`; behavior is identical to the previous release.

### Feature disabled (D6)
1. Set `AUTO_PULL_SECRET_ENABLED=false` and deploy a workload whose image matches a trusted registry.
2. **Expect**: no pull secret created and no `imagePullSecrets` injected (manual behavior restored).

## Automated tests to run

```bash
./gradlew testFast          # manifest unit tests + H2 functional deploy tests for this feature
./gradlew checkstyleMain checkstyleTest
```

Full PR gate: `./gradlew clean build`. No `generateDbSchema` run is needed — this feature adds no migration.
