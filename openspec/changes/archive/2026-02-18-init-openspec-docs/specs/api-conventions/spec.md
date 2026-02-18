# API Conventions

## Purpose
This spec describes the cross-cutting REST API contract — versioning, error response structure, and OpenAPI documentation. These conventions apply to all endpoints.

Status: **Implemented**

## Key Terms
- **ErrorView**: The standard error response DTO. All non-2xx responses use this structure.
- **traceparent**: W3C Trace Context identifier (format: `00-{trace-id}-{span-id}-{trace-flags}`) included in all error responses for distributed tracing correlation.
- **API version**: All endpoints are under `/api/v1/` path prefix.

## Error Response Structure

All error responses use `ErrorView` with these fields:

| Field | Type | Notes |
|---|---|---|
| `path` | String | Request path |
| `method` | String | HTTP method |
| `status` | Integer | HTTP status code |
| `error` | String | HTTP status reason phrase |
| `message` | String | Sanitized error message (max 2000 chars, no stack traces) |
| `traceparent` | String | W3C Trace Context header value |

## ADDED Requirements

### Requirement: All API endpoints versioned under /api/v1/
The system SHALL expose all public REST endpoints under the `/api/v1/` path prefix.

Status: **Implemented**

#### Scenario: Versioned routing
- **WHEN** a client calls `/api/v1/deployments`
- **THEN** the request is routed to the DeploymentController

### Requirement: All error responses use the ErrorView contract
The system SHALL return the `ErrorView` structure for all non-2xx responses, populated by a global `@RestControllerAdvice` exception handler.

Status: **Implemented**

#### Scenario: Validation error
- **WHEN** a request fails bean validation
- **THEN** the response is HTTP 400 with an `ErrorView` body including all required fields

#### Scenario: Not found
- **WHEN** a requested resource does not exist
- **THEN** the response is HTTP 404 with an `ErrorView` body

#### Scenario: Traceparent always present
- **WHEN** any error response is returned
- **THEN** the `traceparent` field is populated with the current W3C Trace Context value

### Requirement: Error messages are sanitized
The system SHALL sanitize error messages to prevent leaking stack traces or internal implementation details. Messages are capped at 2000 characters.

Status: **Implemented**

#### Scenario: Long message truncated
- **WHEN** an exception has a message longer than 2000 characters
- **THEN** the `message` field is truncated to 2000 characters

#### Scenario: Stack traces not exposed
- **WHEN** any server-side error occurs
- **THEN** the `message` field contains a sanitized description without stack trace text

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
The system SHALL expose Swagger UI and OpenAPI specification for all REST endpoints via SpringDoc.

Status: **Implemented**

#### Scenario: Swagger UI accessible
- **WHEN** the application is running
- **THEN** Swagger UI is accessible at the SpringDoc default path

## Implementation Notes
- Global exception handler: `com.epam.aidial.deployment.manager.web.handler.DefaultExceptionHandler` (`@RestControllerAdvice`, annotated with `@LogExecution`)
- Error DTO: `com.epam.aidial.deployment.manager.web.handler.ErrorView`
- OpenAPI: SpringDoc 2.8.5 (`springdoc-openapi-starter-webmvc-ui`)
- Related specs: `security`, `observability-and-logging`
