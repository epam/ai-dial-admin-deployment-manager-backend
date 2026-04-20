# API Contract: Activities

Base path: `/api/v1`

---

## POST /activities

List activities with pagination, sorting, and filtering.

### Request

**Content-Type**: `application/json`

```json
{
  "pageNumber": 0,
  "pageSize": 20,
  "sorts": [
    {
      "column": "epochTimestampMs",
      "direction": "DESC"
    }
  ],
  "filters": [
    {
      "column": "resourceType",
      "operator": "eq",
      "value": "McpDeployment"
    },
    {
      "column": "activityType",
      "operator": "eq",
      "value": "Create"
    },
    {
      "column": "epochTimestampMs",
      "operator": "ge",
      "value": "1775000000000"
    },
    {
      "column": "epochTimestampMs",
      "operator": "le",
      "value": "1776000000000"
    }
  ]
}
```

| Field              | Type    | Required | Default | Description                                                    |
|--------------------|---------|----------|---------|----------------------------------------------------------------|
| `pageNumber`       | integer | No       | 0       | Zero-based page number (min: 0)                                |
| `pageSize`         | integer | No       | 20      | Page size (min: 1, max: 100)                                   |
| `sorts`            | array   | No       | `[]`    | Ordered list of sort directives (AND combined, first is primary) |
| `sorts[].column`   | string  | Yes\*    | —       | Column to sort by (must be an allowed column)                  |
| `sorts[].direction`| string  | Yes\*    | —       | `ASC` or `DESC`                                                |
| `filters`          | array   | No       | `[]`    | Filter criteria (AND logic between filters)                    |
| `filters[].column` | string  | Yes\*    | —       | Column to filter on (must be an allowed column)                |
| `filters[].operator`| string | Yes\*    | —       | One of `eq`, `ne`, `lt`, `le`, `gt`, `ge`, `co`, `nc`          |
| `filters[].value`  | string  | Yes\*    | —       | Comparison value (always String; see notes below)              |

\* Required only when the element is present. Sending an empty `sorts` or `filters` array is valid.

**Default order**: When `sorts` is empty, results are returned in the database's natural order — **not** sorted by timestamp. Clients requiring deterministic ordering must send an explicit `sorts` entry.

**Allowed columns** (for both filtering and sorting): `activityId`, `activityType`, `resourceType`, `resourceId`, `initiatedAuthor`, `initiatedEmail`, `epochTimestampMs`. Requests with unrecognized column names return `400 Bad Request`.

**Case-insensitive columns**: `activityId`, `activityType`, `resourceType`, `resourceId`, `initiatedAuthor`, `initiatedEmail`. Filter values are compared using SQL `LOWER(...)` on both sides. `epochTimestampMs` is compared as-is (String-valued but ordered lexicographically, which matches numeric order for 13-digit epoch-milli timestamps).

### Operators

| Operator | Semantics                        | SQL equivalent      |
|----------|----------------------------------|---------------------|
| `eq`     | Equal                            | `=`                 |
| `ne`     | Not equal                        | `<>`                |
| `lt`     | Less than                        | `<`                 |
| `le`     | Less than or equal               | `<=`                |
| `gt`     | Greater than                     | `>`                 |
| `ge`     | Greater than or equal            | `>=`                |
| `co`     | Contains (substring)             | `LIKE '%value%'`    |
| `nc`     | Does not contain (substring)     | `NOT LIKE '%value%'`|

**Ranges** (including timestamp ranges) are expressed as two filters on the same column, one with `ge`/`gt` and one with `le`/`lt`.

### Response (200 OK)

```json
{
  "data": [
    {
      "activityId": "019606d8-a1b2-7000-8000-abcdef123456",
      "activityType": "Create",
      "resourceType": "McpDeployment",
      "resourceId": "my-mcp-server",
      "epochTimestampMs": 1776000000000,
      "initiatedAuthor": "john.doe",
      "initiatedEmail": "john.doe@example.com",
      "revision": 42
    }
  ],
  "total": 150,
  "totalPages": 8
}
```

| Field        | Type    | Description                        |
|--------------|---------|------------------------------------|
| `data`       | array   | List of activity records           |
| `total`      | long    | Total number of matching records   |
| `totalPages` | integer | Total number of pages              |

### Activity Record Fields

| Field              | Type    | Description                                |
|--------------------|---------|--------------------------------------------|
| `activityId`       | string  | UUID v7 (time-ordered)                     |
| `activityType`     | string  | One of: Create, Update, Delete             |
| `resourceType`     | string  | One of the ActivityResourceType enum values|
| `resourceId`       | string  | Business identifier of the affected entity |
| `epochTimestampMs` | long    | Transaction timestamp in milliseconds      |
| `initiatedAuthor`  | string  | Username of the initiator (nullable)       |
| `initiatedEmail`   | string  | Email of the initiator (nullable)          |
| `revision`         | integer | Envers revision number                     |

---

## GET /activities/{activityId}

Retrieve a single activity by its identifier.

### Path Parameters

| Parameter    | Type   | Description            |
|--------------|--------|------------------------|
| `activityId` | UUID   | Activity UUID          |

### Response (200 OK)

```json
{
  "activityId": "019606d8-a1b2-7000-8000-abcdef123456",
  "activityType": "Create",
  "resourceType": "McpDeployment",
  "resourceId": "my-mcp-server",
  "epochTimestampMs": 1776000000000,
  "initiatedAuthor": "john.doe",
  "initiatedEmail": "john.doe@example.com",
  "revision": 42
}
```

### Response (404 Not Found)

```json
{
  "path": "/api/v1/activities/019606d8-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "method": "GET",
  "status": 404,
  "error": "Not Found",
  "message": "Unable to find activity with id 019606d8-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "traceparent": "00-abc123..."
}
```

---

## Resource Type Values

| Value                          | Entity                           |
|--------------------------------|----------------------------------|
| `AdapterDeployment`            | AdapterDeploymentEntity          |
| `ApplicationDeployment`        | ApplicationDeploymentEntity      |
| `InterceptorDeployment`        | InterceptorDeploymentEntity      |
| `McpDeployment`                | McpDeploymentEntity              |
| `NimDeployment`                | NimDeploymentEntity              |
| `InferenceDeployment`          | InferenceDeploymentEntity        |
| `AdapterImageDefinition`       | AdapterImageDefinitionEntity     |
| `ApplicationImageDefinition`   | ApplicationImageDefinitionEntity |
| `InterceptorImageDefinition`   | InterceptorImageDefinitionEntity |
| `McpImageDefinition`           | McpImageDefinitionEntity         |
| `ImageBuildDomainWhitelist`    | DomainWhitelistEntity            |

Base types `Deployment` and `ImageDefinition` are not exposed — entities are always persisted as concrete subtypes. If the mapper receives a base class, it throws `IllegalArgumentException`.
