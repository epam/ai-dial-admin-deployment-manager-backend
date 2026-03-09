# API Conventions

## Purpose
This spec describes the cross-cutting REST API contract — versioning, base path routing, error response structure, pagination, and OpenAPI documentation. These conventions apply to all endpoints.

Status: **Implemented**

## Key Terms
- **ErrorView**: The standard error response DTO. All non-2xx responses use this structure.
- **traceparent**: W3C Trace Context identifier (format: `00-{trace-id}-{span-id}-{trace-flags}`, e.g. `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`) included in all error responses for distributed tracing correlation.
- **API version**: All public endpoints are under the `/api/v1/` path prefix. Internal endpoints are under `/api/internal/v1/`.
- **DefaultExceptionHandler**: The global `@RestControllerAdvice` that maps all exceptions to `ErrorView` responses.

## Error Response Structure

All error responses use `ErrorView` with these fields:

| Field | Type | Notes |
|---|---|---|
| `path` | String | Request servlet path |
| `method` | String | HTTP method |
| `status` | Integer | HTTP status code |
| `error` | String | HTTP status reason phrase |
| `message` | String | Sanitized error message (max 2000 chars, stack traces stripped) |
| `traceparent` | String | W3C Trace Context value from OpenTelemetry context |

## Requirements

### Requirement: All public API endpoints versioned under /api/v1/
The system SHALL expose all public REST endpoints under the `/api/v1/` path prefix. Internal endpoints (not requiring authentication) are under `/api/internal/v1/`.

Status: **Implemented**

#### Scenario: Versioned routing
- **WHEN** a client calls `/api/v1/deployments`
- **THEN** the request is routed to the DeploymentController

#### Scenario: Internal endpoints unauthenticated
- **WHEN** a client calls `/api/internal/v1/**`
- **THEN** the request is permitted without a bearer token (no auth required for internal paths)

### Requirement: All error responses use the ErrorView contract
The system SHALL return the `ErrorView` structure for all non-2xx responses, populated by `DefaultExceptionHandler` (`@RestControllerAdvice`).

Status: **Implemented**

#### Scenario: Validation error
- **WHEN** a request fails bean validation (`@Valid` / constraint violations)
- **THEN** the response is HTTP 400 with an `ErrorView` body listing all field-level violations

#### Scenario: Not found
- **WHEN** a requested resource does not exist (`EntityNotFoundException` or `NoResourceFoundException`)
- **THEN** the response is HTTP 404 with an `ErrorView` body

#### Scenario: Method not allowed
- **WHEN** an HTTP method is not supported for the given path
- **THEN** the response is HTTP 405 with an `ErrorView` body

#### Scenario: Image in use conflict
- **WHEN** an operation attempts to delete or replace an image that is actively referenced by a deployment (`ImageInUseException`)
- **THEN** the response is HTTP 409 with an `ErrorView` body

#### Scenario: Traceparent always present
- **WHEN** any error response is returned
- **THEN** the `traceparent` field is populated with the current W3C Trace Context value from OpenTelemetry

#### Scenario: Async stream closed by client
- **WHEN** an SSE client disconnects (`AsyncRequestNotUsableException`)
- **THEN** a 200 OK text response is returned; the stream is closed gracefully without error propagation

### Requirement: Error messages are sanitized
The system SHALL sanitize error messages to prevent leaking stack traces or internal implementation details. Messages are capped at 2000 characters.

Status: **Implemented**

#### Scenario: Long message truncated
- **WHEN** an exception has a message longer than 2000 characters
- **THEN** the `message` field is truncated to 2000 characters

#### Scenario: Stack traces not exposed
- **WHEN** any server-side error occurs
- **THEN** the `message` field contains a sanitized description: if the raw message contains Java stack trace lines (`\n\tat ` or `\n at `), only the first line is kept

#### Scenario: General server errors return opaque message
- **WHEN** an unexpected `Exception` is caught by the global handler
- **THEN** the `message` field is `"Internal server error"` (not the original exception message)

#### Scenario: Database errors return opaque message
- **WHEN** a `DatabaseException` is caught
- **THEN** the `message` field is `"Database error occurred"` (not the original cause)

### Requirement: Pagination conventions
The system MUST use cursor-based pagination for paginated public endpoints. There SHALL be no offset-based pagination in the public REST API; Spring Data `Page<T>` is used only internally for batch processing (e.g., startup reconciliation).

Two cursor patterns exist:
- **Link-header cursor** (HuggingFace): uses `pageUrl` query parameter and `nextPageUrl`/`prevPageUrl` response fields. The `limit` parameter controls page size (default: 100, min: 1, max: 1000).
- **MCP protocol cursor**: uses `nextCursor` query parameter following the Model Context Protocol specification.

Most list endpoints (deployments, image definitions, topics, domain whitelist) return **complete collections without pagination**.

Status: **Implemented**

#### Scenario: HuggingFace model search with pagination
- **WHEN** `GET /api/v1/huggingface/models?search=bert&limit=50` is called
- **THEN** up to 50 models are returned; `nextPageUrl` and `prevPageUrl` provide cursors for navigation

#### Scenario: HuggingFace default page size
- **WHEN** `GET /api/v1/huggingface/models` is called without a `limit` parameter
- **THEN** 100 models are returned by default

#### Scenario: MCP tool listing with cursor
- **WHEN** `GET /api/v1/deployments/mcp/{deploymentId}/tools?nextCursor={cursor}` is called
- **THEN** the next page of tools is returned; the response includes a `nextCursor` field for continuation

#### Scenario: Non-paginated list endpoint
- **WHEN** `GET /api/v1/deployments` is called
- **THEN** all deployments are returned in a single response without pagination controls

### Requirement: OpenAPI/Swagger documentation is published
The system SHALL expose Swagger UI and OpenAPI specification for all REST endpoints via SpringDoc. Swagger UI authorization can be optionally disabled via `config.rest.security.disable-swagger-authorization`.

Status: **Implemented**

#### Scenario: Swagger UI accessible
- **WHEN** the application is running
- **THEN** Swagger UI is accessible at the SpringDoc default path (`/swagger-ui.html` or `/swagger-ui/index.html`) and OpenAPI JSON at `/v3/api-docs`

## Implementation Notes
- Global exception handler: `com.epam.aidial.deployment.manager.web.handler.DefaultExceptionHandler` (`@RestControllerAdvice`, annotated with `@LogExecution`)
- Error DTO: `com.epam.aidial.deployment.manager.web.handler.ErrorView`
  - Message sanitization: strips Java stack trace lines, caps at 2000 characters
  - `traceparent` populated via `com.epam.aidial.deployment.manager.utils.TraceContextUtils.formatTraceParent()` from OTel context
- Traceparent also set as an HTTP response header by `com.epam.aidial.deployment.manager.configuration.logging.CorrelationIdInterceptor` (registered via `WebMvcConfig`)
- OpenAPI: SpringDoc `springdoc-openapi-starter-webmvc-ui` 2.8.5
- Security path rules (from `SecurityConfiguration`):
  - `/api/v1/**` — requires authentication
  - `/api/v1/health/**`, `/api/internal/**` — permitted without auth
  - `/swagger-ui/**`, `/v3/api-docs/**` — permitted without auth when `config.rest.security.disable-swagger-authorization=true`
- Related specs: `security`, `observability-and-logging`
