# Research: MCP Registry Backend Filtering

## Decision 1: Filter Field Types (Enums vs Strings)

**Decision**: Use `String` for remote transport type filters; use `String` for package registry type filters (accepting the same string values as the `PackageRegistryType` enum).

**Rationale**:
- `RemoteTransport.type` is a plain `String` field in the model (values: "streamable-http", "sse") — no enum exists.
- `PackageRegistryType` is an enum (`NPM`, `PYPI`, `OCI`, `NUGET`, `MCPB`) with `@JsonValue`/`@JsonCreator` serializing as lowercase strings.
- Using `String` for both filter fields keeps the API symmetric and avoids tight coupling between the filter DTO and internal model enums. Validation can happen in the filter logic by comparing strings case-insensitively.

**Alternatives considered**:
- Using `PackageRegistryType` enum directly in the filter DTO — rejected because it creates asymmetry with remote type (String) and ties the API contract to an internal enum.
- Creating a new `RemoteTransportType` enum — rejected as over-engineering for this feature; the upstream registry defines these as strings.

## Decision 2: Filter DTO Structure (Flat vs Nested)

**Decision**: `ServerFilterDto` uses flat fields: `remoteTransportTypes` (renamed from `remoteTypes`), `packageRegistryTypes` (existing), `packageTransportTypes` (new), `repositoryExists` (existing). No nested sub-objects. The `filter` field on `ServersRequestDto` remains a single `ServerFilterDto`.

**Rationale**:
- Clarification session (2026-04-02) confirmed flat fields over list-of-criteria structure.
- Co-location requirement (same package must match both registry type and transport type) was explicitly dropped — each field is evaluated independently across all packages.
- Flat structure keeps the DTO, the GET query param binding, and the filter predicate straightforward with no additional object graph.
- For GET: multi-value fields use repeated param names (`remoteTransportTypes=sse&remoteTransportTypes=streamable-http`) — standard Spring MVC `List<String>` binding.

**Alternatives considered**:
- List-of-criteria per package (`packages: [{registryTypes, transportTypes}]`) with co-located AND — rejected because the co-location requirement was dropped in clarification.
- Deep nesting (`filter.remotes.transportTypes`, `filter.packages.registryTypes`) — rejected as over-engineering for flat independent fields.

## Decision 3: Multi-Page Scanning Location

**Decision**: Implement the scanning loop in `McpRegistryService`, keeping `McpRegistryClient` unchanged as a single-page fetcher.

**Rationale**:
- `McpRegistryClient` is a thin HTTP client that maps 1:1 to upstream API calls. Adding loop logic there would violate its single-responsibility.
- The service layer is where business logic belongs per the constitution's layered architecture.
- The client's existing `getServers(ServersRequestDto)` method already returns a single page — the service orchestrates multiple calls.

**Alternatives considered**:
- Adding a `getAllServersFiltered()` method in the client — rejected because multi-page orchestration is business logic, not HTTP client responsibility.

## Decision 4: Filter Predicate Implementation

**Decision**: Create a dedicated `McpServerFilter` component in the service package that evaluates whether a `ServerResponseDto` matches a given `ServerFilterDto`.

**Rationale**:
- Keeps `McpRegistryService` focused on orchestration (scanning loop, pagination).
- Filter predicate logic is independently testable.
- Follows existing project patterns where distinct concerns get separate classes.

**Alternatives considered**:
- Inline filter logic in the service method — rejected because it would bloat the service and make filter logic harder to test independently.
- Static utility class — rejected in favor of a Spring `@Component` to follow project conventions.

## Decision 5: Scan Limit Default Value

**Decision**: Default max pages to scan = 25.

**Rationale**:
- Upstream registry default page size is 100 items. Scanning 25 pages examines up to 2500 servers per request — covers the majority of the registry's catalog.
- Keeps response time bounded (25 sequential HTTP calls worst case).
- Configurable via `MCP_REGISTRY_MAX_PAGES_TO_SCAN` for operators who need different trade-offs.

**Alternatives considered**:
- 5 pages (500 items) — rejected as too restrictive for rare filter criteria that match a small fraction of servers.
- 3 pages (300 items) — rejected as too restrictive for diverse filter criteria.

## Decision 6: Full-Page Collection (No Mid-Page Break)

**Decision**: When filtering, always collect all matching servers from an entire upstream page before deciding whether to fetch the next page. Do not break mid-page even if `limit` is already reached.

**Rationale**:
- The upstream cursor is an opaque token that points to the **next** page. There is no mechanism to resume processing from a specific position within a page.
- Breaking mid-page and returning the next-page cursor would permanently lose unprocessed matching servers from the remainder of the current page.
- Breaking mid-page and returning the current-page cursor would cause the client to re-fetch the same page, producing duplicates.
- Collecting the full page may return slightly more results than `limit` (up to one upstream page worth of extra matches), but guarantees no data loss and no duplicates.

**Alternatives considered**:
- Break mid-page with next-page cursor — rejected because it loses servers permanently (the original bug).
- Break mid-page with current-page cursor — rejected because it causes duplicate results on the next request.
- Introduce a composite cursor encoding the upstream cursor + intra-page offset — rejected as over-engineering for Phase 1; the cursor contract must remain the raw upstream cursor to survive the Phase 2 transition to aggregator-based storage.
