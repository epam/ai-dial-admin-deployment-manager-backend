# Infra Changelog

## 0.17.0

### Added

#### Security & RBAC
- `API_KEY_ENABLED` — accepts the `Api-Key` header as an alternative credential alongside JWT/OIDC (default: `false`)
- `API_KEY_CORE_URL` — base URL of DIAL Core for validating API keys
- `API_KEY_CACHE_TTL_SECONDS` — TTL for cached introspection results (default: `60`)
- `API_KEY_CACHE_MAX_SIZE` — maximum entries in the introspection cache (default: `10000`)
- `API_KEY_REQUEST_TIMEOUT_MS` — per-call timeout for HTTP requests to Core's `/v1/user/info` (default: `3000`)
- `API_KEY_ROLES_MAPPING` — JSON mapping of Core project-key role names to DM application roles
- `API_KEY_STARTUP_PROBE` — probes Core's user-info endpoint at startup and aborts if unreachable (default: `true`)
- It is now possible to stop build jobs, so the `deletecollection` permission for `jobs` must be added to build roles.

#### Deployment & Scaling
- Node Pool Configuration is now generally available (removed `[Preview]` tag). To use this feature for Knative deployments, `pod-annotations`, `pod-affinity`, and `nodeselector` keys MUST be added to the Knative ConfigMap.
- `NODE_POOLS` — YAML list of node pool entries
- `NODE_POOL_DEFAULT` — Catch-all default pool ID stamped onto deployments created without an explicit `nodePoolId`
- `NODE_POOL_DEFAULT_MODEL` — Model-workload default pool ID for NIM and KServe-Inference deployments (takes precedence over `NODE_POOL_DEFAULT`)

---

### Changed

#### Providers
- `MCP_PROXY_EXECUTABLE_IMAGE_ALPINE` — default changed to `ghcr.io/epam/ai-dial-deployment-manager-mcp-proxy:0.1.0-alpine` and is no longer required
- `MCP_PROXY_EXECUTABLE_IMAGE_DEBIAN` — default changed to `ghcr.io/epam/ai-dial-deployment-manager-mcp-proxy:0.1.0-debian` and is no longer required

---

### Removed

#### Security & RBAC
- `config.rest.security.default.allowedRoles` and `providers.*.allowed-roles` — removed in favor of `roles-mapping`

---

## 0.16.0

### Added

#### Configuration Management
- `spring.jpa.properties.org.hibernate.envers.store_data_at_delete` — stores full deleted entity state for audit trail reconstruction (default: `true`)
- `app.validation.resources.max-storage-size` / `RESOURCES_STORAGE_MAX_SIZE` — sets the maximum allowed storage size for NIM deployments (default: `200Gi`)

#### Deployment & Scaling
- `app.knative.deploy.ready-grace-period` / `K8S_KNATIVE_DEPLOYMENT_READY_GRACE_PERIOD_SEC` — grace period after `Ready=False` before reporting `CRASHED` (default: `15`)

#### Providers
- `app.nim.deploy.url-schema` / `K8S_NIM_DEPLOYMENT_URL_SCHEMA` — overrides the URL scheme used for resolved NIM service URLs
- `app.nim.deploy.kserve-mode-enabled` / `K8S_NIM_DEPLOYMENT_KSERVE_MODE_ENABLED` — enables NIMService generation for KServe inference platform mode (default: `false`)
- `app.nim-service-expose-ingress-config.spec.ingressClassName` / `K8S_NIM_INGRESS_CLASS_NAME` — configures the ingress class for legacy external NIM ingress (default: `nginx`)

---

### Changed

#### Configuration Management
- Validation resource properties were renamed from `app.resources.*` to `app.validation.resources.*`; existing environment variable names remain unchanged

#### Deployment & Scaling
- Progress deadline behavior now applies a fallback of `startup-timeout + buffer-seconds` for KServe and NIM deployments when no startup probe is configured

#### Providers
- `app.nim.deploy.cluster-host` / `K8S_NIM_CLUSTER_HOST` — now applies only to legacy standalone external NIM URLs and is unused when NIM KServe mode is enabled

---

### Removed

#### Providers
- `app.nim-service-expose-ingress-config.enabled` and `app.nim-service-expose-ingress-config.annotations` — removed configurable enable flag and annotation overrides for NIM ingress

---

## 0.15.0

### Added

#### Security & RBAC

- `SECURITY_REQUIRE_EMAIL` — controls whether an email claim is required in JWT (default: `false`)
- `CLAIMS_EMAIL_KEY` — default JWT claim name for user email extraction (default: `unique_name`)
- `SECURITY_USER_CLAIM` — default JWT claim for user identification (default: `oid`)
- `SECURITY_ROLES_MAPPING` — JSON mapping of IdP roles to application roles (`FULL_ADMIN`, `READ_ONLY_ADMIN`)
- `READ_ONLY_ADMIN` application role — read-only users receive 403 on all mutating endpoints
- `providers.*.roles-mapping` — per-provider role mapping override, merged with default (provider takes precedence)
- `providers.*.email-claims` — per-provider JWT claim paths for email extraction
- `providers.*.principal-claim` — per-provider JWT claim for user identification
- `providers.*.user-info-endpoint` — URI for user info endpoint (required for opaque token introspection)

