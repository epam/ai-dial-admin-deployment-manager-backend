# Security

## Purpose
This spec describes authentication and authorization for the REST API — configurable security modes and multi-provider JWT/OIDC validation.

Status: **Implemented**

## Key Terms
- **Security mode**: The operational auth mode configured via `config.rest.security.mode`: `none` (no authentication) or `oidc`.
- **OIDC provider**: A JWT issuer configuration (issuer URI + JWK set). Multiple providers can be configured simultaneously.
- **Multi-issuer validation**: The ability to accept valid JWTs from any of multiple configured issuers.
- **Public paths**: Endpoints exempt from authentication in OIDC mode: health (`/api/v1/health/**`) and internal API (`/api/internal/**`). Swagger UI (`/swagger-ui/**`, `/v3/api-docs/**`) is conditionally public — only when `config.rest.security.disable-swagger-authorization=true`.
- **STATELESS**: The session management mode — no server-side HTTP session is created; authentication is re-validated on every request.

## Requirements

### Requirement: Configurable security modes
The system SHALL support security modes via `config.rest.security.mode`: `none` or `oidc`.

Status: **Implemented**

#### Scenario: No-security mode
- **WHEN** `config.rest.security.mode=none` (or the security configuration is absent)
- **THEN** all API endpoints are accessible without any authentication

#### Scenario: OIDC mode
- **WHEN** `config.rest.security.mode=oidc`
- **THEN** all non-public API endpoints require a valid JWT bearer token

### Requirement: Public paths are always accessible without authentication
Health and internal API paths SHALL be exempt from authentication even in OIDC mode. Swagger UI paths are conditionally exempt.

Status: **Implemented**

#### Scenario: Health endpoint accessible without auth
- **WHEN** `GET /api/v1/health/**` is called in OIDC mode
- **THEN** the request is permitted without an Authorization header

#### Scenario: Internal API accessible without auth
- **WHEN** any request to `/api/internal/**` is made in OIDC mode
- **THEN** the request is permitted without an Authorization header

#### Scenario: Swagger UI accessible without auth when configured
- **WHEN** `GET /swagger-ui/**` or `GET /v3/api-docs/**` is called in OIDC mode with `config.rest.security.disable-swagger-authorization=true`
- **THEN** the request is permitted without an Authorization header

#### Scenario: Swagger UI requires auth when not explicitly disabled
- **WHEN** `GET /swagger-ui/**` or `GET /v3/api-docs/**` is called in OIDC mode with `config.rest.security.disable-swagger-authorization=false` (or unset)
- **THEN** the request requires a valid JWT bearer token

### Requirement: JWT/OIDC multi-provider authentication
In OIDC mode, the system SHALL validate JWT tokens from multiple configured OIDC providers simultaneously, accepting any valid token from any configured issuer.

Status: **Implemented**

#### Scenario: Valid token from any provider
- **WHEN** an API request includes a valid JWT from any configured issuer (Azure AD, Keycloak, Auth0, Okta, Cognito)
- **THEN** the request is authenticated and proceeds

#### Scenario: Invalid or expired token
- **WHEN** an API request includes an expired, tampered, or structurally invalid JWT
- **THEN** the system responds with 401

#### Scenario: Missing Authorization header
- **WHEN** an API request to a secured endpoint is made without an `Authorization` header (in OIDC mode)
- **THEN** the system responds with 401

### Requirement: Azure Identity SDK integration for Azure AD
The system SHALL support Azure Active Directory as an OIDC issuer using the Azure Identity SDK.

Status: **Implemented**

#### Scenario: Azure AD JWT validation
- **WHEN** a JWT issued by Azure AD is presented with `config.rest.security.mode=oidc`
- **THEN** the token is validated against the configured Azure AD tenant and accepted if valid

### Requirement: Authentication failures return 401 with ErrorView
The system SHALL respond with HTTP 401 and a standard `ErrorView` body for any authentication failure.

Status: **Implemented**

#### Scenario: Authentication failure response format
- **WHEN** authentication fails for any reason
- **THEN** the response status is 401 and the body follows the `ErrorView` contract (path, method, status, error, message, traceparent)

## Implementation Notes
- Security configuration: `com.epam.aidial.deployment.manager.configuration.*`
- Security handlers: `com.epam.aidial.deployment.manager.web.security.*`
- Config property: `config.rest.security.mode` (`none` | `oidc`)
- Session policy: STATELESS (no server-side HTTP session)
- Always-public paths: `/api/v1/health/**`, `/api/internal/**`
- Conditionally-public paths: `/swagger-ui/**`, `/v3/api-docs/**` (only when `config.rest.security.disable-swagger-authorization=true`)
- Azure Identity SDK: azure-identity
- Related spec: `api-conventions`
