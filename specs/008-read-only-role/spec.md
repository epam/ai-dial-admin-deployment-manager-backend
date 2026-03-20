# Feature Specification: Read-Only Admin Role

**Feature Branch**: `008-read-only-role`
**Created**: 2026-03-18
**Status**: Draft
**Input**: User description: "There is a need to provide read-only access to operations in deployment manager. GitHub issue with more details: https://github.com/epam/ai-dial-admin-deployment-manager-backend/issues/149. Example implementation: https://github.com/epam/ai-dial-admin-backend/pull/761. Implementation in ai-dial-admin-backend can be carried over to deployment manager, adding new annotations on non-read operations to controllers and modifying existing tests"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read-Only Admin Views Deployments (Priority: P1)

A read-only administrator authenticates with the deployment manager and can view all entities (deployments, MCP servers, image definitions, image builds, topics, disposable resources, global domain whitelist, config exports) without being able to modify, create, or delete anything.

**Why this priority**: Core value of the feature -- read-only users must be able to see everything without risk of accidental changes.

**Independent Test**: Can be fully tested by authenticating with a READ_ONLY_ADMIN-mapped role and issuing GET requests to all entity endpoints; all return 200 OK with data.

**Acceptance Scenarios**:

1. **Given** a user whose identity provider role maps to READ_ONLY_ADMIN, **When** they request the list of deployments, **Then** the system returns the deployment list successfully.
2. **Given** a user whose identity provider role maps to READ_ONLY_ADMIN, **When** they request any read-only endpoint (GET on deployments, MCP servers, image definitions, image builds, topics, disposable resources, global domain whitelist, config export/preview), **Then** the system returns 200 OK with the expected data.
3. **Given** a user whose identity provider role maps to READ_ONLY_ADMIN, **When** they request the security info endpoint, **Then** the response includes their mapped application roles so the frontend can adapt its UI accordingly.

---

### User Story 2 - Read-Only Admin Is Blocked From Mutating Operations (Priority: P1)

A read-only administrator is prevented from performing any create, update, or delete operation. The system returns 403 Forbidden for all mutating endpoints.

**Why this priority**: Security enforcement -- the entire point of the role is to prevent changes. Equal priority with viewing.

**Independent Test**: Can be tested by authenticating with a READ_ONLY_ADMIN-mapped role and issuing POST/PUT/DELETE requests to all mutating endpoints; all return 403 Forbidden.

**Acceptance Scenarios**:

1. **Given** a user whose identity provider role maps to READ_ONLY_ADMIN, **When** they attempt to create a deployment, **Then** the system returns 403 Forbidden.
2. **Given** a user whose identity provider role maps to READ_ONLY_ADMIN, **When** they attempt to update, delete, or perform any mutating operation on any entity (deployments, MCP servers, image definitions, image builds, disposable resources, global domain whitelist, config import), **Then** the system returns 403 Forbidden.
3. **Given** a user whose identity provider role maps to READ_ONLY_ADMIN, **When** they attempt to access an internal endpoint (e.g., `/api/internal/`), **Then** access is unaffected because internal endpoints are not secured.

---

### User Story 3 - Full Admin Retains Unrestricted Access (Priority: P1)

A full administrator continues to have unrestricted access to all operations, including both read and write. Existing behavior is preserved.

**Why this priority**: Backwards compatibility -- existing admins must not be disrupted.

**Independent Test**: Can be tested by authenticating with a FULL_ADMIN-mapped role and confirming all endpoints (read and write) continue to work as before.

**Acceptance Scenarios**:

1. **Given** a user whose identity provider role maps to FULL_ADMIN, **When** they perform any operation (GET, POST, PUT, DELETE) on any endpoint, **Then** the system processes the request successfully (same behavior as today).
2. **Given** existing configuration that uses the legacy `allowedRoles` property without any `roles-mapping`, **When** a user authenticates with a role listed in `allowedRoles`, **Then** they are granted FULL_ADMIN access (backwards-compatible default).

---

### User Story 4 - Administrator Configures Role Mapping (Priority: P2)

An operator configures the mapping from identity provider roles to application roles (FULL_ADMIN, READ_ONLY_ADMIN) via environment variables / application properties, at both the default level and per identity provider.

**Why this priority**: Required for deployment but not for core enforcement logic.

**Independent Test**: Can be tested by setting `roles-mapping` configuration with different IdP role-to-application role mappings and verifying the correct application roles are resolved.

**Acceptance Scenarios**:

1. **Given** an operator sets `config.rest.security.default.roles-mapping={"adminRole":["FULL_ADMIN"],"viewerRole":["READ_ONLY_ADMIN"]}`, **When** a user authenticates with `viewerRole`, **Then** they receive READ_ONLY_ADMIN application role.
2. **Given** an operator sets provider-specific `providers.azure.roles-mapping={"azureAdmin":["FULL_ADMIN"]}` alongside default roles-mapping, **When** the mappings overlap on the same IdP role, **Then** provider-specific mapping takes precedence.
3. **Given** an operator uses legacy `allowedRoles` configuration (no `roles-mapping`), **When** a user authenticates with a role from `allowedRoles`, **Then** they receive FULL_ADMIN (backwards-compatible behavior).
4. **Given** an operator sets an empty roles mapping and no legacy `allowedRoles`, **When** any user authenticates, **Then** they receive 403 Forbidden (no valid role mapping).

---

### User Story 5 - Security Info Endpoint Returns User Roles (Priority: P2)

The `/api/v1/security-info` endpoint returns the authenticated user's mapped application roles, so the frontend can adapt its UI (e.g., hiding edit buttons for read-only users).

**Why this priority**: Enables frontend to display appropriate UI elements based on user permissions.

**Independent Test**: Can be tested by calling `/api/v1/security-info` with different user roles and verifying the response includes the correct application roles.

