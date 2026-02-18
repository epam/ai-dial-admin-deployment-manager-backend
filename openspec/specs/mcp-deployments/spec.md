# MCP Deployments

## Purpose
This spec describes deployments of type MCP — the image-based deployment family member for Model Context Protocol servers. MCP deployments extend the base image-based contract with MCP transport configuration and endpoint path.

Status: **Implemented**

## Key Terms
- **MCP deployment**: A deployment of type `MCP` that runs an MCP server container and exposes MCP protocol endpoints (tools, resources, prompts).
- **Transport (`McpTransportDto`)**: The **wire protocol** used to communicate with the MCP server at runtime. Values: `SSE` (Server-Sent Events) or `HTTP_STREAMING`. Nullable — defaults to `HTTP_STREAMING` when not specified. **Not to be confused with** `McpTransportTypeDto` on the image definition (see `mcp-image-definitions` spec), which controls the deployment model (LOCAL vs REMOTE) — the two are independent concerns and any combination is valid.
- **mcpEndpointPath**: The URL path at which the MCP server listens, relative to the deployment's base URL (e.g., `/mcp`).

## Requirements

### Requirement: MCP deployment extends the image-based contract
An MCP deployment SHALL carry all image-based deployment fields: `imageDefinitionId` (required), `imageDefinitionName` (required), `imageDefinitionVersion` (required), plus all base deployment fields. It SHALL be linked to an MCP image definition.

Status: **Implemented**

#### Scenario: Image definition reference required
- **WHEN** `POST /api/v1/deployments` is called with `type: MCP` without `imageDefinitionId`
- **THEN** the system responds with 400

#### Scenario: Image definition linked
- **WHEN** an MCP deployment is created with a valid `imageDefinitionId`
- **THEN** the deployment references the specified image definition and uses its container image for Kubernetes resource generation

### Requirement: MCP deployment carries transport configuration
An MCP deployment SHALL support an optional `transport` field (McpTransportDto) that configures how the MCP server communicates.

Status: **Implemented**

#### Scenario: Transport type stored and returned
- **WHEN** an MCP deployment is created or updated with a `transport` value
- **THEN** the transport configuration is persisted and returned in all responses

#### Scenario: Null transport defaults to HTTP_STREAMING
- **WHEN** an MCP deployment is created without specifying `transport`
- **THEN** the deployment is created successfully; the response shows `transport: null` but the system uses `HTTP_STREAMING` as the effective default for MCP client communication

### Requirement: MCP deployment carries optional endpoint path
An MCP deployment SHALL support an optional `mcpEndpointPath` field that defines the URL path for the MCP server endpoint. The value MUST start with `/`.

Status: **Implemented**

#### Scenario: Valid endpoint path
- **WHEN** an MCP deployment is created with `mcpEndpointPath: "/mcp"`
- **THEN** the path is persisted and returned in responses

#### Scenario: Path not starting with slash rejected
- **WHEN** an MCP deployment is created with `mcpEndpointPath: "mcp"` (no leading slash)
- **THEN** the system responds with 400

#### Scenario: Null path accepted
- **WHEN** an MCP deployment is created without `mcpEndpointPath`
- **THEN** `mcpEndpointPath` is null and the deployment is created successfully

### Requirement: MCP deployment requires KNative enabled
An MCP deployment SHALL require `K8S_KNATIVE_ENABLED=true` to deploy. When KNative is disabled, the `KnativeDeploymentManager` bean is not instantiated (`@ConditionalOnProperty`) and no alternative deployment backend exists for image-based types.

Status: **Implemented**

#### Scenario: Deploy with KNative disabled
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called for an MCP deployment with `K8S_KNATIVE_ENABLED=false`
- **THEN** the deploy operation fails because no suitable deployment manager is available

## Implementation Notes
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.CreateMcpDeploymentRequestDto`
- Response DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.McpDeploymentDto`
- Parent abstract DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.ImageBasedDeploymentDto`
- Transport DTO: `com.epam.aidial.deployment.manager.web.dto.McpTransportDto`
- Kubernetes backend: KNative service (when `K8S_KNATIVE_ENABLED=true`)
- Related specs: `deployments` (base contract), `mcp-image-definitions`, `mcp-servers`, `kubernetes-manifests`
