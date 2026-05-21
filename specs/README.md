# Capability Specs

This directory contains one spec file per capability implemented in the AI DIAL Admin Deployment Manager Backend. Each spec documents requirements, scenarios, and implementation notes for a single capability.

**Workflow:** New features follow the spec-kit workflow: `/speckit.specify` (create spec) → `/speckit.plan` → `/speckit.tasks` → `/speckit.implement`. Use `/speckit.constitution` to amend project-wide conventions.

Numbered feature specs (`NNN-<short-name>/`) declare a `**Capability**:` field that maps them to the capability specs below; on implementation, the matching capability spec is updated and gains an `Implemented via NNN-<feature>` reference. See root `CLAUDE.md` § "Numbered-spec hygiene" and `.specify/memory/constitution.md` § "spec-kit Workflow Rules".

---

## In-flight & recent features

Numbered specs created via `/speckit.specify`. `Status` reflects the value in each spec's header — flip to `Implemented` only when the feature ships and the matching capability spec(s) are updated.

| Feature | Status | Capability | Brief |
|---|---|---|---|
| [001-deployment-topics](001-deployment-topics/spec.md) | Implemented | topics | Add topic assignment on deployments, mirroring image-definition topics |
| [002-deployment-command-args](002-deployment-command-args/spec.md) | Implemented | deployments | Support `command` and `args` configuration for all deployment types |
| [003-unified-deployment-source](003-unified-deployment-source/spec.md) | Implemented | deployments | Unified deployment source model with direct `imageReference` for Knative |
| [004-store-service-name](004-store-service-name/spec.md) | Implemented | deployments | Persist resolved K8s service name on deployment row to survive prefix changes |
| [005-external-registry-ref](005-external-registry-ref/spec.md) | Implemented | image-definitions | External registry reference on image/deployment sources for client lookups |
| [006-config-export-preview](006-config-export-preview/spec.md) | Implemented | export-import | `POST` preview endpoint that previews export contents without producing the ZIP |
| [007-config-import-preview](007-config-import-preview/spec.md) | Implemented | export-import | Preview import outcome (creates / overwrites / skips) before committing |
| [008-read-only-role](008-read-only-role/spec.md) | Implemented | security | Read-only admin role; non-read endpoints carry write annotations |
| [009-mcp-registry-filtering](009-mcp-registry-filtering/spec.md) | Implemented | mcp-registry | Backend filtering of MCP registry beyond what the upstream registry supports |
| [010-import-validations](010-import-validations/spec.md) | Implemented | export-import | Validate deserialized domain objects on import with the same rules as DTOs |
| [011-application-type](011-application-type/spec.md) | Implemented | application-image-definitions, application-deployments | New `Application` image-definition + deployment subtype |
| [012-nim-url-schema](012-nim-url-schema/spec.md) | Implemented | nim-deployments | Add `http`/`https` schema prefix to NIM service URLs based on endpoint kind |
| [013-nim-served-model-name](013-nim-served-model-name/spec.md) | Implemented | nim-deployments | Override served model name via `NIM_SERVED_MODEL_NAME` env var |
| [014-auditing](014-auditing/spec.md) | Implemented | N/A — creates new capability auditing | Activity history for deployment resources via Hibernate Envers |
| [015-nim-kserve-migration](015-nim-kserve-migration/spec.md) | Implemented | nim-deployments, kubernetes-manifests | Migrate NIM CRD generation to KServe `inferencePlatform` (opt-in via env flag) |
| [016-node-pool-selector](016-node-pool-selector/spec.md) | Implemented | deployments | Node-pool selector API + per-deployment node pool assignment |
| [017-stop-image-build](017-stop-image-build/spec.md) | Implemented | image-builds | Stop in-flight image builds from the UI/API |
| [018-api-key-via-core-userinfo](018-api-key-via-core-userinfo/spec.md) | Implemented | security | Accept `Api-Key` header in oidc mode; validate via DIAL Core `/v1/user/info` |
| [019-explicit-pool-scheduling](019-explicit-pool-scheduling/spec.md) | Implemented | deployments | Replace derived label-key + capacity-numbers pool config with explicit nodeSelector/affinity/tolerations; create-time defaults cascade for new deployments |
| [021-inference-task-transformer](021-inference-task-transformer/spec.md) | Implemented | inference-deployments, kubernetes-manifests | Auto-detect HuggingFace text-classification at deploy time; chain a KServe predictor + transformer with operator-controlled image/resources |

---

## Core Domain

