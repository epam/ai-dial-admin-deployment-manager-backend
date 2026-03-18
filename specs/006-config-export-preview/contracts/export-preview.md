# API Contract: Export Preview Endpoint

**Branch**: `006-config-export-preview` | **Date**: 2026-03-16

## Endpoint

```
POST /api/v1/configs/export-preview
Content-Type: application/json
```

## Request

Identical body to `POST /api/v1/configs/export`. Uses the existing polymorphic `ExportRequestDto`
with `$type` discriminator.

### Example

```json
{
  "$type": "custom",
  "addSecrets": false,
  "addGlobalImageBuildDomainWhitelist": true,
  "components": [
    { "name": "my-mcp-deployment", "type": "MCP_DEPLOYMENT" },
    { "name": "550e8400-e29b-41d4-a716-446655440000", "type": "MCP_IMAGE_DEFINITION" }
  ]
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `$type` | string | Yes | Must be `"custom"` |
| `addSecrets` | boolean | No (default: `false`) | Accepted for API consistency; has no observable effect on the preview response |
| `addGlobalImageBuildDomainWhitelist` | boolean | No (default: `false`) | When `true`, populates `globalImageBuildDomainWhitelist` in the response |
| `components` | array | No | Entities to include; empty array returns an empty preview |
| `components[].name` | string | Yes | Deployment name or image definition UUID |
| `components[].type` | string | Yes | One of the `ExportConfigComponentTypeDto` values |

## Response

**HTTP 200 OK** — `application/json`

```json
{
  "globalImageBuildDomainWhitelist": ["example.com", "registry.internal"],
  "imageDefinitions": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "displayName": "my-mcp-image",
      "version": "1.2.3",
      "description": "An MCP image definition",
      "type": "MCP_IMAGE_DEFINITION"
    }
  ],
  "deployments": [
    {
      "id": "my-mcp-deployment",
      "displayName": "My MCP Deployment",
      "version": null,
      "description": "Runs the MCP server",
      "type": "MCP_DEPLOYMENT"
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `globalImageBuildDomainWhitelist` | `string[]` | Current global whitelist. Empty array when `addGlobalImageBuildDomainWhitelist` was `false`. |
| `imageDefinitions` | `ExportComponentInfoDto[]` | Resolved image definitions (explicitly selected + auto-included). |
| `deployments` | `ExportComponentInfoDto[]` | Resolved deployments. |

### `ExportComponentInfoDto` fields

| Field | Type | Nullable | Image definition source | Deployment source |
|---|---|---|---|---|
| `id` | string | No | `ImageDefinition.id` (UUID as string) | `Deployment.id` |
| `displayName` | string | Yes | `ImageDefinition.name` | `Deployment.displayName` |
| `version` | string | Yes | `ImageDefinition.version` | `null` |
| `description` | string | Yes | `ImageDefinition.description` | `Deployment.description` |
| `type` | string | No | `MCP_IMAGE_DEFINITION` / `ADAPTER_IMAGE_DEFINITION` / `INTERCEPTOR_IMAGE_DEFINITION` | `MCP_DEPLOYMENT` / `ADAPTER_DEPLOYMENT` / `INTERCEPTOR_DEPLOYMENT` / `NIM_DEPLOYMENT` / `INFERENCE_DEPLOYMENT` |

## Error Responses

| HTTP Status | Condition |
|---|---|
| `400 Bad Request` | Invalid request body (missing `$type`, unknown type value, blank component name) |
| `401 Unauthorized` | Authentication required (same rules as `/export`) |
| `403 Forbidden` | Insufficient permissions |

## Behaviour Notes

1. **Auto-inclusion**: selecting a deployment that references an image definition via an internal
   image source will include that image definition in `imageDefinitions`, even if not explicitly listed.
2. **Missing entities**: if a named component does not exist, it is silently omitted (no error).
3. **Empty selection**: empty or absent `components` returns HTTP 200 with all empty lists.
4. **Idempotent / read-only**: never modifies persisted data.
5. **`addSecrets` flag**: accepted for API consistency; `ExportComponentInfoDto` carries no env-var
   fields so the flag has no observable effect.
