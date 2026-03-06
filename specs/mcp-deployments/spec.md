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
An MCP deployment SHALL carry all image-based deployment fields: `imageDefinitionId` (nullable), `imageDefinitionName` (nullable), `imageDefinitionVersion` (nullable), `imageDefinitionType` (`ImageTypeDto`; `@NotNull` in response), plus all base deployment fields. The image definition can be referenced either by `imageDefinitionId` alone, or by the (`imageDefinitionType`, `imageDefinitionName`, `imageDefinitionVersion`) triple.

Status: **Implemented**

#### Scenario: Image definition linked by ID
- **WHEN** an MCP deployment is created with a valid `imageDefinitionId`
- **THEN** the deployment references the specified image definition and `imageDefinitionType` is populated in the response

#### Scenario: Image definition linked by type + name + version
- **WHEN** an MCP deployment is created with `imageDefinitionType: MCP`, `imageDefinitionName`, and `imageDefinitionVersion` (no `imageDefinitionId`)
- **THEN** the image definition is resolved by type + name + version and the deployment is created successfully

#### Scenario: Incomplete image reference rejected
- **WHEN** `POST /api/v1/deployments` is called with `type: MCP` without a complete image reference (no `imageDefinitionId` and missing one or more of `imageDefinitionType`/`imageDefinitionName`/`imageDefinitionVersion`)
- **THEN** the system responds with 400

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
An MCP deployment SHALL require `app.knative.enabled=true` to deploy. When KNative is disabled, the `KnativeDeploymentManager` bean is not instantiated (`@ConditionalOnProperty`) and no alternative deployment backend exists for image-based types.

Status: **Implemented**

#### Scenario: Deploy with KNative disabled
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called for an MCP deployment with `app.knative.enabled=false`
- **THEN** the deploy operation fails because no suitable deployment manager is available

## Implementation Notes
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.CreateMcpDeploymentRequestDto`
- Response DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.McpDeploymentDto`
- Parent abstract DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.ImageBasedDeploymentDto`
- Transport DTO: `com.epam.aidial.deployment.manager.web.dto.McpTransportDto`
- Kubernetes backend: KNative service (when `app.knative.enabled=true`)
- Related specs: `deployments` (base contract), `mcp-image-definitions`, `mcp-servers`, `kubernetes-manifests`
