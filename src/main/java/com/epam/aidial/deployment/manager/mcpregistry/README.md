# MCP Registry Module

This module acts as a **proxy** for the [official MCP Registry API](https://modelcontextprotocol.io) (registry.modelcontextprotocol.io). It exposes REST endpoints in the same style as the rest of the Deployment Manager (e.g. Hugging Face integration), so the admin panel can discover and use MCP servers from the registry without calling the upstream API directly.

## What Was Done

- **Client** – HTTP client that calls the external MCP Registry (list servers, list versions, get version). Uses the project’s shared OkHttp and JSON configuration.
- **Caching** – Responses for list-servers and get-version are cached (Caffeine) with a configurable duration to reduce upstream calls.
- **Models** – POJOs aligned with the MCP Registry OpenAPI schema: `ServersRequest`, `ServerListResponse`, `ServerResponse`, `ServerDetail`, `ServerListMetadata`, `Repository`, `Icon`, `KeyValueInput`, `LocalTransport`, `RemoteTransport`, `Package`, etc.
- **REST API** – Controller under `/api/v1/mcp-registry/servers` with:
  - **GET** (query params): list servers (cursor, limit, search, updated_since, version).
  - **GET** `/{namespace}/{name}/versions`: list all versions of a server (path uses two segments so a slash in the server name does not cause 400).
  - **GET** `/{namespace}/{name}/versions/{version}`: get a specific server version.
  - **POST** `/list`: list servers with all parameters in the request body (`ServersRequest`).
  - **POST** `/versions`: list versions or get one version with body (`ServerVersionsRequest`). If `version` is set, returns that version; otherwise returns the list of versions. Request body uses `serverName` (e.g. `ai.com.mcp/petstore`) and optional `version`.
- **Configuration** – `app.mcp-registry.base-url` and `app.mcp-registry.cache-duration` (env: `MCP_REGISTRY_BASE_URL`, `MCP_REGISTRY_CACHE_DURATION`).
- **Tests** – Controller tests (`McpRegistryControllerTest`) and client tests (`McpRegistryClientTest`), plus JSON fixtures under `src/test/resources/mcp-registry/`.
- **HTTP examples** – `docs/rest-collection/mcp-registry.http` for manual calls.

## Package Layout

- **client** – `McpRegistryClient`, `McpRegistryClientException` (HTTP calls to upstream).
- **model** – Request/response and schema POJOs for the registry API.
- **properties** – `McpRegistryProperties` (base URL, cache duration).
- **service** – `McpRegistryService` (orchestrates client, caching).
- **web.controller** – `McpRegistryController` (REST endpoints).
- **web.dto** – `ServerVersionsRequest` (POST body for list/get versions: `serverName`, optional `version`).

## Configuration

In `application.yml` (overridable via environment):

```yaml
app:
  mcp-registry:
    base-url: https://registry.modelcontextprotocol.io
    cache-duration: 15m
```

- `MCP_REGISTRY_BASE_URL` – upstream registry base URL.
- `MCP_REGISTRY_CACHE_DURATION` – cache TTL (e.g. `15m`).

No new external dependencies were added; the module uses existing project dependencies (OkHttp, Jackson, Caffeine, Spring Web).
