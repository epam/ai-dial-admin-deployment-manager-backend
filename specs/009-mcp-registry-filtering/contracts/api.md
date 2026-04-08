# API Contract: MCP Registry Backend Filtering

## Modified Endpoints

### GET /api/v1/mcp-registry/servers

List MCP servers with optional backend filtering.

**Filter query parameters** (all optional):

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `remoteTransportTypes` | `List<String>` (repeated param) | No | Remote transport types (OR logic). Values: `sse`, `streamable-http`. **Renamed from `remoteTypes`.** |
| `packageRegistryTypes` | `List<String>` (repeated param) | No | Package registry types (OR logic). Values: `npm`, `pypi`, `oci`, `nuget`, `mcpb`. |
| `packageTransportTypes` | `List<String>` (repeated param) | No | **New.** Package transport types (OR logic). Values: `stdio`, `streamable-http`, `sse`. |
| `repositoryExists` | `Boolean` | No | `true` = only servers with repository; `false` = only servers without. |

Multi-value encoding: repeat the parameter name — `remoteTransportTypes=sse&remoteTransportTypes=streamable-http`.

**Existing parameters** (unchanged):

| Parameter | Type | Description |
|-----------|------|-------------|
| `cursor` | String | Pagination cursor |
| `limit` | Integer | Max items (1–1000, default 100) |
| `search` | String | Name search (forwarded to upstream) |
| `updatedSince` | String | RFC3339 timestamp (forwarded to upstream) |
| `version` | String | Version filter (forwarded to upstream) |

**Example**:
```
GET /api/v1/mcp-registry/servers?remoteTransportTypes=sse&packageRegistryTypes=oci&packageTransportTypes=streamable-http&repositoryExists=true&limit=20
```

**Response**: `ServerListResponseDto` (unchanged shape)
```json
{
  "servers": [ ... ],
  "metadata": {
    "nextCursor": "cursor-value-or-null",
    "count": 20
  }
}
```

---

### POST /api/v1/mcp-registry/servers/list

List MCP servers with optional backend filtering (POST variant).

**Request body** (`ServersRequestDto`):

```json
{
  "cursor": null,
  "limit": 100,
  "search": "some-name",
  "updatedSince": null,
  "version": "latest",
  "filter": {
    "remoteTransportTypes": ["sse", "streamable-http"],
    "packageRegistryTypes": ["npm", "oci"],
    "packageTransportTypes": ["stdio"],
    "repositoryExists": true
  }
}
```

**`ServerFilterDto` schema** (within `filter`):

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `remoteTransportTypes` | `List<String>` | No | **Renamed from `remoteTypes`.** Remote transport types (OR). Empty = no constraint. |
| `packageRegistryTypes` | `List<String>` | No | Package registry types (OR). Empty = no constraint. |
| `packageTransportTypes` | `List<String>` | No | **New.** Package transport types (OR). Empty = no constraint. |
| `repositoryExists` | `Boolean` | No | `true` = must have repo; `false` = must not have repo; null = any. |

**Response**: `ServerListResponseDto` (unchanged shape)

---

## Behavior Contract

### Filter activation rules

1. **No filter** (`filter` null/absent or all fields null/empty): Single upstream request, no scanning — identical to existing behavior.
2. **At least one non-empty field**: Multi-page scanning activated. Up to `maxPagesToScan` upstream pages fetched and filtered in memory.

### Independent dimension evaluation

`packageRegistryTypes` and `packageTransportTypes` are evaluated **independently across all packages** on a server. A server with Package A (OCI, stdio) and Package B (npm, streamable-http) matches `packageRegistryTypes=oci` + `packageTransportTypes=streamable-http` because it has at least one OCI package AND at least one package with streamable-http transport (even though they are different packages).

### Result count and `limit`

When backend filtering is active, `limit` controls when to **stop fetching new upstream pages**, not a hard cap on returned servers. A filtered response MAY contain more servers than `limit` (up to one upstream page extra) because the system never discards matches mid-page.

### Pagination contract

| Scenario | `nextCursor` | Meaning |
|----------|-------------|---------|
| Page filled, upstream has more pages | Present | More results may exist |
| Scan limit reached, upstream has more pages | Present | Caller can continue scanning |
| Upstream exhausted | Null/absent | All data examined |
| Zero results, upstream has more pages | Present | No matches yet, caller can continue |
| Zero results, upstream exhausted | Null/absent | No matching servers exist |

### GET ↔ POST parity

| Filter field | GET (repeated params) | POST (JSON body `filter`) |
|---|---|---|
| `remoteTransportTypes` | `?remoteTransportTypes=sse&remoteTransportTypes=streamable-http` | `"remoteTransportTypes": ["sse", "streamable-http"]` |
| `packageRegistryTypes` | `?packageRegistryTypes=npm&packageRegistryTypes=oci` | `"packageRegistryTypes": ["npm", "oci"]` |
| `packageTransportTypes` | `?packageTransportTypes=stdio` | `"packageTransportTypes": ["stdio"]` |
| `repositoryExists` | `?repositoryExists=true` | `"repositoryExists": true` |
