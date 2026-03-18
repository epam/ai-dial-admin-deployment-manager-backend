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

**Decision**: Add a nested `ServerFilterDto` object as a `filter` field inside `ServersRequestDto`. The filter DTO uses concept-based field naming (`remoteTypes`, `packageRegistryTypes`, `repositoryExists`).

**Rationale**:
- Spec FR-002 requires filters organized around server domain concepts.
- A nested `filter` object separates filter concerns from pagination/search params (`cursor`, `limit`, `search`, `updatedSince`, `version`).
- Naming convention (`remoteTypes`, `packageRegistryTypes`, `repositoryExists`) makes the domain concept clear without deep nesting.
- For the GET endpoint, the controller accepts flat query params and constructs the filter DTO manually (matching existing controller patterns).

**Alternatives considered**:
- Flat fields directly on `ServersRequestDto` — rejected because it mixes filtering concerns with pagination and makes future extension harder.
- Deep nesting (`filter.remotes.types`, `filter.packages.registryTypes`) — rejected as over-engineering for 3 filter fields.

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

**Decision**: Default max pages to scan = 5.

**Rationale**:
- Upstream registry default page size is 100 items. Scanning 5 pages examines up to 500 servers per request — sufficient for most filter scenarios.
- Keeps response time bounded (5 sequential HTTP calls worst case).
- Configurable via `MCP_REGISTRY_MAX_PAGES_TO_SCAN` for operators who need different trade-offs.

**Alternatives considered**:
- 10 pages (1000 items) — rejected as potentially too slow for a prototype.
- 3 pages (300 items) — rejected as too restrictive for diverse filter criteria.
