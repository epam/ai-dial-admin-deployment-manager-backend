# Feature Specification: MCP Registry Backend Filtering

**Feature Branch**: `009-mcp-registry-filtering`
**Created**: 2026-03-18
**Status**: Draft
**Input**: User description: "I want to adjust MCP registry functionality. Want to support filtering on BE for some properties. MCP registry itself doesn't support filters except for name. Hence I propose to filter it on BE runtime. Read up to N pages from MCP registry and try to collect one page or response collected result after N."

## Phased Approach

This feature follows a two-phase delivery strategy:

**Phase 1 — Prototype/Preview (this spec)**: The system applies filters at request time by scanning multiple upstream registry pages and filtering in memory. This approach is a pragmatic short-term solution that delivers filtering capabilities quickly, with known limitations: response time depends on how many upstream pages must be scanned, and results may be incomplete if the scan limit is reached before all upstream data is examined.

**Phase 2 — Server-Side Storage / Aggregator (future)**: Following the [MCP Registry Aggregator pattern](https://github.com/modelcontextprotocol/registry/blob/main/docs/modelcontextprotocol-io/registry-aggregators.mdx), the system will periodically sync registry data into a local data store and serve filtered queries directly from that store. This eliminates per-request upstream scanning, enables richer and faster queries, and guarantees complete results. The aggregator syncs on a regular schedule using cursor-based pagination and `updated_since` timestamps to fetch incremental updates.

**Design constraint**: The filter API contract (request parameters and response shape) introduced in Phase 1 MUST be designed as the permanent interface that survives the transition to Phase 2. Callers should not need to change when the underlying implementation switches from multi-page scanning to aggregator-based storage. No special "preview" or "prototype" labeling in the API — the contract is stable from day one.

## Clarifications

### Session 2026-04-02

- Q: Should the filter DTO use flat fields or a list-of-criteria structure for package filtering? → A: Flat fields — `remoteTransportTypes` (renamed from `remoteTypes`), `packageRegistryTypes` (existing), `packageTransportTypes` (new), `repositoryExists` (existing). Package registry type and package transport type filters are evaluated independently across all packages on the server (no co-location requirement).
- Q: Do the filter endpoints require additional authorization beyond the existing list-servers endpoint auth? → A: Same auth as existing list-servers — no additional restriction.
- Q: When the upstream errors mid-scan after some results are collected, what should the system return? → A: Return collected results if any; propagate the error only if zero results were collected (existing edge case behavior retained).
- Q: How should multi-value filter fields be encoded as GET query parameters? → A: Repeated param name — `remoteTransportTypes=sse&remoteTransportTypes=streamable-http` (standard Spring MVC List binding).
- Q: Should there be a configurable per-request timeout for multi-page scans? → A: No explicit timeout — rely on existing HTTP client/server timeouts configured at infrastructure level.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Filter MCP Servers by Properties Not Supported by Registry (Priority: P1)

An administrator browsing MCP servers wants to narrow results by properties the upstream MCP registry does not support filtering on — such as remote transport type, package registry type, package transport type, or repository presence. The system reads multiple pages from the upstream registry behind the scenes, applies the requested filters in memory, and returns a single page of matching results to the caller.

The filter model uses flat fields organized around the server's domain concepts (remotes, packages, repository). Each field accepts a list of values with OR logic within it. AND logic applies across different filter dimensions. The package registry type and package transport type filters are independent — each is evaluated across all packages on the server separately (a server matches `packageRegistryTypes=["oci"]` + `packageTransportTypes=["streamable-http"]` if it has at least one OCI package AND at least one package with streamable-http transport, which may or may not be the same package).

**Why this priority**: This is the core value of the feature. Without backend-side filtering, users must manually browse through large numbers of servers to find ones matching their criteria, which is impractical for registries with thousands of entries.

**Independent Test**: Can be fully tested by sending a list-servers request with a backend filter (e.g., remote type = "sse") and verifying only servers matching that filter are returned.

**Acceptance Scenarios**:

1. **Given** the upstream registry contains servers with mixed remote transport types, **When** a user requests servers filtered by remote type "sse", **Then** only servers that have at least one remote transport entry of type "sse" are returned.
2. **Given** the upstream registry contains servers with and without remotes, **When** a user requests servers filtered by remote type "streamable-http", **Then** servers that have no remotes at all are excluded.
3. **Given** the upstream registry contains servers across multiple package ecosystems, **When** a user requests servers filtered by package registry type "oci", **Then** only servers that have at least one package with registry type "oci" are returned.
4. **Given** the upstream registry contains servers with and without a repository link, **When** a user requests servers filtered by "repository exists", **Then** only servers with a non-null repository are returned.
5. **Given** a user requests servers filtered by "repository does not exist", **Then** only servers without a repository are returned.
6. **Given** a user requests servers filtered by remote type ["sse", "streamable-http"], **Then** servers that have at least one remote of type "sse" OR at least one remote of type "streamable-http" are returned.
7. **Given** a user requests servers filtered by package registry type ["npm", "pypi"], **Then** servers that have at least one npm package OR at least one pypi package are returned.
8. **Given** the upstream registry contains servers with various package transport types, **When** a user requests servers filtered by package transport type ["stdio"], **Then** only servers that have at least one package with `transport.type = "stdio"` are returned.
9. **Given** a user requests servers filtered by `packageRegistryTypes: ["oci"]` AND `packageTransportTypes: ["streamable-http"]`, **When** the results are returned, **Then** servers that have at least one OCI package AND at least one package with streamable-http transport are returned — these may be different packages on the same server.
10. **Given** a backend filter is applied and the first upstream page has no matching servers, **When** the system fetches subsequent pages, **Then** the system continues fetching up to N pages until it collects enough results to fill the requested page size or exhausts the maximum page scan limit.
11. **Given** a backend filter is applied alongside the existing `search` parameter, **When** the request is made, **Then** the `search` parameter is forwarded to the upstream registry (server-side) while the backend filter is applied in memory to the results.

---

### User Story 2 - Combine Multiple Filters (Priority: P1)

An administrator wants to find servers that match several criteria at once — for example, servers that have an SSE remote AND are published as an OCI package AND have a source repository linked. Combining filters in a single request should narrow results using AND logic across different filter dimensions, while OR logic applies within a single dimension's value list.

**Why this priority**: Same priority as basic filtering — combining filters is not an add-on, it is inherent to how the filter model works. Each filter dimension independently contributes to a single AND predicate.

**Independent Test**: Can be tested by sending a request with multiple filter dimensions (e.g., remoteTransportTypes + packageRegistryTypes + repositoryExists) and verifying the returned servers satisfy all conditions.

**Acceptance Scenarios**:

1. **Given** a user requests servers with `remoteTransportTypes: ["sse"]` AND `packageRegistryTypes: ["npm"]`, **When** the results are returned, **Then** every server in the results has at least one SSE remote AND at least one npm package.
2. **Given** a user requests servers with `packageRegistryTypes: ["oci"]` AND `repositoryExists: true`, **When** the results are returned, **Then** every server has at least one OCI package AND a non-null repository.
3. **Given** a user requests servers with `remoteTransportTypes: ["sse", "streamable-http"]` AND `packageRegistryTypes: ["npm", "pypi"]`, **When** the results are returned, **Then** every server has (at least one SSE or streamable-http remote) AND (at least one npm or pypi package).
4. **Given** a user specifies only one filter dimension, **When** the results are returned, **Then** only that single dimension is applied — no implicit defaults on other filter dimensions.
5. **Given** a user requests servers with `packageRegistryTypes: ["oci"]` AND `packageTransportTypes: ["streamable-http"]`, **When** the results are returned, **Then** every server has at least one OCI package AND at least one package with streamable-http transport (evaluated independently across all packages).

---

### User Story 3 - Paginate Through Backend-Filtered Results (Priority: P2)

An administrator who received a first page of backend-filtered results wants to continue browsing the next page. The system must provide a way to resume fetching from where the previous request left off, so the user can page through the full filtered result set.

The pagination cursor follows the same contract as the upstream MCP registry: `nextCursor` is present when more results may exist, and null/absent when all upstream data has been examined. When the scan limit is reached but the upstream registry has more pages, the system returns a cursor so the caller can continue scanning in a subsequent request. This makes filtered pagination transparent — callers paginate identically whether filters are applied or not.

**Why this priority**: Pagination is essential for a usable browsing experience but depends on the core filtering mechanism from P1 being in place first.

**Independent Test**: Can be tested by requesting the first page of filtered servers, then using the returned cursor to request the next page, and verifying no duplicates and proper continuation.

**Acceptance Scenarios**:

1. **Given** a backend-filtered request returned a full page of results and more matching servers may exist upstream, **When** the response is returned, **Then** a `nextCursor` is included that allows fetching the next page.
2. **Given** the system has scanned N upstream pages (scan limit) without filling a complete result page but the upstream has more pages, **When** the response is returned, **Then** a `nextCursor` is included so the caller can continue scanning in the next request, and the partial results collected so far are returned.
3. **Given** a user provides a cursor from a previous backend-filtered response, **When** the next page is requested, **Then** the system resumes scanning from the correct upstream position and returns the next batch of matching results.
4. **Given** the upstream registry has been fully exhausted (no more pages), **When** the response is returned, **Then** `nextCursor` is null/absent — the caller knows all data has been examined.

---

### User Story 4 - Configurable Maximum Page Scan Limit (Priority: P3)

An operator deploying the system wants to control how many upstream registry pages the backend will scan per request to balance result completeness against response time and upstream API load.

**Why this priority**: Operational safety net — prevents runaway requests to the upstream registry. Lower priority because a sensible default is sufficient for most deployments.

**Independent Test**: Can be tested by configuring a low scan limit (e.g., 2 pages), applying a filter that matches very few servers, and verifying the system stops after scanning the configured number of pages.

**Acceptance Scenarios**:

1. **Given** the maximum scan limit is configured to M pages, **When** a backend-filtered request is made that matches fewer servers than the page size, **Then** the system stops scanning after M upstream pages regardless of how many results were collected, and returns a cursor if the upstream has more pages.
2. **Given** no explicit scan limit is configured, **When** a backend-filtered request is made, **Then** the system uses a reasonable default limit.

---

### Edge Cases

- What happens when all N upstream pages are scanned but zero servers match the filter? The system returns an empty server list. If the upstream has more pages, a cursor is returned so the caller can continue; if the upstream is exhausted, cursor is null.
- What happens when the upstream registry returns an error mid-scan (e.g., on page 3 of 5)? The system returns whatever results were collected from successful pages so far, or propagates the error if no results were collected yet.
- What happens when no backend filters are specified? The request is passed through to the upstream registry without any multi-page scanning — existing behavior is preserved.
- What happens when the upstream registry has fewer pages than the scan limit? The system stops when the upstream indicates no more pages (null nextCursor) and returns collected results with null cursor.
- What happens when a user provides a backend filter on the GET endpoint (query params)? The filters are accepted as query parameters the same way they are in the POST request body.
- What happens when a server has null/empty `packages` list and a package criterion filter is applied? The server does not match the filter and is excluded.
- What happens when a server has null/empty `remotes` list and a remote type filter is applied? The server does not match the filter and is excluded.
- What happens when a filter criterion has an empty list of values (e.g., remote type = [])? An empty list is treated as "no filter on this dimension" — equivalent to omitting the criterion entirely.
- What happens when `packageTransportTypes` is specified but a package has a null `transport` field? That package does not match the transport type filter and is skipped; the server matches only if another of its packages has a non-null transport with a matching type.
- What happens when a single upstream page has more matching servers than the requested limit? The system collects all matches from that page and returns them, even if the count exceeds `limit`. This is intentional: the upstream cursor is an opaque token pointing to the next page, so breaking mid-page would permanently lose unprocessed matches. The `limit` controls when to stop fetching new pages, not a hard cap on results.

## Requirements *(mandatory)*

### Functional Requirements

**Filter model**

- **FR-001**: System MUST accept a structured filter object on the list-servers endpoints (both GET and POST variants) for properties not supported by the upstream MCP registry.
- **FR-002**: The filter model uses flat fields grouped by server domain concept (`remotes`, `packages`, `repository`). Each field is evaluated independently across all matching entities on the server.
- **FR-003**: System MUST support filtering by remote transport type via `remoteTransportTypes` — matching servers that have at least one remote entry whose `type` matches any of the specified values (case-insensitive, e.g., ["streamable-http", "sse"]). Values are combined with OR logic.
- **FR-004**: System MUST support filtering by package registry type via `packageRegistryTypes` — matching servers that have at least one package whose `registryType` matches any of the specified values (case-insensitive, e.g., ["npm", "pypi", "oci", "nuget", "mcpb"]). Values are combined with OR logic.
- **FR-004b**: System MUST support filtering by package transport type via `packageTransportTypes` — matching servers that have at least one package whose `transport.type` matches any of the specified values (case-insensitive, e.g., ["stdio", "streamable-http", "sse"]). Values are combined with OR logic. This filter is evaluated independently from `packageRegistryTypes` — the matching package does not need to be the same package.
- **FR-005**: System MUST support filtering by repository existence via `repositoryExists` — matching servers that have a non-null repository (`true`) or a null repository (`false`).
- **FR-006**: When multiple filter fields are specified, they MUST be combined with AND logic — a server must satisfy every specified field to be included in results. Within a single field's value list, OR logic applies.
- **FR-007**: When a filter field is omitted (null/absent) or its value list is empty, it MUST NOT constrain results — only explicitly provided fields with non-empty values participate in filtering.
- **FR-007a**: On the GET endpoint, multi-value filter fields MUST be accepted as repeated query parameters (e.g., `remoteTransportTypes=sse&remoteTransportTypes=streamable-http`), consistent with standard Spring MVC `List<String>` binding.

**Multi-page scanning (Phase 1 — Prototype)**

- **FR-008**: When backend filters are applied, the system MUST fetch pages from the upstream registry iteratively (up to a configurable maximum number of pages) and apply filters in memory to accumulate matching results.
- **FR-009**: The system MUST stop fetching **new** upstream pages when either: (a) the requested page size of matching results has been reached or exceeded, or (b) the maximum page scan limit has been reached, or (c) the upstream registry has no more pages. Within a single upstream page, **all** matching servers MUST be collected — the system MUST NOT discard matches mid-page. Because the upstream cursor is an opaque token that always points to the next page, breaking mid-page would permanently lose unprocessed servers. Consequently, a filtered response MAY return more servers than `limit` (up to one upstream page worth of extra matches).
- **FR-010**: When no backend filters are specified, the system MUST preserve existing pass-through behavior — a single upstream request with no multi-page scanning.

**Pagination**

- **FR-011**: The system MUST use `nextCursor` as the sole indicator of whether more results may exist. When the scan limit is reached but the upstream registry has more pages, the system MUST return a cursor so the caller can continue scanning. When the upstream is fully exhausted, cursor MUST be null.
- **FR-012**: The upstream-supported filters (`search`, `updatedSince`, `version`) MUST continue to be forwarded to the upstream registry as query parameters, applied server-side.

**Configuration**

- **FR-013**: The maximum number of upstream pages to scan per request MUST be configurable via an application property (default: 25).

**API stability**

- **FR-014**: The filter API contract (request parameters and response shape) MUST be designed as the permanent interface that will be retained when the underlying implementation transitions from multi-page scanning (Phase 1) to aggregator-based storage (Phase 2). No preview/prototype labeling in the API.

### Key Entities

- **Server Filter**: A flat set of optional filter fields evaluated with AND logic across dimensions and OR logic within each field's value list:
  - `remoteTransportTypes`: list of strings matched against `RemoteTransport.type` ("streamable-http", "sse").
  - `packageRegistryTypes`: list of strings matched against `Package.registryType` ("npm", "pypi", "oci", "nuget", "mcpb").
  - `packageTransportTypes`: list of strings matched against `Package.transport.type` ("stdio", "streamable-http", "sse"). Evaluated independently from `packageRegistryTypes`.
  - `repositoryExists`: boolean — true requires non-null repository, false requires null repository.

  When the entire filter is null/absent or all fields are empty/null, no backend filtering occurs.
- **Scan Context**: Tracks the current upstream cursor position, the number of pages scanned so far, and accumulated matching results. This is method-local state within the service — it is not encoded into the cursor returned to the caller. The cursor returned to callers is the raw upstream registry cursor. This entity is specific to Phase 1 and will be replaced by standard database pagination in Phase 2.

### Assumptions

- The upstream MCP Registry API remains stable at `v0.1` for the duration of Phase 1.
- The registry's cursor-based pagination is deterministic — the same cursor always resumes from the same position.
- Phase 2 (aggregator-based storage) will be specified as a separate feature; this spec covers only Phase 1.
- The filter endpoints inherit the same authorization as the existing list-servers endpoints — no additional role restriction is applied to filtering.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can filter MCP servers by remote transport type (`remoteTransportTypes`), package registry type (`packageRegistryTypes`), package transport type (`packageTransportTypes`), and repository existence (`repositoryExists`) in a single request and receive only matching results.
- **SC-002**: Filters are composable — any combination of the supported filter dimensions can be used together, with multiple values per dimension (OR within, AND across).
- **SC-003**: Paginated browsing through filtered results works seamlessly — callers paginate using `nextCursor` identically whether filters are applied or not, with no duplicates or missing servers between pages.
- **SC-004**: When no backend filters are provided, the system behaves identically to the current implementation with no additional upstream requests.
- **SC-005**: The system never exceeds the configured maximum page scan limit per request, keeping response times bounded.
- **SC-006**: All new filter parameters are documented in the API specification (OpenAPI annotations).
- **SC-007**: The filter API contract is stable — the same request shape will continue to work when the system transitions from prototype to aggregator-based storage in Phase 2.
