# Capability Specs

This directory contains one spec file per capability implemented in the AI DIAL Admin Deployment Manager Backend. Each spec documents requirements, scenarios, and implementation notes for a single capability.

**Workflow:** New features follow the spec-kit workflow: `/speckit.specify` (create spec) → `/speckit.plan` → `/speckit.tasks` → `/speckit.implement`. Use `/speckit.constitution` to amend project-wide conventions.

---

## Core Domain

| Capability | Status | Summary |
|---|---|---|
| [image-definitions](image-definitions/spec.md) | Implemented | Base ImageDefinition entity — CRUD, common fields, subtype discriminator, grouped/versions views, async cascade deletion |
| [mcp-image-definitions](mcp-image-definitions/spec.md) | Implemented | MCP-specific image definition — adds `transportType` (LOCAL/REMOTE) field and validation |
| [interceptor-image-definitions](interceptor-image-definitions/spec.md) | Implemented | Interceptor-specific image definition subtype — no additional fields beyond the base contract |
| [adapter-image-definitions](adapter-image-definitions/spec.md) | Implemented | Adapter-specific image definition subtype — no additional fields beyond the base contract |
| [image-builds](image-builds/spec.md) | Implemented | Image build pipeline — trigger builds, status lifecycle (NOT_BUILT → BUILDING → BUILT / BUILD_FAILED), log streaming via SSE |
| [deployments](deployments/spec.md) | Implemented | Base deployment CRUD, lifecycle states, deploy/undeploy, duplicate, change-image, pod introspection, SSE event streaming, probe properties, startup reconciliation |
| [mcp-deployments](mcp-deployments/spec.md) | Implemented | MCP server deployment — extends image-based deployments with transport protocol (SSE/HTTP_STREAMING) and MCP endpoint path |
| [interceptor-deployments](interceptor-deployments/spec.md) | Implemented | Interceptor deployment — extends image-based deployments with type INTERCEPTOR as the sole distinction |
| [adapter-deployments](adapter-deployments/spec.md) | Implemented | Adapter deployment — extends image-based deployments with type ADAPTER as the sole distinction |
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
