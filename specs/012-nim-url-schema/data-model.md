# Data Model: NIM Service URL Schema Prefix

## Modified Entities

### NimDeployProperties

Configuration holder for NIM deployment settings. Located at `com.epam.aidial.deployment.manager.configuration.NimDeployProperties`.

**Existing fields** (unchanged):
- `namespace` (String) — K8s namespace for NIM deployments
- `startupTimeout` (int) — startup timeout in seconds
- `informerResyncInterval` (long) — informer resync interval in seconds
- `useClusterInternalUrl` (boolean) — cluster-internal vs external endpoint toggle
- `clusterHost` (String) — host for external URL ingress

**New field**:
- `urlSchema` (String) — Optional override for URL schema prefix. When empty/null, defaults apply: `http` for cluster-internal, `https` for external. When set, this value is used regardless of endpoint type.

### application.yml Entry

```yaml
app:
  nim:
    deploy:
      url-schema: ${K8S_NIM_DEPLOYMENT_URL_SCHEMA:}
```

Default: empty (use endpoint-type-based defaults).

## No New Entities

This feature modifies only `NimDeployProperties` and `NimDeploymentManager.resolveServiceUrl()`. No new entities, database tables, or migrations are needed.
