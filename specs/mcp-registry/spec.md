# MCP Registry

## Purpose
This spec describes the MCP Registry browser — a read-only integration with an external MCP server registry that allows clients to discover available MCP servers, their versions, and full version metadata.

Status: **Implemented**

## Key Terms
- **MCP Registry**: An external service hosting a catalog of available MCP server packages. The backend proxies queries to this registry.
- **Server**: A namespaced MCP server package identified by `{namespace}/{name}` (e.g., `github/github`).
- **Version**: A specific published version of a server. Each version includes metadata (image reference, protocol version, tools, etc.).
- **McpRegistryProperties**: Configuration for the external registry URL.

## Requirements

### Requirement: List available MCP registry servers
The system SHALL proxy list requests to the external MCP registry and return available servers with summary information.

Status: **Implemented**

#### Scenario: List all servers (GET)
- **WHEN** `GET /api/v1/mcp-registry/servers` is called
- **THEN** available servers are returned from the external registry

#### Scenario: List servers (POST variant)
- **WHEN** `POST /api/v1/mcp-registry/servers/list` is called with an optional filter body
- **THEN** servers matching the filter are returned

### Requirement: Get server versions
The system SHALL return all published versions of a specific MCP server.

Status: **Implemented**

#### Scenario: List versions by namespace and name (GET)
- **WHEN** `GET /api/v1/mcp-registry/servers/{namespace}/{name}/versions` is called
- **THEN** all published versions of that server are returned

#### Scenario: List versions (POST variant)
- **WHEN** `POST /api/v1/mcp-registry/servers/versions` is called with `namespace` and `name` in the body
- **THEN** all published versions of that server are returned

### Requirement: Get a specific server version
The system SHALL return full metadata for a specific version of a server.

Status: **Implemented**

#### Scenario: Get version by namespace, name, and version
- **WHEN** `GET /api/v1/mcp-registry/servers/{namespace}/{name}/versions/{version}` is called
- **THEN** the full metadata for that specific version is returned (image reference, protocol version, description, tools, etc.)

#### Scenario: Non-existent server or version
- **WHEN** the requested server or version does not exist in the external registry
- **THEN** the system returns an appropriate error response (404 or registry-proxied error)

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.registry.mcp.web.controller.McpRegistryController` (path: `/api/v1/mcp-registry/servers`)
- Service: `com.epam.aidial.deployment.manager.registry.mcp.service.McpRegistryService`
- Client: `com.epam.aidial.deployment.manager.registry.mcp.client.McpRegistryClient`
- Models: `com.epam.aidial.deployment.manager.registry.mcp.model.*` (Package, Repository, ServerDetail, etc.)
- Properties: `com.epam.aidial.deployment.manager.registry.mcp.properties.McpRegistryProperties`
- Web DTOs: `com.epam.aidial.deployment.manager.registry.mcp.web.dto.*` (ServerResponseDto, ServerListResponseDto, etc.)
- This is a proxy/browser — the registry is read-only; no create/update/delete operations