#### Configuration Management

- Config export endpoint — download the current deployment configuration as ZIP
- Config import endpoint — upload and apply a deployment configuration
- Import-preview endpoint — compute a diff of what would change without applying it
- `CONFIG_EXPORT_FILE_NAME` — name of the JSON file in exported config archive (default: `dm-config.json`)
- `CONFIG_EXPORT_ZIP_NAME` — name of the exported ZIP archive (default: `deployment-manager-config.zip`)

#### Deployment & Scaling

- Progress deadline auto-computation for KNative/KServe deployments from startup probe:
  - `DEPLOYMENT_PROGRESS_DEADLINE_DEFAULT_INITIAL_DELAY` (default: `0`)
  - `DEPLOYMENT_PROGRESS_DEADLINE_DEFAULT_PERIOD` (default: `10`)
  - `DEPLOYMENT_PROGRESS_DEADLINE_DEFAULT_FAILURE_THRESHOLD` (default: `3`)
  - `DEPLOYMENT_PROGRESS_DEADLINE_BUFFER_SECONDS` (default: `30`)
- Support for scaling configuration (initial/min/max scale) for Knative deployments
- Support for direct `imageReference` in Knative deployment sources
- Support for `command` and `args` configuration for all deployment types
- Deployment topics — categorize deployments with user-defined topics
- Application deployments and image definitions — new deployment type

#### Networking

- `HUGGINGFACE_DEFAULT_ALLOWED_DOMAINS` — comma-separated list of default domains added to Cilium egress for HuggingFace inference deployments (default: `huggingface.co,transfer.xethub.hf.co,cas-server.xethub.hf.co`)
- `K8S_NIM_CLUSTER_HOST` — cluster host for external NIM URL; required when `K8S_NIM_DEPLOYMENT_USE_CLUSTER_INTERNAL_URL` is `false`
- NIM Service Ingress Configuration — configurable Ingress template for externally exposed NIM services (applied when `K8S_NIM_DEPLOYMENT_USE_CLUSTER_INTERNAL_URL` is `false`):
  - `app.nim-service-expose-ingress-config.enabled` — enable/disable Ingress creation (default: `true`)
  - `app.nim-service-expose-ingress-config.annotations` — annotations applied to the Ingress resource, with defaults: `proxy-body-size=0`, `proxy-read-timeout=600`, `cluster-issuer=letsencrypt-production`, `force-ssl-redirect=true`
  - `app.nim-service-expose-ingress-config.spec.ingressClassName` — Ingress class name (default: `nginx`)
  - TLS and routing rules auto-generated from `cluster-host` and NIM service name; each service gets a `<NimServiceName>-tls-secret`

#### Providers

- MCP Registry integration — browse and filter MCP servers from the upstream registry:
  - `MCP_REGISTRY_BASE_URL` (default: `https://registry.modelcontextprotocol.io`)
  - `MCP_REGISTRY_MAX_PAGES_TO_SCAN` (default: `25`)
- MCP tool calling — invoke tools on deployed MCP servers
- GCP datasource auth type — `datasource.auth.type` now supports `gcp` in addition to `basic` and `azure`

#### Reconciliation

- Reconciliation executor thread pool configuration:
  - `DEPLOYMENT_RECONCILE_EXECUTOR_THREAD_POOL_SIZE` (default: `5`)
  - `DEPLOYMENT_RECONCILE_EXECUTOR_QUEUE_CAPACITY` (default: `100`)
  - `DEPLOYMENT_RECONCILE_EXECUTOR_THREAD_NAME_PREFIX` (default: `k8s-reconciliation-pool`)

---

### Changed

#### Deployment & Scaling

- `K8S_KNATIVE_DEPLOYMENT_STARTUP_TIMEOUT_SEC` default changed from `300` to `60`
- `KNATIVE_SERVICE_DEFAULT_MAX_SCALE` default changed from `3` to `1`

#### Reconciliation

- `K8S_KNATIVE_DEPLOYMENT_INFORMER_RESYNC_INTERVAL_SEC` default changed from `60` to `600`
- `K8S_NIM_DEPLOYMENT_INFORMER_RESYNC_INTERVAL_SEC` default changed from `60` to `600`
- `K8S_KSERVE_DEPLOYMENT_INFORMER_RESYNC_INTERVAL_SEC` default changed from `60` to `600`

#### Observability

- OpenTelemetry: removed redundant signal-specific OTLP endpoint/protocol overrides — global endpoint is now used for traces, metrics, and logs

---

### Deprecated

#### Security & RBAC

- `SECURITY_EMAIL_CLAIM` — replaced by `CLAIMS_EMAIL_KEY` at the default security level
- `config.rest.security.default.allowedRoles` and `providers.*.allowed-roles` — existing values auto-mapped to `FULL_ADMIN` for backward compatibility

---

