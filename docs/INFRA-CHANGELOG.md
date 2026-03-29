# Infra Changelog

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

