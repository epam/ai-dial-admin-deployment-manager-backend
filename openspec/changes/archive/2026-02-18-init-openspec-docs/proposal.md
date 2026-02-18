## Why

The project has no OpenSpec documentation — the `openspec/` directory exists with a minimal `config.yaml` placeholder but no capability specs, no architectural context, and no enforcement rules. This makes AI-assisted development unreliable: tools lack the conventions, constraints, and domain knowledge needed to generate consistent, correct artifacts. Establishing the baseline now enables all future feature work to use the full OpenSpec workflow.

## What Changes

- Populate `openspec/config.yaml` with full project context: tech stack, layered architecture, naming conventions, code style, API conventions, testing conventions, maintenance policies, and per-artifact enforcement rules
- Create `openspec/specs/README.md` as the navigable index of all capabilities
- Create 23 new capability spec files covering all currently implemented functionality

## Capabilities

### New Capabilities

**Core Domain**
- `image-definitions`: Base ImageDefinition entity — CRUD operations, common fields, and subtype discriminator shared by MCP, Interceptor, and Adapter image definitions
- `mcp-image-definitions`: MCP-specific image definition configuration, fields, and validation rules
- `interceptor-image-definitions`: Interceptor-specific image definition configuration, fields, and validation rules
- `adapter-image-definitions`: Adapter-specific image definition configuration, fields, and validation rules
- `image-builds`: Image build pipeline — trigger, status lifecycle, log streaming via SSE
- `deployments`: Base deployment CRUD, lifecycle states (NOT_DEPLOYED → PENDING → RUNNING / CRASHED / STOPPED / STOPPING), deploy/undeploy operations, pod introspection, SSE event streaming, and startup reconciliation. Two deployment families share this base: image-based (MCP, Interceptor, Adapter — require `imageDefinitionId`) and model-source (Inference, NIM — reference model sources directly)
- `mcp-deployments`: MCP server deployment — extends image-based with `transport` (McpTransportDto) and `mcpEndpointPath`
- `interceptor-deployments`: Interceptor deployment — extends image-based with no additional fields; `type: INTERCEPTOR` is the sole distinction
- `adapter-deployments`: Adapter deployment — extends image-based with no additional fields; `type: ADAPTER` is the sole distinction
- `inference-deployments`: Inference model deployment CRUD and lifecycle (KServe-backed, model-source family)
- `nim-deployments`: NVIDIA NIM model deployment CRUD and lifecycle (NIM-specific, model-source family)
- `mcp-servers`: MCP server management operations
- `topics`: Topic listing (read-only enumeration)
- `domain-whitelist`: Global domain whitelist CRUD for deployment access control

**Kubernetes Integration**
- `kubernetes-manifests`: Kubernetes manifest generation strategies — KNative (serverless), NIM (NVIDIA), KServe (model serving)
- `kubernetes-events`: Real-time Kubernetes event streaming to clients via Server-Sent Events
- `kubernetes-cleanup`: Disposable Kubernetes resource lifecycle tracking and scheduled cleanup

**Cross-cutting Concerns**
- `security`: JWT/OIDC multi-provider authentication (Azure, Keycloak, Auth0, Okta, Cognito) with configurable security modes
- `api-conventions`: REST API contract — versioning, `ErrorView` response structure with distributed tracing (`traceparent`), pagination
- `observability-and-logging`: OpenTelemetry log integration, W3C Trace Context propagation, log levels

**Infrastructure**
- `database-and-migrations`: Multi-vendor database support (H2 / PostgreSQL / SQL Server) with Flyway migrations
- `health`: Health check endpoint

**External Integrations**
- `container-registry`: Docker registry operations (image inspection, copying via Skopeo)
- `huggingface`: HuggingFace model hub integration — model search and metadata retrieval (cursor-based pagination)
- `buildkit`: Container image building via BuildKit (replaced Kaniko)

### Modified Capabilities

_(none — no existing specs to modify)_

## Impact

- **Files created**: `openspec/config.yaml` (replaced), `openspec/specs/README.md`, 23 × `openspec/specs/<name>/spec.md`
- **Source code**: No application code changes — documentation only
- **Breaking changes**: None
- **Configuration changes**: None
