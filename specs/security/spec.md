# Security

## Purpose
This spec describes authentication and authorization for the REST API — configurable security modes, multi-provider JWT/OIDC validation, and role-based access control.

Status: **Implemented**

## Key Terms
- **Security mode**: The operational auth mode configured via `config.rest.security.mode`: `none` (no authentication), `basic` (HTTP Basic Auth), or `oidc` (JWT/OIDC).
- **OIDC provider**: A JWT issuer configuration (issuer URI + JWK set). Multiple providers can be configured simultaneously.
- **Multi-issuer validation**: The ability to accept valid JWTs from any of multiple configured issuers.
- **Application role**: An internal role (`FULL_ADMIN` or `READ_ONLY_ADMIN`) mapped from identity provider roles at authentication time.
- **Roles mapping**: Configuration that maps identity provider role names to application roles, supporting both default and per-provider levels.
- **Public paths**: Endpoints exempt from authentication in OIDC/basic mode: health (`/api/v1/health/**`) and internal API (`/api/internal/**`). Swagger UI (`/swagger-ui/**`, `/v3/api-docs/**`) is conditionally public — only when `config.rest.security.disable-swagger-authorization=true`.
- **STATELESS**: The session management mode — no server-side HTTP session is created; authentication is re-validated on every request.

## Requirements

### Requirement: Configurable security modes
The system SHALL support three security modes via `config.rest.security.mode`: `none`, `basic`, or `oidc`.

Status: **Implemented**

#### Scenario: No-security mode
- **WHEN** `config.rest.security.mode=none` (or the security configuration is absent)
- **THEN** all API endpoints are accessible without any authentication
- **AND** role enforcement does not apply

#### Scenario: Basic auth mode
- **WHEN** `config.rest.security.mode=basic`
- **THEN** all non-public API endpoints require HTTP Basic Authentication with the configured username and password (`spring.security.user.name` / `spring.security.user.password`)

#### Scenario: OIDC mode
- **WHEN** `config.rest.security.mode=oidc`
- **THEN** all non-public API endpoints require a valid JWT bearer token
- **AND** role-based access control is enforced

### Requirement: Public paths are always accessible without authentication
Health and internal API paths SHALL be exempt from authentication even in OIDC/basic mode. Swagger UI paths are conditionally exempt.

Status: **Implemented**

#### Scenario: Health endpoint accessible without auth
- **WHEN** `GET /api/v1/health/**` is called in OIDC or basic mode
- **THEN** the request is permitted without an Authorization header

#### Scenario: Internal API accessible without auth
- **WHEN** any request to `/api/internal/**` is made in OIDC or basic mode
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

### Requirement: Application roles and role-based access control
The system SHALL support two application roles: `FULL_ADMIN` (unrestricted access) and `READ_ONLY_ADMIN` (read-only access). Identity provider roles are mapped to application roles at authentication time.

Status: **Implemented** (feature 008-read-only-role)

#### Scenario: FULL_ADMIN has unrestricted access
- **WHEN** a user authenticates with a role mapped to `FULL_ADMIN`
- **THEN** they can access all endpoints (GET, POST, PUT, DELETE)

#### Scenario: READ_ONLY_ADMIN is blocked from mutating operations
- **WHEN** a user authenticates with a role mapped to `READ_ONLY_ADMIN`
- **THEN** all read-only endpoints (GET, export, preview) return 200 OK
- **AND** all mutating endpoints (create, update, delete, import, deploy) return 403 Forbidden

#### Scenario: No valid role mapping
- **WHEN** a user authenticates but none of their IdP roles are mapped to an application role
- **THEN** all protected endpoints return 403 Forbidden

### Requirement: Configurable role mapping
Administrators SHALL configure the mapping from identity provider roles to application roles via `roles-mapping` configuration at both default and per-provider levels.

#### Scenario: Default roles-mapping
- **WHEN** `config.rest.security.default.roles-mapping={"adminRole":["FULL_ADMIN"],"viewerRole":["READ_ONLY_ADMIN"]}` is configured
- **THEN** users with `viewerRole` receive `READ_ONLY_ADMIN` application role

#### Scenario: Provider-specific override
- **WHEN** `providers.azure.roles-mapping={"azureAdmin":["FULL_ADMIN"]}` is configured alongside default roles-mapping
- **THEN** provider-specific mapping takes precedence for overlapping keys; non-overlapping keys from default mapping are preserved

#### Resolution precedence (per provider):
1. Provider `roles-mapping` merged with default `roles-mapping` (provider wins on overlap)
2. Default `roles-mapping` alone
3. Empty → no roles → 403

### Requirement: Security info endpoint
The system SHALL expose the authenticated user's mapped application roles via `GET /api/v1/security-info`.

#### Scenario: OIDC mode returns user info
- **WHEN** an authenticated user calls `GET /api/v1/security-info`
- **THEN** the response contains `userInfo` with `id` (principal), `email` (if present), and `roles` (application role names)

