# Contract: Build Details Response (Updated)

## Endpoint

```
GET /api/v1/images/builds/{id}/details
```

`{id}` is the build-run-specific identifier (the `imageDefinitionId` UUID, which is unique per build version).

## Response Schema

```json
{
  "imageDefinitionId": "<UUID>",
  "status": "<ImageStatus>",
  "imageName": "<string|null>",
  "builtAt": "<ISO-8601 instant>",
  "logs": ["<string>", "..."],
  "domains": [
    {
      "domain": "<fqdn>",
      "verdict": "<ALLOWED|BLOCKED>"
    }
  ]
}
```

### New Field: `domains`

| Property | Type | Description |
|----------|------|-------------|
| `domains` | `List<DomainEntryDto>` (nullable) | List of all unique domain access records for this build run. `null` or absent when Hubble Relay is disabled or no external DNS flows were observed. |
| `domains[].domain` | `string` | Bare external FQDN (trailing `.` stripped). |
| `domains[].verdict` | `string` enum | `ALLOWED` or `BLOCKED`. |

### Example Response (Hubble Relay enabled)

```json
{
  "imageDefinitionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "SUCCEEDED",
  "imageName": "registry.example.com/my-app:1.0.1",
  "builtAt": "2026-04-24T10:30:00Z",
  "logs": [
    "[2026-04-24T10:25:01Z] Step 1/4: FROM python:3.11-slim",
    "..."
  ],
  "domains": [
    { "domain": "auth.docker.io",       "verdict": "ALLOWED" },
    { "domain": "registry-1.docker.io", "verdict": "ALLOWED" },
    { "domain": "pypi.org",             "verdict": "ALLOWED" },
    { "domain": "dl.acme.internal",     "verdict": "BLOCKED" }
  ]
}
```

### Example Response (Hubble Relay disabled)

```json
{
  "imageDefinitionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "SUCCEEDED",
  "imageName": "registry.example.com/my-app:1.0.1",
  "builtAt": "2026-04-24T10:30:00Z",
  "logs": ["..."],
  "domains": null
}
```

## Constraints

- `domains` ordering is insertion order (by `id` ascending) — reflects chronological observation order.
- Entries are deduplicated: at most one entry per (domain, verdict) pair per build run.
- The field is always present in the JSON (value may be `null` when Hubble Relay is disabled or no flows observed).
