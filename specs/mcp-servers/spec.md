# MCP Servers

## Purpose
This spec describes the MCP server interaction API — querying a live MCP deployment for its advertised tools, resources, and prompts via the MCP protocol, as well as invoking individual tools. All three listing endpoints support cursor-based pagination.

Status: **Implemented**

## Key Terms
- **MCP tool**: A callable function advertised by an MCP server (e.g., a code execution tool, a search tool).
- **MCP resource**: A data resource accessible via the MCP server (e.g., a file, a database table).
- **MCP prompt**: A pre-defined prompt template exposed by the MCP server.
- **MCP protocol**: The Model Context Protocol used for communication between AI clients and MCP servers.
- **`nextCursor`**: An optional opaque cursor for paginating large tool/resource/prompt listings. If the server returns a non-null cursor, passing it in the next request advances to the next page.

## Requirements

### Requirement: List MCP tools for a deployment
The system SHALL query a live MCP deployment and return the list of tools it advertises.

Status: **Implemented**

#### Scenario: Retrieve tools
- **WHEN** `GET /api/v1/deployments/mcp/{deploymentId}/tools` is called for a running MCP deployment
- **THEN** the list of tools advertised by the MCP server is returned

#### Scenario: MCP server unreachable
- **WHEN** `GET /api/v1/deployments/mcp/{deploymentId}/tools` is called and the MCP server is unavailable
- **THEN** an appropriate error response is returned (not a 200 with empty list)

#### Scenario: Non-existent deployment
- **WHEN** `GET /api/v1/deployments/mcp/{deploymentId}/tools` is called with an unknown deployment ID
- **THEN** the system responds with 404

### Requirement: List MCP resources for a deployment
The system SHALL query a live MCP deployment and return the list of resources it advertises.

Status: **Implemented**

#### Scenario: Retrieve resources
- **WHEN** `GET /api/v1/deployments/mcp/{deploymentId}/resources` is called
- **THEN** the list of resources advertised by the MCP server is returned

### Requirement: List MCP prompts for a deployment
The system SHALL query a live MCP deployment and return the list of prompts it advertises.

Status: **Implemented**

#### Scenario: Retrieve prompts
- **WHEN** `GET /api/v1/deployments/mcp/{deploymentId}/prompts` is called
- **THEN** the list of prompts advertised by the MCP server is returned

### Requirement: Call an MCP tool on a deployment
The system SHALL invoke a specific tool on a live MCP deployment and return the result.

Status: **Implemented**

#### Scenario: Successful tool call
- **WHEN** `POST /api/v1/deployments/mcp/{deploymentId}/call-tool` is called with a valid tool name and arguments
- **THEN** the tool is invoked on the MCP server and the result is returned

#### Scenario: MCP server unreachable during tool call
- **WHEN** `POST /api/v1/deployments/mcp/{deploymentId}/call-tool` is called and the MCP server is unavailable
- **THEN** an appropriate error response is returned

#### Scenario: Non-existent deployment
- **WHEN** `POST /api/v1/deployments/mcp/{deploymentId}/call-tool` is called with an unknown deployment ID
- **THEN** the system responds with 404

### Requirement: Cursor-based pagination for MCP listings
Tools, resources, and prompts listing endpoints SHALL support cursor-based pagination via an optional `nextCursor` query parameter.

Status: **Implemented**

#### Scenario: First page (no cursor)
- **WHEN** any MCP listing endpoint is called without `nextCursor`
- **THEN** the first page of results is returned; the response may include a cursor for the next page

#### Scenario: Subsequent page (with cursor)
- **WHEN** any MCP listing endpoint is called with a `nextCursor` value from a previous response
- **THEN** the next page of results is returned

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.web.controller.McpController`
- Base path: `/api/v1/deployments/mcp`
- Endpoints:
  - `GET /api/v1/deployments/mcp/{deploymentId}/tools`
  - `POST /api/v1/deployments/mcp/{deploymentId}/call-tool`
  - `GET /api/v1/deployments/mcp/{deploymentId}/resources`
  - `GET /api/v1/deployments/mcp/{deploymentId}/prompts`
- Service: `com.epam.aidial.deployment.manager.service.McpService`
  - Validates the deployment exists, is of type `McpDeployment`, and has `RUNNING` status before connecting
  - Throws `EntityNotFoundException` (404) for unknown deployment IDs
  - Throws `McpClientException` when the MCP server connection fails
- MCP client factory: `com.epam.aidial.deployment.manager.service.McpClientFactory`
- MCP endpoint path resolver: `com.epam.aidial.deployment.manager.service.McpEndpointPathResolver`
- Health checker: `com.epam.aidial.deployment.manager.service.deployment.healthcheck.McpHealthChecker`
- MCP SDK: `io.modelcontextprotocol` (protocol communication via `McpSyncClient`)
- Request/response types: `McpSchema.CallToolRequest` / `McpSchema.CallToolResult`, `McpSchema.ListToolsResult`, `McpSchema.ListResourcesResult`, `McpSchema.ListPromptsResult`
- Pagination: optional `nextCursor` query parameter (`@RequestParam(required = false)`) on all three listing endpoints
- Related specs: `mcp-deployments`, `mcp-image-definitions`
