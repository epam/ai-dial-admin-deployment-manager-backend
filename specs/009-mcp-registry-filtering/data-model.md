# Data Model: MCP Registry Backend Filtering

## Modified Entities

### ServerFilterDto (modified)

Flat filter DTO nested inside `ServersRequestDto` as the `filter` field.

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `remoteTransportTypes` | `List<String>` | Yes | **Renamed from `remoteTypes`.** Remote transport types to match (OR logic). Values: `streamable-http`, `sse`. Matched case-insensitively against `RemoteTransport.type`. |
| `packageRegistryTypes` | `List<String>` | Yes | Package registry types to match (OR logic). Values: `npm`, `pypi`, `oci`, `nuget`, `mcpb`. Matched case-insensitively against `Package.registryType` enum value. |
| `packageTransportTypes` | `List<String>` | Yes | **New field.** Package transport types to match (OR logic). Values: `stdio`, `streamable-http`, `sse`. Matched case-insensitively against `Package.transport.type` enum value. A package with null `transport` does not match. |
| `repositoryExists` | `Boolean` | Yes | `true` = servers with non-null repository; `false` = servers with null repository. Null = no constraint. |

**Cross-dimension logic**: AND. A server must satisfy every non-empty/non-null dimension.

**Active criteria**: `hasActiveCriteria()` returns `true` when any field is non-null and non-empty (including `packageTransportTypes`).

**Filter logic matrix:**

| remoteTransportTypes | packageRegistryTypes | packageTransportTypes | repositoryExists | Behavior |
|---|---|---|---|---|
| null/empty | null/empty | null/empty | null | Pass-through (no multi-page scan) |
| ["sse"] | — | — | — | Servers with ≥1 SSE remote |
| — | ["oci"] | — | — | Servers with ≥1 OCI package |
| — | — | ["stdio"] | — | Servers with ≥1 package with stdio transport |
| — | ["oci"] | ["streamable-http"] | — | Servers with ≥1 OCI package AND ≥1 package with streamable-http transport (evaluated independently — may be different packages) |

### ServersRequestDto (unchanged)

`filter` field already present; no changes to this class.

### McpRegistryProperties (unchanged)

`maxPagesToScan` property already present; no changes.

## Existing Entities (read-only, used in filter evaluation)

### ServerDetail (accessed via `ServerResponseDto.getServer()`)

| Field | Type | Filter usage |
|-------|------|-------------|
| `remotes` | `List<RemoteTransport>` | Check `type` against `remoteTransportTypes` |
| `packages` | `List<Package>` | Check `registryType` against `packageRegistryTypes`; check `transport.type` against `packageTransportTypes` |
| `repository` | `Repository` | Null check for `repositoryExists` |

### RemoteTransport

| Field | Type | Filter usage |
|-------|------|-------------|
| `type` | `String` | Compared against `remoteTransportTypes` values (case-insensitive) |

### Package

| Field | Type | Filter usage |
|-------|------|-------------|
| `registryType` | `PackageRegistryType` | `enum.getValue()` compared against `packageRegistryTypes` values (case-insensitive) |
| `transport` | `LocalTransport` (nullable) | `transport.type.getValue()` compared against `packageTransportTypes` values (case-insensitive); null transport = no match |

### LocalTransport

| Field | Type | Values |
|-------|------|--------|
| `type` | `LocalTransportType` | `STDIO("stdio")`, `STREAMABLE_HTTP("streamable-http")`, `SSE("sse")` |

## Scan Context (internal, method-local, not persisted)

| Variable | Type | Purpose |
|----------|------|---------|
| `collected` | `List<ServerResponseDto>` | Accumulated matching results across pages |
| `currentCursor` | `String` | Cursor for next upstream fetch |
| `lastSuccessfulCursor` | `String` | Cursor returned to caller when scan limit reached |
| `pagesScanned` | `int` | Count of pages fetched; stops at `maxPagesToScan` |
