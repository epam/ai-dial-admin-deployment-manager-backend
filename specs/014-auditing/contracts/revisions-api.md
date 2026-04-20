# API Contract: Revisions & Entity Snapshots

Base path: `/api/v1`

---

## POST /history/revisions

List revisions with pagination, sorting, and filtering.

### Request

**Content-Type**: `application/json`

```json
{
  "pageNumber": 0,
  "pageSize": 20,
  "sorts": [
    {
      "column": "timestamp",
      "direction": "DESC"
    }
  ],
  "filters": [
    {
      "column": "author",
      "operator": "co",
      "value": "john"
    },
    {
      "column": "timestamp",
      "operator": "ge",
      "value": "1775000000000"
    }
  ]
}
```

| Field              | Type    | Required | Default | Description                                                    |
|--------------------|---------|----------|---------|----------------------------------------------------------------|
| `pageNumber`       | integer | No       | 0       | Zero-based page number (min: 0)                                |
| `pageSize`         | integer | No       | 20      | Page size (min: 1, max: 100)                                   |
| `sorts`            | array   | No       | `[]`    | Ordered list of sort directives                                |
| `sorts[].column`   | string  | Yes\*    | —       | Column to sort by (must be an allowed column)                  |
| `sorts[].direction`| string  | Yes\*    | —       | `ASC` or `DESC`                                                |
| `filters`          | array   | No       | `[]`    | Filter criteria (AND logic between filters)                    |
| `filters[].column` | string  | Yes\*    | —       | Column to filter on (must be an allowed column)                |
| `filters[].operator`| string | Yes\*    | —       | One of `eq`, `ne`, `lt`, `le`, `gt`, `ge`, `co`, `nc`          |
| `filters[].value`  | string  | Yes\*    | —       | Comparison value (always String)                               |

\* Required only when the element is present.

**Allowed columns** (for both filtering and sorting): `id`, `timestamp`, `author`, `email`. Requests with unrecognized column names return `400 Bad Request`.

**Case-insensitive columns**: `author`, `email`. Filter values are compared using SQL `LOWER(...)` on both sides. `id` and `timestamp` are compared as-is.

### Operators

Same as the activities API — see `contracts/activities-api.md` for full operator reference.

### Response (200 OK)

```json
{
  "data": [
    {
      "id": 42,
      "timestamp": 1776000000000,
      "author": "john.doe",
      "email": "john.doe@example.com"
    }
  ],
  "total": 150,
  "totalPages": 8
}
```

| Field        | Type    | Description                        |
|--------------|---------|------------------------------------|
| `data`       | array   | List of revision records           |
| `total`      | long    | Total number of matching records   |
| `totalPages` | integer | Total number of pages              |

### Revision Record Fields

| Field       | Type    | Description                                |
|-------------|---------|--------------------------------------------|
| `id`        | integer | Envers revision number                     |
| `timestamp` | long    | Transaction timestamp in milliseconds      |
| `author`    | string  | Username of the initiator (nullable)       |
| `email`     | string  | Email of the initiator (nullable)          |

---

## POST /history/revisions/query

Query a specific revision by ID or by timestamp.

### Request

**Content-Type**: `application/json`

The request body is polymorphic, using a `type` discriminator field.

#### Query by ID

```json
{
  "type": "GET_BY_ID",
  "id": 42
}
```

| Field  | Type    | Required | Description                  |
|--------|---------|----------|------------------------------|
| `type` | string  | Yes      | Must be `"GET_BY_ID"`             |
| `id`   | integer | Yes      | Revision ID (must not be null) |

#### Query by Timestamp

```json
{
  "type": "GET_BY_TIMESTAMP",
  "timestamp": 1776000000000
}
```

| Field       | Type   | Required | Description                                        |
|-------------|--------|----------|----------------------------------------------------|
| `type`      | string | Yes      | Must be `"GET_BY_TIMESTAMP"`                            |
| `timestamp` | long   | Yes      | Epoch milliseconds (must be positive, not null)    |

Returns the **most recent revision at or before** the given timestamp.

### Response (200 OK)

```json
{
  "id": 42,
  "timestamp": 1776000000000,
  "author": "john.doe",
  "email": "john.doe@example.com"
}
```

### Response (404 Not Found)

```json
{
  "path": "/api/v1/history/revisions/query",
  "method": "POST",
  "status": 404,
  "error": "Not Found",
  "message": "Unable to find revision with id 999",
  "traceparent": "00-abc123..."
}
```

---

## Entity Snapshot Endpoints

Each audited entity controller exposes endpoints to retrieve historical entity state at a specific Envers revision. Responses use the same DTOs as the current-state endpoints.

### GET /deployments/{id}/revision/{revision}

Get a deployment snapshot at a specific revision.

| Parameter  | Type    | Description                |
|------------|---------|----------------------------|
| `id`       | string  | Deployment ID              |
| `revision` | integer | Envers revision number     |

**Response (200 OK)**: `DeploymentDto` — same shape as `GET /deployments/{id}`

**Response (404 Not Found)**: If the deployment did not exist at the given revision.

### GET /deployments/revision/{revision}

Get all deployments at a specific revision.

| Parameter  | Type    | Description                |
|------------|---------|----------------------------|
| `revision` | integer | Envers revision number     |

**Response (200 OK)**: `List<DeploymentDto>` — all deployments as they existed at the given revision. May be empty if no deployments existed.

### GET /images/definitions/{id}/revision/{revision}

Get an image definition snapshot at a specific revision.

| Parameter  | Type    | Description                |
|------------|---------|----------------------------|
| `id`       | UUID    | Image definition ID        |
| `revision` | integer | Envers revision number     |

**Response (200 OK)**: `ImageDefinitionDto` — same shape as `GET /images/definitions/{id}`

**Response (404 Not Found)**: If the image definition did not exist at the given revision.

### GET /images/definitions/revision/{revision}

Get all image definitions at a specific revision.

| Parameter  | Type    | Description                |
|------------|---------|----------------------------|
| `revision` | integer | Envers revision number     |

**Response (200 OK)**: `List<ImageDefinitionDto>` — all image definitions as they existed at the given revision. May be empty.

### GET /global-whitelist/image-build/revision/{revision}

Get the domain whitelist snapshot at a specific revision.

| Parameter  | Type    | Description                |
|------------|---------|----------------------------|
| `revision` | integer | Envers revision number     |

**Response (200 OK)**: `List<String>` — same shape as `GET /global-whitelist/image-build`

**Response (404 Not Found)**: If the domain whitelist did not exist at the given revision.
