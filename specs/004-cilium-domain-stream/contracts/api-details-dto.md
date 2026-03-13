# Contract: Image Build Details DTO (extended)

**Feature**: 004-cilium-domain-stream  
**Date**: 2025-03-13

## Endpoint

- **Method / path**: `GET /api/v1/images/builds/{id}/details` (existing).
- **Change**: Response body is extended with an optional **accessedDomains** field.

## Response body (ImageBuildDetailsDto)

Existing fields unchanged. New field:

| Field            | Type                        | Description |
|------------------|-----------------------------|-------------|
| accessedDomains  | array of AccessedDomainDto  | List of distinct domains accessed during the build with verdict; one entry per domain. Omitted or empty when Cilium is disabled or no domains were accessed. |

### AccessedDomainDto

| Field   | Type   | Description |
|---------|--------|-------------|
| domain  | string | Domain that was accessed. |
| verdict | string | `"ALLOWED"` or `"BLOCKED"`. |

**Example (fragment)**:
```json
{
  "imageDefinitionId": "9dcfbce6-a1db-4053-b9fd-11589031042a",
  "status": "BUILD_SUCCESSFUL",
  "imageName": "...",
  "builtAt": "2025-03-13T12:00:00Z",
  "logs": [ "..." ],
  "accessedDomains": [
    { "domain": "github.com", "verdict": "ALLOWED" },
    { "domain": "pypi.org", "verdict": "BLOCKED" }
  ]
}
```

Backward compatibility: Clients that do not expect `accessedDomains` can ignore it. When the feature is off or no data is available, the field may be absent or an empty array.
