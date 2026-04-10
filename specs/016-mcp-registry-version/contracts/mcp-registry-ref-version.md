# API Contract: McpRegistryRef with Version

**Feature**: `016-mcp-registry-version`
**Affected endpoints**: All image definition and deployment CRUD endpoints that include a source object with `externalRegistryRef` of type `mcp-registry`.

---

## McpRegistryRefDto — Updated Schema

### With version

```json
{
  "$type": "mcp-registry",
  "packageName": "github/github",
  "version": "2025.4.1"
}
```

### Without version (backward compatible)

```json
{
  "$type": "mcp-registry",
  "packageName": "github/github"
}
```

### Field reference

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `$type` | `"mcp-registry"` | yes | fixed discriminator |
| `packageName` | string | yes | non-blank (`@NotBlank`) |
| `version` | string | no | nullable; when provided, must contain at least one non-whitespace character |

---

## Example: Image definition source with versioned MCP registry ref

```json
{
  "$type": "git-dockerfile",
  "url": "https://github.com/owner/mcp-server.git",
  "externalRegistryRef": {
    "$type": "mcp-registry",
    "packageName": "owner/mcp-server",
    "version": "1.2.0"
  }
}
```

---

## Example: Deployment source with versioned MCP registry ref

```json
{
  "$type": "image_reference",
  "imageReference": "registry.example.com/mcp-server:1.2.0",
  "externalRegistryRef": {
    "$type": "mcp-registry",
    "packageName": "owner/mcp-server",
    "version": "1.2.0"
  }
}
```

---

## Validation Errors

Sending a blank `version` returns HTTP 400:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "...version: must not be blank",
  "path": "/api/v1/images/definitions",
  "method": "POST",
  "traceparent": "..."
}
```

Sending `version` as empty string (`""`) returns HTTP 400 with the same pattern.

---

## Clearing the version

To remove a version from an existing `McpRegistryRef`, send the ref without the `version` field:

```json
{
  "$type": "mcp-registry",
  "packageName": "owner/mcp-server"
}
```

The stored version is cleared; subsequent reads return `McpRegistryRef` with only `packageName`.
