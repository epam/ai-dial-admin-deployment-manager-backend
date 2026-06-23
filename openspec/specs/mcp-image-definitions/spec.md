# MCP Image Definitions

## Purpose
This spec describes image definitions of subtype MCP (`$type: "mcp"`). MCP image definitions extend the base image definition with one additional field: `transportType`, which configures the MCP server communication protocol.

Status: **Implemented**

## Key Terms
- **MCP image definition**: An image definition of `$type: "mcp"` whose built container runs an MCP server.
- **Transport type (`McpTransportTypeDto`)**: Describes the **deployment model** of the MCP server — where it runs relative to the client. Values: `LOCAL` (the MCP server runs in the same process/pod as the client) or `REMOTE` (the MCP server is a separate, remotely-accessible service). Nullable. This field affects the image build pipeline: `REMOTE` uses the image copy pipeline (stable image); `LOCAL` uses a wrapper build pipeline with image analysis. **Not to be confused with** `McpTransportDto` on the deployment (see `mcp-deployments` spec), which controls the wire protocol (SSE vs HTTP_STREAMING) at runtime — the two are independent concerns.

## Additional Fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `transportType` | McpTransportTypeDto | No | MCP transport protocol; null means default/unspecified |

## Requirements

### Requirement: MCP image definition carries a transport type field
An MCP image definition SHALL support an optional `transportType` field that specifies the MCP communication protocol.

Status: **Implemented**

#### Scenario: Create with transport type
- **WHEN** `POST /api/v1/images/definitions` is called with `$type: "mcp"` and a `transportType` value
- **THEN** the MCP image definition is created with the specified transport type persisted

#### Scenario: Create without transport type
- **WHEN** `POST /api/v1/images/definitions` is called with `$type: "mcp"` and no `transportType`
- **THEN** the MCP image definition is created with `transportType: null`

#### Scenario: Transport type returned in response
- **WHEN** `GET /api/v1/images/definitions/{id}` is called for an MCP image definition
- **THEN** the response includes `transportType` (value or null) alongside all base fields

## Implementation Notes
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.McpImageDefinitionRequestDto`
- Response DTO: `com.epam.aidial.deployment.manager.web.dto.McpImageDefinitionDto` (extends `ImageDefinitionDto`, adds `transportType`)
- Transport type enum: `com.epam.aidial.deployment.manager.web.dto.McpTransportTypeDto`
- Related specs: `image-definitions` (base CRUD and base fields), `mcp-deployments`
