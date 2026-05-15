# Feature Specification: API-key authentication via DIAL Core /v1/user/info

**Feature Branch**: `018-api-key-via-core-userinfo`
**Created**: 2026-05-12
**Status**: Implemented
**Capability**: security
**Revisions**:
- 2026-05-13 — added User Story 6 (per-request keys via DM-MCP chain) and User Story 7 (JWT-rooted per-request keys). Updated FR-003, FR-004, FR-008, FR-011 and the per-request-key edge case to reflect that the introspector handles both `{project, roles}` and `{userClaims, roles}` Core responses; the JWT-rooted path resolves roles via `config.rest.security.default.roles-mapping` and surfaces the end-user identity for audit.
**Input**: User description: "DM should accept an `Api-Key` header in addition to JWT, validating the key by delegating to DIAL Core's `/v1/user/info` endpoint and mapping the returned Core roles to DM's application roles via the existing roles-mapping mechanism. The design is documented in `docs/dm-api-key-via-core-userinfo.md`."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Machine caller authenticates with a long-lived API key (Priority: P1)

A CI/CD pipeline, the MCP server, or an operator script presents a long-lived project API key (issued by DIAL Core) in the `Api-Key` header on every DM call. DM validates the key by calling Core's `GET /v1/user/info` and proceeds with the request, mapping Core's roles to DM's application roles.

**Why this priority**: Core value of the feature -- existing oidc mode forces all callers through SSO/JWT, which is impractical for machine-to-machine traffic.

**Independent Test**: Configure DM with `config.rest.security.api-key.enabled=true` and a stub `/v1/user/info` returning `{"roles":["admin"],"project":"test-project"}`, then `curl -H 'Api-Key: <key>' /api/v1/deployments` and observe HTTP 200.

**Acceptance Scenarios**:

1. **Given** `api-key.enabled=true` and a valid key recognised by Core, **When** a machine caller issues `GET /api/v1/deployments` with `Api-Key: <valid-key>`, **Then** DM calls Core's `/v1/user/info` once, maps Core's `roles` to DM's application roles using `api-key.roles-mapping`, sets the principal to Core's `project` field, and returns the deployment list.
2. **Given** `api-key.enabled=true` and a key Core rejects, **When** a machine caller issues a request with that key, **Then** DM returns HTTP 401 with the standard `ErrorView` body and does NOT cache the failure.
3. **Given** `api-key.enabled=false` (default), **When** a request includes `Api-Key`, **Then** DM ignores the header and the request is treated as if no credential were supplied (JWT/opaque-token chain applies).

---

### User Story 2 - JWT-based SSO continues to work alongside API keys (Priority: P1)

Human operators continue to access DM via JWT-based SSO from the admin UI while machine callers use API keys. The two authentication paths coexist; enabling API keys does not disable JWT.

**Why this priority**: A hard either/or between SSO and API keys would block adoption -- the admin UI must keep working.

**Independent Test**: With `api-key.enabled=true` and a valid OIDC provider configured, perform a JWT-authenticated request and verify the api-key filter does not interfere; perform an api-key-authenticated request and verify it succeeds.

**Acceptance Scenarios**:

1. **Given** `api-key.enabled=true` and a valid OIDC provider, **When** a request includes `Authorization: Bearer <jwt>` (no `Api-Key`), **Then** the existing JWT chain handles the request and the api-key path is a no-op.
2. **Given** both `Authorization: Bearer <jwt>` and `Api-Key: <key>` headers are present, **When** the request is processed, **Then** the JWT path handles the request exclusively (JWT-first precedence) and DM does NOT call Core's `/v1/user/info`.
3. **Given** `api-key.enabled=true` but `mode != oidc`, **When** DM starts up, **Then** startup fails with a clear error log -- api-key mode is only valid alongside `oidc`.

---

### User Story 3 - Per-request latency is absorbed by an in-process cache (Priority: P2)

