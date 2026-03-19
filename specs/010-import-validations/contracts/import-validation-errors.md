# Contract: Import Validation Error Response

## Affected Endpoints

- `POST /api/v1/configs/import` — returns 400 BAD_REQUEST when validation fails
- `POST /api/v1/configs/import/preview` — includes validation errors in preview response

## Error Response (import endpoint)

When imported entities fail validation, the import endpoint returns 400 BAD_REQUEST with an `ErrorView` body. The `message` field contains a structured description of all violations grouped by entity.

### Example Error Response

```json
{
  "path": "/api/v1/configs/import",
  "method": "POST",
  "status": 400,
  "error": "Bad Request",
  "message": "Import validation failed:\n[MCP_DEPLOYMENT 'My-Deploy'] Field [name]: Deployment ID must contain only lowercase Latin letters, numbers, and hyphens\n[MCP_DEPLOYMENT 'My-Deploy'] Field [displayName]: must not be null\n[ADAPTER_IMAGE_DEFINITION 'my-image'] Field [version]: Must be a valid semantic version (e.g. 1.0.0)",
  "traceparent": "00-abc123..."
}
```

### Message Format

Each violation line follows the pattern:
```
[{ENTITY_TYPE} '{entity_identifier}'] Field [{field_path}]: {violation_message}
```

## Preview Response Changes

The preview response (`ImportConfigPreviewDto`) gains an optional `validationErrors` field:

```json
{
  "mcpDeployments": [...],
  "adapterImageDefinitions": [...],
  "validationErrors": [
    {
      "entityType": "MCP_DEPLOYMENT",
      "entityIdentifier": "My-Deploy",
      "fieldPath": "name",
      "message": "Deployment ID must contain only lowercase Latin letters, numbers, and hyphens"
    }
  ]
}
```

When no validation errors exist, the field is either absent or an empty list.

## Backward Compatibility

- Valid imports: No change in behavior or response format
- Valid previews: No change; `validationErrors` is empty/absent
- Existing error handling for deserialization failures (malformed JSON, unknown types) is unchanged
