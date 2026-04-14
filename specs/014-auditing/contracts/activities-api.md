# API Contract: Activities

Base path: `/api/v1`

---

## POST /activities

List activities with pagination, sorting, and filtering.

### Request

**Content-Type**: `application/json`

```json
{
  "page": 0,
  "size": 20,
  "sort": {
    "field": "epochTimestampMs",
    "direction": "DESC"
  },
  "filters": [
    {
      "field": "resourceType",
      "value": "McpDeployment"
    },
    {
      "field": "activityType",
      "value": "Create"
    },
    {
      "field": "epochTimestampMs",
      "from": 1775000000000,
      "to": 1776000000000
    }
  ]
}
```

| Field            | Type    | Required | Default           | Description                                |
|------------------|---------|----------|-------------------|--------------------------------------------|
| `page`           | integer | No       | 0                 | Zero-based page number                     |
| `size`           | integer | No       | 20                | Page size                                  |
| `sort.field`     | string  | No       | epochTimestampMs  | Field to sort by                           |
| `sort.direction` | string  | No       | DESC              | ASC or DESC                                |
| `filters`        | array   | No       | []                | Filter criteria (AND logic between filters). Each filter has `field` + `value` for equality, or `field` + `from`/`to` for range |

**Filterable fields**: `activityId`, `activityType`, `resourceType`, `resourceId`, `initiatedAuthor`, `initiatedEmail` (all case-insensitive string matches), and `epochTimestampMs` (range filter with `from`/`to` epoch milliseconds — supports greater-than-or-equal / less-than-or-equal semantics; either bound may be omitted for open-ended ranges).

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
