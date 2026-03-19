# API Contract: MCP Registry Backend Filtering

## Modified Endpoints

### GET /api/v1/mcp-registry/servers

List MCP servers with optional backend filtering.

**New query parameters** (all optional, added alongside existing params):

| Parameter              | Type            | Required | Description                                                                 |
|------------------------|-----------------|----------|-----------------------------------------------------------------------------|
| `remoteTypes`          | String (CSV)    | No       | Comma-separated remote transport types. Values: `sse`, `streamable-http`.   |
| `packageRegistryTypes` | String (CSV)    | No       | Comma-separated package registry types. Values: `npm`, `pypi`, `oci`, `nuget`, `mcpb`. |
| `repositoryExists`     | Boolean         | No       | `true` = only servers with repository; `false` = only servers without.      |

**Existing parameters** (unchanged):

| Parameter     | Type    | Required | Description                              |
|---------------|---------|----------|------------------------------------------|
| `cursor`      | String  | No       | Pagination cursor                        |
| `limit`       | Integer | No       | Max items (1-1000, default 100)          |
| `search`      | String  | No       | Name search (forwarded to upstream)      |
| `updatedSince`| String  | No       | RFC3339 timestamp (forwarded to upstream)|
| `version`     | String  | No       | Version filter (forwarded to upstream)   |

**Example**:
```
GET /api/v1/mcp-registry/servers?remoteTypes=sse,streamable-http&packageRegistryTypes=npm&repositoryExists=true&limit=20
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

### POST /api/v1/mcp-registry/servers/list

List MCP servers with optional backend filtering (POST variant).

**Modified request body** (`ServersRequestDto`):

```json
{
  "cursor": null,
  "limit": 100,
  "search": "some-name",
  "updatedSince": null,
  "version": "latest",
  "filter": {
    "remoteTypes": ["sse", "streamable-http"],
    "packageRegistryTypes": ["npm", "oci"],
    "repositoryExists": true
  }
}
```

**New field**:

| Field    | Type             | Required | Description                                                                          |
|----------|------------------|----------|--------------------------------------------------------------------------------------|
| `filter` | `ServerFilterDto`| No       | When absent/null, pass-through behavior. When present, backend filtering is applied. |

**`ServerFilterDto` schema**:

| Field                  | Type             | Required | Description                                                          |
|------------------------|------------------|----------|----------------------------------------------------------------------|
| `remoteTypes`          | List\<String\>   | No       | Remote transport types (OR within). Empty list = no constraint.      |
| `packageRegistryTypes` | List\<String\>   | No       | Package registry types (OR within). Empty list = no constraint.      |
| `repositoryExists`     | Boolean          | No       | `true` = must have repo; `false` = must not have repo; null = any.   |

**Response**: `ServerListResponseDto` (unchanged shape)

## Behavior Contract

### Filter application rules

1. **No filter specified** (`filter` is null/absent): Single upstream request, no scanning. Identical to current behavior.
2. **Filter specified with all-empty criteria** (e.g., `{"filter": {"remoteTypes": [], "repositoryExists": null}}`): Treated as no filter — pass-through behavior.
3. **Filter specified with at least one non-empty criterion**: Multi-page scanning activated. Up to N upstream pages fetched and filtered in memory.

### Result count and `limit`

When backend filtering is active, the `limit` parameter controls when to **stop fetching new upstream pages**, not a hard cap on the number of returned servers. The system always processes an entire upstream page once fetched — it does not discard matching servers mid-page. This means a filtered response MAY contain more servers than `limit` (up to one upstream page worth of extra matches). This is intentional: the upstream cursor is an opaque token pointing to the next page, so breaking mid-page would either lose servers permanently or cause duplicates on the next paginated request. When no filter is active (pass-through mode), `limit` is forwarded to the upstream registry and behaves as a strict cap.

### Pagination contract

| Scenario                                          | `nextCursor` in response | Meaning                                    |
|---------------------------------------------------|--------------------------|-------------------------------------------|
| Page filled and upstream has more pages            | Present                  | More results may exist, continue paginating |
| Scan limit reached, upstream has more pages        | Present                  | More results may exist, continue paginating |
| Upstream exhausted (no more pages)                 | Null/absent              | All data has been examined                  |
| Zero results, upstream has more pages              | Present                  | No matches yet, caller can continue         |
| Zero results, upstream exhausted                   | Null/absent              | No matching servers exist                   |

### GET ↔ POST parity

The GET and POST endpoints accept the same filter criteria and produce the same results. The only difference is parameter encoding:

| Filter field           | GET (query param)                        | POST (JSON body)                          |
|------------------------|------------------------------------------|-------------------------------------------|
| `remoteTypes`          | `?remoteTypes=sse,streamable-http`       | `"filter": {"remoteTypes": ["sse", "streamable-http"]}` |
| `packageRegistryTypes` | `?packageRegistryTypes=npm,oci`          | `"filter": {"packageRegistryTypes": ["npm", "oci"]}`     |
| `repositoryExists`     | `?repositoryExists=true`                 | `"filter": {"repositoryExists": true}`                    |