| Capability | Status | Summary |
|---|---|---|
| [image-definitions](image-definitions/spec.md) | Implemented | Base ImageDefinition entity — CRUD, common fields, subtype discriminator, grouped/versions views, async cascade deletion |
| [mcp-image-definitions](mcp-image-definitions/spec.md) | Implemented | MCP-specific image definition — adds `transportType` (LOCAL/REMOTE) field and validation |
| [interceptor-image-definitions](interceptor-image-definitions/spec.md) | Implemented | Interceptor-specific image definition subtype — no additional fields beyond the base contract |
| [adapter-image-definitions](adapter-image-definitions/spec.md) | Implemented | Adapter-specific image definition subtype — no additional fields beyond the base contract |
| [application-image-definitions](application-image-definitions/spec.md) | Implemented | Application-specific image definition subtype — no additional fields beyond the base contract |
| [image-builds](image-builds/spec.md) | Implemented | Image build pipeline — trigger builds, status lifecycle (NOT_BUILT → BUILDING → BUILT / BUILD_FAILED), log streaming via SSE |
| [deployments](deployments/spec.md) | Implemented | Base deployment CRUD, lifecycle states, deploy/undeploy, duplicate, change-image, pod introspection, SSE event streaming, probe properties, startup reconciliation |
| [mcp-deployments](mcp-deployments/spec.md) | Implemented | MCP server deployment — extends image-based deployments with transport protocol (SSE/HTTP_STREAMING) and MCP endpoint path |
| [interceptor-deployments](interceptor-deployments/spec.md) | Implemented | Interceptor deployment — extends image-based deployments with type INTERCEPTOR as the sole distinction |
| [adapter-deployments](adapter-deployments/spec.md) | Implemented | Adapter deployment — extends image-based deployments with type ADAPTER as the sole distinction |
| [application-deployments](application-deployments/spec.md) | Implemented | Application deployment — extends image-based deployments with type APPLICATION as the sole distinction |
| [inference-deployments](inference-deployments/spec.md) | Implemented | Inference model deployment — KServe-backed, HuggingFace model source, scaling configuration (PENDING_REQUESTS / ACTIVE_REQUESTS / HARDWARE_USAGE) |
| [nim-deployments](nim-deployments/spec.md) | Implemented | NVIDIA NIM model deployment — NIM-backed, NGC registry source, GPU configuration, optional gRPC port |
| [mcp-servers](mcp-servers/spec.md) | Implemented | MCP server management — list tools, resources, and prompts from running MCP deployments |
| [topics](topics/spec.md) | Implemented | Topic listing — read-only enumeration of distinct topics across image definitions |
| [domain-whitelist](domain-whitelist/spec.md) | Implemented | Global domain whitelist CRUD for deployment network access control |

## Configuration Management

| Capability | Status | Summary |
|---|---|---|
| [export-import](export-import/spec.md) | Implemented | Configuration portability — export deployments and image definitions as ZIP archive, import with OVERWRITE or KEEP_EXISTING conflict resolution; sensitive env vars sanitized before export |

## Kubernetes Integration

| Capability | Status | Summary |
|---|---|---|
| [kubernetes-manifests](kubernetes-manifests/spec.md) | Implemented | Kubernetes manifest generation — KNative (serverless), NIM (NVIDIA GPU), KServe (model serving); env var injection; probe converters |
| [kubernetes-events](kubernetes-events/spec.md) | Implemented | Real-time Kubernetes event streaming to clients via Server-Sent Events |
| [kubernetes-cleanup](kubernetes-cleanup/spec.md) | Implemented | Disposable Kubernetes resource lifecycle tracking and scheduled cleanup via ShedLock |

## Cross-cutting Concerns

| Capability | Status | Summary |
|---|---|---|
| [security](security/spec.md) | Implemented | Configurable security modes (none / basic / oidc), JWT/OIDC multi-provider authentication (Azure, Keycloak, Auth0, Okta, Cognito), Azure Identity SDK |
| [api-conventions](api-conventions/spec.md) | Implemented | REST API contract — versioning, ErrorView response structure with distributed tracing (traceparent), pagination patterns |
| [observability-and-logging](observability-and-logging/spec.md) | Implemented | OpenTelemetry log integration, W3C Trace Context propagation, `@LogExecution` AOP convention |
| [auditing](auditing/spec.md) | Implemented | Hibernate Envers change-tracking + denormalized activity feed; activity / revision / per-entity snapshot endpoints |

## Infrastructure

| Capability | Status | Summary |
|---|---|---|
| [database-and-migrations](database-and-migrations/spec.md) | Implemented | Multi-vendor database support (H2 / PostgreSQL / SQL Server) with Flyway migrations; SQL and Java migration paths; current schema V1.46 |
| [health](health/spec.md) | Implemented | Health check endpoint for liveness/readiness probes |

## External Integrations

| Capability | Status | Summary |
|---|---|---|
| [container-registry](container-registry/spec.md) | Implemented | Docker registry operations — image inspection, copying via Skopeo |
| [huggingface](huggingface/spec.md) | Implemented | HuggingFace model hub integration — model search and metadata retrieval with cursor-based pagination |
| [buildkit](buildkit/spec.md) | Implemented | Container image building via BuildKit — rootful and rootless modes |
| [mcp-registry](mcp-registry/spec.md) | Implemented | MCP registry browser — list available MCP server packages and versions from an external registry |
