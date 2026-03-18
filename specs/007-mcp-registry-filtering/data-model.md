# Data Model: MCP Registry Backend Filtering

## New Entities

### ServerFilterDto

Filter criteria for narrowing MCP server search results. Nested inside `ServersRequestDto` as the `filter` field.

| Field                  | Type           | Nullable | Description                                                                                     |
|------------------------|----------------|----------|-------------------------------------------------------------------------------------------------|
| `remoteTypes`          | List\<String\> | Yes      | Remote transport types to match (OR logic). Values: "streamable-http", "sse". Matches `remotes[].type`. |
| `packageRegistryTypes` | List\<String\> | Yes      | Package registry types to match (OR logic). Values: "npm", "pypi", "oci", "nuget", "mcpb". Matches `packages[].registryType`. |
| `repositoryExists`     | Boolean        | Yes      | If `true`, match servers with non-null repository. If `false`, match servers with null repository. If null/absent, no constraint. |

**Filter logic**:
- Within a dimension's value list: **OR** (server matches if any value matches)
- Across dimensions: **AND** (server must satisfy all specified dimensions)
- Null/absent dimension or empty list: no constraint on that dimension

### Scan Context (internal, not exposed in API)

Tracks multi-page scanning state within the service method. Method-local state — not encoded into the cursor returned to callers. The cursor returned to callers is the raw upstream registry cursor.

| Field           | Type    | Description                                         |
|-----------------|---------|-----------------------------------------------------|
| `upstreamCursor`| String  | Current cursor position in the upstream registry     |
| `pagesScanned`  | int     | Number of upstream pages fetched so far              |
| `collected`     | List    | Accumulated matching `ServerResponseDto` entries     |

## Modified Entities

### ServersRequestDto (existing — modified)

Add one new field:

| Field    | Type            | Nullable | Description                                                            |
|----------|-----------------|----------|------------------------------------------------------------------------|
| `filter` | ServerFilterDto | Yes      | Backend filter criteria. When null/absent, pass-through behavior applies. |

All existing fields (`cursor`, `limit`, `search`, `updatedSince`, `version`) remain unchanged.

### McpRegistryProperties (existing — modified)

Add one new field:

| Field             | Type | Default | Env Variable                      | Description                                          |
|-------------------|------|---------|-----------------------------------|------------------------------------------------------|
| `maxPagesToScan`  | int  | 25      | `MCP_REGISTRY_MAX_PAGES_TO_SCAN`  | Maximum upstream pages to scan per filtered request.  |

## Existing Entities (read-only, used in filter evaluation)

The filter predicate method signature is `matches(ServerResponseDto, ServerFilterDto)`. `ServerResponseDto` wraps `ServerDetail` via its `server` field — filter evaluation traverses `serverResponseDto.getServer()` to access the fields below.

### ServerDetail (accessed via `ServerResponseDto.getServer()`)

Filter evaluation reads these fields:

| Field        | Type                | Filter usage                               |
|--------------|---------------------|--------------------------------------------|
| `remotes`    | List\<RemoteTransport\> | Check `type` field against `remoteTypes` filter |
| `packages`   | List\<Package\>     | Check `registryType` field against `packageRegistryTypes` filter |
| `repository` | Repository          | Null check for `repositoryExists` filter   |

### RemoteTransport

| Field  | Type   | Filter usage                               |
|--------|--------|--------------------------------------------|
| `type` | String | Compared against `remoteTypes` filter values (case-insensitive) |

### Package

| Field          | Type                | Filter usage                                         |
|----------------|---------------------|------------------------------------------------------|
| `registryType` | PackageRegistryType | Enum `.getValue()` compared against `packageRegistryTypes` filter values |