The cost of an HTTP hop to Core on every authenticated DM call is amortised by caching successful introspection results in-process for a short TTL (60 s by default, matching Core's own user-info cache).

**Why this priority**: Required to keep API-key auth practical for hot endpoints, but not blocking for initial rollout.

**Independent Test**: Issue five sequential requests with the same `Api-Key` against a stub `/v1/user/info`; observe exactly one HTTP call to the stub.

**Acceptance Scenarios**:

1. **Given** `api-key.cache-ttl-seconds=60`, **When** the same valid key is presented N times within 60 s, **Then** DM calls Core's `/v1/user/info` exactly once and serves N-1 requests from the cache.
2. **Given** `api-key.cache-ttl-seconds=60`, **When** the same key is presented after 60 s, **Then** DM re-validates against Core.
3. **Given** Core responds non-200, **When** the same key is presented again, **Then** DM re-attempts the call against Core (failures are never cached).
4. **Given** the cache is keyed on `sha256(apiKey)`, **When** keys are stored in the cache, **Then** raw key material does NOT appear in cache keys, logs, or thread dumps.

---

### User Story 4 - Operator configures role mapping for API keys (Priority: P2)

An operator maps Core's role names (e.g. `admin`, `default`) to DM's application roles (`FULL_ADMIN`, `READ_ONLY_ADMIN`) via `config.rest.security.api-key.roles-mapping` JSON, with the same shape as `providers.*.roles-mapping`.

**Why this priority**: Required to grant the correct level of access to API-key callers; without it every key authenticates but gets 403 on protected endpoints.

**Independent Test**: Configure `api-key.roles-mapping={"admin":["FULL_ADMIN"]}` and a Core response with `roles:["admin"]`; issue POST to a `@FullAdminOnly` endpoint and observe 200/201. Re-configure with `{"admin":["READ_ONLY_ADMIN"]}` and observe 403.

**Acceptance Scenarios**:

1. **Given** `api-key.roles-mapping={"admin":["FULL_ADMIN"]}` and Core returns `roles:["admin"]`, **When** the caller hits a `@FullAdminOnly` endpoint, **Then** the request succeeds.
2. **Given** `api-key.roles-mapping={"admin":["READ_ONLY_ADMIN"]}` and Core returns `roles:["admin"]`, **When** the caller hits a `@FullAdminOnly` endpoint, **Then** the request returns 403; GET on a read endpoint returns 200.
3. **Given** Core returns roles that have no entry in the mapping, **When** the caller hits any protected endpoint, **Then** the request returns 403 (authenticated but with no authorities).

---

### User Story 6 - Per-request keys via Core → DM-MCP → DM chain (Priority: P1)

A user (machine caller or human operator) invokes a tool on the DIAL deployment-manager MCP server through DIAL Core. Core mints a per-request key for downstream propagation and DM-MCP forwards it to DM as `Api-Key`. DM validates the key via Core's `/v1/user/info`. The endpoint accepts per-request keys transparently and DM proceeds with the same role-mapping and audit semantics as if the original caller were calling DM directly.

**Why this priority**: This is the dominant production path for end users — direct project-key calls to DM are an admin/CI escape hatch; per-request keys propagated through MCP are how the admin UI's MCP-backed flows reach DM.

**Independent Test**: With DM and DM-MCP configured against the same Core, invoke a DM-MCP tool from a Core-authenticated client; observe DM-MCP forwarding the `Api-Key` header and DM responding 200.

**Acceptance Scenarios**:

1. **Given** a project-key root: the per-request key's `/v1/user/info` response is `{roles, project}` (Core's `ProxyContext.key` is set to the project's `Key`). **When** DM introspects it, **Then** roles are mapped via `api-key.roles-mapping`, the Spring principal is the response `project`, and the request proceeds as a project-key caller.
2. **Given** the per-request key is short-lived (Core default TTL 1800 s) and DM caches introspection for `cache-ttl-seconds`, **When** the key is revoked at Core mid-flight, **Then** DM accepts the cached value for at most one TTL window and re-validates after expiry.
3. **Given** many concurrent users hit DM through DM-MCP, **When** each chain produces a unique per-request key, **Then** the Caffeine cache (LRU-bounded at `cache-max-size`) handles the higher cardinality without authoritative failures; operators may raise `cache-max-size` if eviction pressure shows up in metrics.

---

### User Story 7 - JWT-rooted per-request keys carry the end-user identity (Priority: P1)

When the root credential is a human user's JWT (SSO login), Core mints a per-request key with `ApiKeyData.originalKey == null` and `extractedClaims == <JWT claims>`. Core's `/v1/user/info` for that key returns `{roles, userClaims}` (no `project`). DM resolves the Spring principal and email from `userClaims` and maps the JWT roles via `config.rest.security.default.roles-mapping` — the same mapping DM uses when the user calls it directly with a JWT.

**Why this priority**: Without this, every SSO user reaching DM through the MCP chain is rejected with 401 ("Malformed Core user-info response") — the chain only works for project-key machine callers. The audit log also has to surface the real end-user, not "no principal".

**Independent Test**: Stub `/v1/user/info` to return `{"roles":["sso-admin"],"userClaims":{"oid":["user-1"],"email":["u@example.com"]}}`; with `default.roles-mapping={"sso-admin":["FULL_ADMIN"]}`, issue `GET /api/v1/deployments` with `Api-Key: <stub-key>` and observe 200, Spring principal `user-1`, `UserSecurityDetails.email` = `u@example.com`.

**Acceptance Scenarios**:

1. **Given** the response is `{roles, userClaims}` (JWT root), **When** DM introspects it, **Then** the Spring principal is `userClaims[default.principal-claim]`, `UserSecurityDetails.email` is `userClaims[default.email-claim]` (or null), and roles are mapped via `config.rest.security.default.roles-mapping`.
2. **Given** the JWT user's role (e.g. `sso-admin`) is mapped to `FULL_ADMIN` in `default.roles-mapping`, **When** the user invokes a `@FullAdminOnly` endpoint via the chain, **Then** the request succeeds.
3. **Given** the JWT user's role has no entry in `default.roles-mapping`, **When** the user invokes a protected endpoint, **Then** the request returns 403 (authenticated, no authorities) — same outcome as calling DM directly with the same JWT.
4. **Given** `userClaims` lacks the configured principal claim (or `userClaims` is empty), **When** DM introspects, **Then** the request is rejected with 401 and the failure is not cached.
5. **Given** `deployment.author` / `imageDefinition.author` / `AuditRevision.email` are populated from `SecurityClaimsExtractor.getEmail()`, **When** the JWT-rooted user creates a resource via the chain, **Then** the audit row records the user's email, not null.
6. **Given** the cross-mapping rule (project-key roles must not resolve via the default mapping, and vice versa), **When** a JWT-root response carries a role that only appears in `api-key.roles-mapping`, **Then** the role is not granted and the request is denied.

---

### User Story 5 - DM fails fast when Core is unreachable at startup (Priority: P3)

When `api-key.enabled=true` and `api-key.startup-probe=true`, DM probes Core's user-info URL once during boot. If the URL is unreachable, DM aborts startup with a clear error message rather than silently returning 503 to every API-key request.

**Why this priority**: Improves operator experience but the runtime fallback (per-request 503) is acceptable if the probe is skipped.

**Independent Test**: Set `API_KEY_CORE_URL` to an unreachable host and start DM; observe boot failure.

**Acceptance Scenarios**:

1. **Given** `api-key.enabled=true`, `startup-probe=true`, and `core-url` is unreachable, **When** DM starts, **Then** the application fails to start and logs the connection failure.
2. **Given** `api-key.enabled=true`, `startup-probe=true`, and the URL returns 401 to the probe (expected, since the probe sends a sentinel key), **When** DM starts, **Then** the application starts successfully (401 is treated as "reachable").
3. **Given** `api-key.enabled=true`, `startup-probe=false`, **When** DM starts with an unreachable Core, **Then** DM starts; the first API-key request returns 503.

---

### Edge Cases

- What happens when `Api-Key` is present but blank? The filter treats the header as absent and skips api-key auth.
- What happens when Core's response is malformed (neither `project` nor `userClaims` present, or `userClaims` lacks the configured principal claim)? DM rejects with 401 and logs a warning -- malformed Core responses are not cached.
- What happens when Core times out (per `api-key.request-timeout-ms`)? DM returns 503; the failure is not cached.
- What happens when both `Authorization` and `Api-Key` are present and the JWT is invalid? DM returns 401 (the JWT path's failure response). The api-key is not consulted; this matches the documented JWT-first precedence.
- What happens when the api-key filter receives a request for a public path (e.g. `/api/v1/health`)? The filter still runs but the `permitAll()` matcher in `authorizeHttpRequests` short-circuits authorisation; if `Api-Key` is absent the filter is a no-op anyway.
- What happens to per-request keys (Core's short-lived keys for downstream propagation)? They validate against `/v1/user/info` like project keys, and are the main mechanism for the Core → DM-MCP → DM chain. Core's response shape depends on the root credential: project-key roots produce `{project, roles}` (handled like a project key), JWT roots produce `{userClaims, roles}` (handled via the OIDC default mapping). See User Story 6 and User Story 7.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST accept an `Api-Key` header as an alternative credential when `config.rest.security.mode=oidc` and `config.rest.security.api-key.enabled=true`.
- **FR-002**: System MUST validate API keys by issuing `GET <core-url>/v1/user/info` with header `Api-Key: <key>` and treating HTTP 200 as success.
- **FR-003**: System MUST parse Core's two response shapes and route accordingly:
  - **Project-key auth**: `{"roles": [...], "project": "..."}` — `project` becomes the Spring principal, `UserSecurityDetails.email` is null, role resolution uses `api-key.roles-mapping`.
  - **JWT-rooted per-request key**: `{"roles": [...], "userClaims": {...}}` — the principal is `userClaims[default.principal-claim]` (rejecting the response with 401 if that claim is missing or blank), `UserSecurityDetails.email` is `userClaims[default.email-claim]` (or null), role resolution uses `default.roles-mapping`.
  - **Neither present** (or `userClaims` empty): reject with 401 (`Malformed Core user-info response`), not cached.
- **FR-004**: System MUST route role resolution by response shape, not by a single shared mapping. Project-key roles (`response.project` present) resolve through `config.rest.security.api-key.roles-mapping`; JWT-rooted roles (`response.userClaims` present) resolve through `config.rest.security.default.roles-mapping`. The mappings are intentionally **not** cross-applied — a role appearing only in the api-key mapping must not be granted on the JWT-root path, and vice versa. Each resolver is built once at startup via `UserRolesResolver`.
- **FR-005**: System MUST cache successful introspection results in-process, keyed by `sha256(apiKey)`, with configurable TTL (default 60 s) and max size (default 10 000).
- **FR-006**: System MUST NOT cache non-2xx responses; revoked keys must propagate after one cache TTL at most.
- **FR-007**: System MUST treat `Authorization: Bearer <token>` as taking precedence: when both headers are present, the existing JWT/opaque-token chain handles the request and the api-key is ignored.
- **FR-008**: System MUST fail to start when `api-key.enabled=true` and `core-url` is blank, or `mode != oidc`.
- **FR-009**: System SHOULD probe `<core-url>/v1/user/info` at startup when `api-key.startup-probe=true` (default), aborting startup on connection failure.
- **FR-010**: System MUST NOT log raw API keys; masked / hashed values only.
- **FR-011**: System MUST fail to start when `api-key.enabled=true` and **both** `api-key.roles-mapping` and `config.rest.security.default.roles-mapping` are blank or parse to empty objects. Either alone is sufficient: operators who serve only project-key callers can leave `default.roles-mapping` empty; operators whose api-key traffic is purely JWT-rooted per-request keys (the Core → DM-MCP → DM chain) can leave `api-key.roles-mapping` empty. Rationale: if both are empty, every authenticated api-key caller receives zero authorities and is rejected with 403, so the feature is enabled but unusable. Failing fast surfaces the misconfiguration at boot rather than at first request.

### Key Entities

- **ApiKeyProperties**: Configuration block under `config.rest.security.api-key.*`. Includes `enabled`, `coreUrl` (base URL of DIAL Core; `/v1/user/info` is appended in `CoreApiKeyIntrospector`), `cacheTtlSeconds`, `cacheMaxSize`, `requestTimeoutMs`, `rolesMapping` (JSON), `startupProbe`. The class additionally reads `config.rest.security.default.roles-mapping` via constructor injection (parsed into `parsedDefaultRolesMapping`) so it can validate at boot that at least one mapping is non-empty and supply both mappings to `ApiKeyAuthorityResolver`.
- **IntrospectionResult**: Internal record `(String principal, String email, List<String> rawRoles, boolean fromProjectKey)` produced by `CoreApiKeyIntrospector` from Core's response. `fromProjectKey == true` for `{project, roles}` responses (principal=project, email=null); `false` for `{userClaims, roles}` responses (principal/email pulled from `userClaims` using the OIDC `default.principal-claim`/`default.email-claim`).
- **ApiKeyAuthenticationToken**: A Spring `AbstractAuthenticationToken` carrying the resolved principal (project or userClaims principal) and the mapped `FULL_ADMIN`/`READ_ONLY_ADMIN` authorities; `getDetails()` returns a `UserSecurityDetails(email)` so `SecurityClaimsExtractor.getEmail()` surfaces the end user for audit fields (deployment.author, imageDefinition.author, AuditRevision.email).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A machine caller with a valid project key and a role mapped to `FULL_ADMIN` can drive a deployment end-to-end (create + deploy + undeploy) without ever obtaining a JWT.
- **SC-002**: With `api-key.cache-ttl-seconds=60`, repeated calls with the same key produce exactly one `/v1/user/info` request per 60 s window per DM process.
- **SC-003**: Existing JWT-authenticated flows are unaffected when `api-key.enabled=true`: all current security tests pass without modification.
- **SC-004**: When `api-key.enabled=true` and Core's `/v1/user/info` is unreachable at startup, DM aborts with a clear error log rather than booting and returning 503 to every request.

## Assumptions

- DIAL Core's `GET /v1/user/info` is authenticated by Core, returns `{"roles": [...], "project": "..."}` on success for API keys, and is reachable from DM at the configured URL (HTTPS in production, intra-cluster HTTP is acceptable when DM and Core are co-located).
- DM ↔ Core is trusted enough to forward the API key in cleartext over the configured channel (HTTPS or intra-cluster HTTP).
- Core's role model is coarse (e.g. `["admin"]`, `["default"]`) and operators are expected to provide an explicit `api-key.roles-mapping`; an unmapped role results in 403 (matching existing OIDC behaviour).
- Per-request keys (Core's short-lived keys, default 1800 s TTL) will validate against `/v1/user/info` like project keys, but DM is optimised for long-lived project keys.
- DM does not store, persist, or replicate API keys; the in-process Caffeine cache is the only retention mechanism and survives only as long as the DM JVM.