#### Scenario: None mode returns anonymous info
- **WHEN** `config.rest.security.mode=none` and anyone calls `GET /api/v1/security-info`
- **THEN** the response contains `userInfo` with `id: "anonymousUser"`, `email: null`, `roles: ["ROLE_ANONYMOUS"]`

### Requirement: Azure Identity SDK integration for Azure AD
The system SHALL support Azure Active Directory as an OIDC issuer using the Azure Identity SDK. Three credential types are supported: managed identity, Azure CLI, and client-secret (service principal).

Status: **Implemented**

#### Scenario: Azure AD JWT validation
- **WHEN** a JWT issued by Azure AD is presented with `config.rest.security.mode=oidc`
- **THEN** the token is validated against the configured Azure AD tenant and accepted if valid

### Requirement: Authentication failures return 401, authorization failures return 403
The system SHALL respond with HTTP 401 for authentication failures and HTTP 403 for authorization failures (valid auth but insufficient role).

Status: **Implemented**

#### Scenario: Authentication failure response
- **WHEN** authentication fails (missing/invalid/expired token)
- **THEN** the response status is 401 and the body follows the `ErrorView` contract

#### Scenario: Authorization failure response
- **WHEN** authentication succeeds but the user lacks required authority (e.g., READ_ONLY_ADMIN on a `@FullAdminOnly` endpoint)
- **THEN** the response status is 403 and the body follows the `ErrorView` contract

### Requirement: API-key authentication via DIAL Core delegation
In `oidc` mode with `config.rest.security.api-key.enabled=true`, the system SHALL also accept `Api-Key` headers and validate them by calling DIAL Core's `/v1/user/info` endpoint. Validation handles both project keys and per-request keys (project-key roots and JWT roots). Roles SHALL be mapped to application roles via `config.rest.security.api-key.roles-mapping` for project-key responses, or via `config.rest.security.default.roles-mapping` for JWT-rooted `userClaims` responses.

Status: **Implemented** (feature 018-api-key-via-core-userinfo)

#### Scenario: Valid project-key, no Authorization header
- **WHEN** a request includes `Api-Key: <valid-key>` and no `Authorization` header
- **AND** Core's `/v1/user/info` responds `{"roles":[...], "project":"<p>"}` (project-key auth, or per-request key minted from a project-key root)
- **THEN** DM maps `roles` to application roles via `api-key.roles-mapping`
- **AND** sets the Spring principal to `project`
- **AND** sets `UserSecurityDetails.email` to null

#### Scenario: JWT-rooted per-request key (Core → DM-MCP → DM chain)
- **WHEN** a request includes `Api-Key: <per-request-key>` whose Core root was a JWT
- **AND** Core's `/v1/user/info` responds `{"roles":[...], "userClaims":{...}}` (no `project`)
- **THEN** DM maps `roles` to application roles via `config.rest.security.default.roles-mapping`
- **AND** sets the Spring principal to `userClaims[default.principal-claim]`
- **AND** sets `UserSecurityDetails.email` to `userClaims[default.email-claim]` (or null if absent)
- **AND** audit fields (`deployment.author`, `imageDefinition.author`, `AuditRevision.email`) record the end-user email

#### Scenario: JWT-rooted per-request key with missing principal claim
- **WHEN** Core's `/v1/user/info` response has `userClaims` but lacks the configured `default.principal-claim`
- **THEN** DM returns 401 (`Malformed Core user-info response`)
- **AND** the failure is NOT cached

#### Scenario: Response carries neither `project` nor `userClaims`
- **WHEN** Core's `/v1/user/info` returns 200 with neither field populated
- **THEN** DM returns 401 (`Malformed Core user-info response`)
- **AND** the failure is NOT cached

#### Scenario: Cross-mapping isolation
- **WHEN** a role name appears in `api-key.roles-mapping` but not in `default.roles-mapping`, and Core returns that role on the JWT-rooted (`userClaims`) path
- **THEN** the request is authenticated but receives no application authorities; protected endpoints return 403
- **AND** symmetrically: a role only in `default.roles-mapping` is not granted on the project-key path

#### Scenario: Invalid api-key
- **WHEN** Core's `/v1/user/info` responds non-200 for the supplied key
- **THEN** DM returns 401 with the standard `ErrorView` body
- **AND** the failure is NOT cached

#### Scenario: Both Api-Key and Authorization present
- **WHEN** both `Api-Key` and `Authorization` headers are present
- **THEN** the JWT/opaque-token path handles the request exclusively; the `Api-Key` header is ignored (JWT-first precedence)
- **AND** DM does NOT call Core's `/v1/user/info`

#### Scenario: Api-key role with no mapping
- **WHEN** Core returns roles that have no entry in the applicable mapping (`api-key.roles-mapping` for project-key path, `default.roles-mapping` for JWT-root path)
- **THEN** the request is authenticated but receives no application authorities
- **AND** any protected endpoint returns 403

#### Scenario: Core unreachable
- **WHEN** Core's `/v1/user/info` is unreachable (timeout / connection refused)
- **THEN** DM returns 503 with the standard `ErrorView` body
- **AND** the failure is NOT cached