**Acceptance Scenarios**:

1. **Given** a user authenticated with FULL_ADMIN role, **When** they call `/api/v1/security-info`, **Then** the response includes `userInfo` with their roles containing "FULL_ADMIN".
2. **Given** a user authenticated with READ_ONLY_ADMIN role, **When** they call `/api/v1/security-info`, **Then** the response includes `userInfo` with their roles containing "READ_ONLY_ADMIN".
3. **Given** authentication mode is "none", **When** anyone calls `/api/v1/security-info`, **Then** the response contains `userInfo` with `id: "anonymousUser"`, `email: null`, and `roles: ["ROLE_ANONYMOUS"]` (Spring Security anonymous authentication context).

---

### Edge Cases

- What happens when a user has both FULL_ADMIN and READ_ONLY_ADMIN mapped roles? FULL_ADMIN takes effect for mutating operations (both roles are granted as authorities; the FULL_ADMIN check succeeds).
- What happens when a user has no mapped roles at all? The system returns 403 Forbidden on all protected endpoints.
- What happens when the `roles-mapping` configuration contains an invalid/unknown application role name? The unknown role is ignored; only recognized roles (FULL_ADMIN, READ_ONLY_ADMIN) are mapped.
- What happens when security mode is "none"? All endpoints are accessible without authentication; role enforcement does not apply. The security-info endpoint returns anonymous user info (`id: "anonymousUser"`, `roles: ["ROLE_ANONYMOUS"]`).
- What happens when provider-specific and default role mappings partially overlap? Provider-specific mapping wins for overlapping keys; non-overlapping keys from default mapping are preserved (merge behavior).
- What happens when security mode is "basic"? `BasicSecurityConfiguration` does NOT have the `@EnableMethodSecurity` annotation, so `@PreAuthorize` / `@FullAdminOnly` checks are not enforced. The reason is that in basic auth mode the user only provides a username and password with no mechanism to assign or derive roles, so every authenticated user is treated as a full admin.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support two application roles: FULL_ADMIN (unrestricted access) and READ_ONLY_ADMIN (read-only access).
- **FR-002**: System MUST allow administrators to configure mapping from identity provider roles to application roles via `roles-mapping` configuration property, as a JSON object mapping IdP role names to lists of application role names.
- **FR-003**: System MUST support `roles-mapping` at both the default level (`config.rest.security.default.roles-mapping`) and per identity provider (`providers.*.roles-mapping`).
- **FR-004**: System MUST merge provider-specific role mappings with default role mappings, where provider-specific mappings take precedence for overlapping keys.
- **FR-005**: System MUST maintain backward compatibility: when only legacy `allowedRoles` is configured (no `roles-mapping`), all allowed roles MUST map to FULL_ADMIN.
- **FR-006**: System MUST block READ_ONLY_ADMIN users from all mutating operations (create, update, delete, import, reload) with 403 Forbidden.
- **FR-007**: System MUST allow READ_ONLY_ADMIN users to access all read-only operations (list, get, export, preview).
- **FR-008**: System MUST expose user's mapped application roles via the `/api/v1/security-info` endpoint.
- **FR-009**: System MUST return 403 Forbidden for users with no valid role mapping.
- **FR-010**: System MUST leave internal endpoints (`/api/internal/**`) and health endpoints unaffected by role enforcement.
- **FR-011**: System MUST deprecate `config.rest.security.default.allowedRoles` and `providers.*.allowed-roles` in favor of `roles-mapping`.

### Key Entities

- **UserRole**: Application-level role enum (FULL_ADMIN, READ_ONLY_ADMIN) representing the level of access granted to an authenticated user.
- **RolesMapping**: Configuration that maps identity provider role names (strings) to sets of application roles (UserRole). Exists at default and per-provider levels.
- **SecurityInfo**: Response object from the security-info endpoint containing user information (ID, email, application roles).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Read-only users can successfully access all read endpoints and receive correct data, with zero mutating operations permitted (100% enforcement).
- **SC-002**: Full-admin users retain full access to all operations with no behavioral change from current system.
- **SC-003**: Existing deployments using legacy `allowedRoles` configuration continue to function without any configuration changes required.
- **SC-004**: Frontend can determine user role via security-info endpoint and adapt UI accordingly within a single additional request.
- **SC-005**: All mutating controller endpoints are protected, verified by security tests covering at least one endpoint in selected controller.

## Assumptions

- The feature follows the same patterns established in `ai-dial-admin-backend` PR #761, adapted for the deployment manager's domain (deployments, MCP servers, image definitions, etc. instead of models, applications, etc.).
- The `@FullAdminOnly` annotation pattern (wrapping `@PreAuthorize("hasAuthority('FULL_ADMIN')")`) is the enforcement mechanism for mutating endpoints.
- Role resolution happens at authentication time (in JwtAuthenticationConverter / OpaqueAuthenticationConverter), not at request time.
- The `SecurityInfoController` is a new controller to be created, returning user info including mapped roles.
- The `roles-mapping` configuration follows the same JSON format as the reference implementation: `{"idpRole":["FULL_ADMIN"],"viewerRole":["READ_ONLY_ADMIN"]}`.
- A `RolesMappingResolver` resolves the effective role mapping considering default and provider-specific configurations with the precedence rules defined in FR-004/FR-005.
- A `UserRolesResolver` translates granted authorities (IdP roles) to application roles (UserRole) based on the resolved mapping, replacing the current pass-through role filtering.
- `BasicSecurityConfiguration` (security mode `basic`) intentionally omits `@EnableMethodSecurity`. In basic auth mode there is no role information available (only username/password), so all authenticated users are treated as full admins. Role-based access control only applies to OAuth2/JWT/opaque-token security modes.
