# API Contract: ExternalRegistryRef Type

**Feature**: `005-external-registry-ref`
**Affected endpoints**: All image definition and deployment CRUD endpoints that include a source object.

---

## ExternalRegistryRefDto — Discriminated Union

The `externalRegistryRef` field in source DTOs is a polymorphic object identified by `$type`.

### McpRegistryRefDto

```json
{
  "$type": "mcp-registry",
  "packageName": "my-mcp-server"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `$type` | `"mcp-registry"` | yes | fixed discriminator |
| `packageName` | string | yes | non-blank |

---

### GitHubRefDto

```json
{
  "$type": "github",
  "repo": "owner/my-project"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `$type` | `"github"` | yes | fixed discriminator |
| `repo` | string | yes | non-blank; conventional format `owner/repo` |

---

### GenericRefDto

```json
{
  "$type": "generic",
  "url": "https://example-registry.com/entries/my-tool"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `$type` | `"generic"` | yes | fixed discriminator |
| `url` | string | yes | non-blank; conventionally a fully-qualified URL |

---

## Validation Errors

Sending a blank field returns HTTP 400:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "externalRegistryRef.packageName: must not be blank",
  "path": "/api/v1/images/definitions",
  "method": "POST",
  "traceparent": "..."
}
```

Sending an unknown `$type` returns HTTP 400 (Jackson deserialization failure):

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Could not resolve type id 'unknown-registry' ...",
  "path": "...",
  "method": "...",
  "traceparent": "..."
}
```

---

## Clearing a Reference

To remove an existing `externalRegistryRef`, send the source object without the field (or with `null`):

```json
{
  "$type": "docker",
  "imageUri": "myrepo/myimage:1.0"
}
```

The stored reference is cleared; subsequent reads return no `externalRegistryRef`.