#### Scenario: Cache hit
- **WHEN** the same valid api-key is presented N times within `cache-ttl-seconds`
- **THEN** DM calls Core's `/v1/user/info` exactly once and serves the remaining N-1 requests from the in-process cache
- **AND** the cache key is `sha256(apiKey)` so raw key material is not retained

#### Scenario: Startup probe fails
- **WHEN** `api-key.enabled=true`, `api-key.startup-probe=true`, and `core-url` is unreachable at startup
- **THEN** DM fails to start with a clear error log

#### Scenario: Both roles mappings empty rejected at startup
- **WHEN** `api-key.enabled=true` and **both** `api-key.roles-mapping` and `default.roles-mapping` are blank or parse to empty objects
- **THEN** DM fails to start with a clear error message naming both properties
- **AND** the rationale is that with both empty, every authenticated api-key caller would receive 403, so the feature would be enabled but unusable. Either alone is sufficient: operators serving only project-key callers can leave `default.roles-mapping` empty; operators whose api-key traffic is purely JWT-rooted per-request keys can leave `api-key.roles-mapping` empty

## Mutating endpoints protected by @FullAdminOnly

| Controller | Method | Path | Operation |
|---|---|---|---|
| DeploymentController | POST | `/api/v1/deployments` | createDeployment |
| DeploymentController | POST | `/api/v1/deployments/duplicate` | duplicateDeployment |
| DeploymentController | POST | `/api/v1/deployments/change-image` | changeImage |
| DeploymentController | PUT | `/api/v1/deployments/{id}` | updateDeployment |
| DeploymentController | DELETE | `/api/v1/deployments/{id}` | deleteDeployment |
| DeploymentController | POST | `/api/v1/deployments/{id}/deploy` | deploy |
| DeploymentController | POST | `/api/v1/deployments/{id}/undeploy` | undeploy |
| ConfigController | POST | `/api/v1/configs/import` | importConfig |
| ImageDefinitionController | POST | `/api/v1/images/definitions` | createImageDefinition |
| ImageDefinitionController | PUT | `/api/v1/images/definitions/{id}` | updateImageDefinition |
| ImageDefinitionController | DELETE | `/api/v1/images/definitions/{id}` | deleteImageDefinition |
| ImageBuildController | POST | `/api/v1/images/builds` | buildImage |
| DisposableResourceController | POST | `/api/v1/disposable/clean` | clean |
| GlobalDomainWhitelistController | POST | `/api/v1/global-whitelist/image-build` | updateDomainWhitelistForImageBuild |
| McpController | POST | `/api/v1/deployments/mcp/{deploymentId}/call-tool` | callTool |

## Implementation Notes
- Security configurations: `OidcSecurityConfiguration` (oidc), `BasicSecurityConfiguration` (basic), `NoneSecurityConfiguration` (none)
- Public paths extracted to `PublicPathsResolver` (shared by oidc/basic)
- Role mapping: `RolesMappingResolver` (Spring component), `UserRolesResolver` (per-provider instance)
- JWT decoder abstraction: `NimbusJwtDecoderResolver` interface (overridden in tests)
- Config property: `config.rest.security.mode` (`none` | `basic` | `oidc`)
- Roles mapping: `config.rest.security.default.roles-mapping` (JSON string), `providers.*.roles-mapping` (JSON string)
- Basic auth credentials: `spring.security.user.name`, `spring.security.user.password`
- Session policy: STATELESS (no server-side HTTP session)
- Always-public paths: `/api/v1/health/**`, `/api/internal/**`
- Conditionally-public paths: `/swagger-ui/**`, `/v3/api-docs/**` (only when `config.rest.security.disable-swagger-authorization=true`)
- Azure Identity SDK: azure-identity 1.18.0; credential types in `AzureAuthConfig`: managed identity, CLI, client-secret
- API-key authentication (oidc mode only, opt-in via `config.rest.security.api-key.enabled=true`): `ApiKeyAuthenticationFilter` (under `web/security/apikey/`, registered as a `@Bean` inside `OidcSecurityConfiguration` and installed via `addFilterBefore(BearerTokenAuthenticationFilter)`); introspector `CoreApiKeyIntrospector` calls Core's `/v1/user/info` and parses either `{project, roles}` (project-key auth, including per-request keys minted from a project-key root) or `{userClaims, roles}` (JWT-rooted per-request keys from the Core → DM-MCP → DM chain); results cached in `ApiKeyCache` (Caffeine, key=`sha256(apiKey)`, TTL from `api-key.cache-ttl-seconds`); role mapping via `ApiKeyAuthorityResolver` which routes by response shape — project-key responses resolve through `api-key.roles-mapping`, `userClaims` responses through `default.roles-mapping` (no cross-mapping); principal = `project` or `userClaims[default.principal-claim]` carried by `ApiKeyAuthenticationToken`; `UserSecurityDetails.email` populated from `userClaims[default.email-claim]` for JWT-root callers so audit fields record the end user.
- Related specs: `api-conventions`, `008-read-only-role`, `018-api-key-via-core-userinfo`
