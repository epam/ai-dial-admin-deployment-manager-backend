# Quickstart: NIM Service URL Schema Prefix

## What Changed

NIM service URL resolution now prepends a schema prefix (`http://` or `https://`) to endpoint URLs returned by the NIM Kubernetes operator. Previously, raw endpoint values were used as-is, which could lack a schema prefix.

## Default Behavior

- **Cluster-internal endpoint** (`use-cluster-internal-url: true`): prepends `http://`
- **External endpoint** (`use-cluster-internal-url: false`): prepends `https://`
- If the endpoint already contains a schema prefix, it is returned unchanged.

## Overriding the Schema

Set the `K8S_NIM_DEPLOYMENT_URL_SCHEMA` environment variable (or `app.nim.deploy.url-schema` in application.yml) to override the default schema:

```yaml
app:
  nim:
    deploy:
      url-schema: ${K8S_NIM_DEPLOYMENT_URL_SCHEMA:}
```

Examples:
- `K8S_NIM_DEPLOYMENT_URL_SCHEMA=https` — forces `https://` for all NIM URLs
- `K8S_NIM_DEPLOYMENT_URL_SCHEMA=http` — forces `http://` for all NIM URLs
- Unset or empty — uses defaults (http for cluster, https for external)

## Verification

Run `./gradlew testFast` — the existing and new unit tests in `NimDeploymentManagerTest` validate all schema prefix scenarios.
