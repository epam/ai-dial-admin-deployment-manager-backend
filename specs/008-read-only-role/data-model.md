# Data Model: Read-Only Admin Role

No database entities or migrations are required for this feature. All data structures are in-memory configuration and DTOs.

## Configuration Entities

### UserRole (enum)

Application-level role representing a user's access level.

| Value | Description |
|---|---|
| `FULL_ADMIN` | Unrestricted access to all operations |
| `READ_ONLY_ADMIN` | Read-only access; mutating operations return 403 |

### RolesMapping (configuration property)

Maps identity provider role names to application roles.

| Property | Type | Default | Description |
|---|---|---|---|
| `config.rest.security.default.roles-mapping` | `String` (JSON) | `"{}"` | Default IdP-role → app-role mapping, parsed as `Map<String, Set<UserRole>>` by ObjectMapper |
| `providers.*.roles-mapping` | `String` (JSON) | — | Per-provider IdP-role → app-role mapping, validated and parsed during provider initialization |

**Resolution precedence** (evaluated per provider):
1. Provider `roles-mapping` merged with default `roles-mapping` (provider wins on overlap)
2. Provider `allowed-roles` + default `allowedRoles` → all mapped to FULL_ADMIN
3. Default `roles-mapping` alone
4. Default `allowedRoles` → all mapped to FULL_ADMIN
5. Empty → no roles → 403

## DTO Entities

### SecurityInfoDto

Response from `/api/v1/security-info`.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `userInfo` | `UserInfoDto` | No | Always present; in "none" mode contains anonymous user info |

### UserInfoDto

User information nested within SecurityInfoDto.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | `String` | Yes | Principal identifier from token (null when security mode is "none" is not expected — anonymous auth provides "anonymousUser") |
| `email` | `String` | Yes | Email extracted from token claims |
| `roles` | `Set<String>` | Yes | Application role names (e.g., "FULL_ADMIN"). In "none" mode, contains "ROLE_ANONYMOUS" |

## State Transitions

None. Roles are resolved stateless at authentication time from token claims + configuration.
