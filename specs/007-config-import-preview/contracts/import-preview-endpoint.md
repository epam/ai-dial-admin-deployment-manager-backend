# API Contract: POST /api/v1/configs/import/preview

**Feature**: `007-config-import-preview`
**Date**: 2026-03-16

---

## Endpoint

| Property | Value |
|----------|-------|
| Method | `POST` |
| Path | `/api/v1/configs/import/preview` |
| Content-Type | `multipart/form-data` |
| Authentication | Same as `POST /api/v1/configs/import` |

---

## Request Parameters

| Parameter | Location | Type | Required | Notes |
|-----------|----------|------|----------|-------|
| `file` | form part (`@RequestPart`) | `MultipartFile` | Yes | ZIP archive containing a config JSON file |
| `resolutionPolicy` | query param (`@RequestParam`) | `ConflictResolutionPolicy` enum | Yes | `OVERWRITE`, `SKIP_IF_EXISTS`, or `FAIL_IF_EXISTS` |

---

## Response: 200 OK

**Body**: `ImportConfigPreviewDto` (JSON)

```jsonc
{
  "mcpImageDefinitions": [
    {
      "importAction": "CREATE",   // CREATE | UPDATE | SKIP | FAIL
      "prev": null,               // null for CREATE; existing McpImageDefinitionDto otherwise
      "next": { /* McpImageDefinitionDto */ }  // null for SKIP; incoming entity otherwise
    }
  ],
  "adapterImageDefinitions": [ /* ImportComponentDto<AdapterImageDefinitionDto>[] */ ],
  "interceptorImageDefinitions": [ /* ImportComponentDto<InterceptorImageDefinitionDto>[] */ ],
  "globalImageBuildDomainWhitelist": {
    "importAction": "UPDATE",
    "prev": ["existing.com"],
    "next": ["existing.com", "incoming.com", "new.com"]
  },                              // null / absent when incoming whitelist is empty
  "mcpDeployments": [ /* ImportComponentDto<McpDeploymentDto>[] */ ],
  "adapterDeployments": [ /* ImportComponentDto<AdapterDeploymentDto>[] */ ],
  "interceptorDeployments": [ /* ImportComponentDto<InterceptorDeploymentDto>[] */ ],
  "nimDeployments": [ /* ImportComponentDto<NimDeploymentDto>[] */ ],
  "inferenceDeployments": [ /* ImportComponentDto<InferenceDeploymentDto>[] */ ]
}
```

All list fields are always present and non-null (empty list `[]` when ZIP has no entities of that type).
`globalImageBuildDomainWhitelist` is `null` / absent when the incoming whitelist is empty or not present in the ZIP.

---

## Error Responses

| Status | Condition |
|--------|-----------|
| `400 Bad Request` | `resolutionPolicy` parameter missing or invalid value |
| `400 Bad Request` | Uploaded file is not a valid ZIP archive |
| `400 Bad Request` | ZIP does not contain the expected config file (same error as `/import`) |
| `400 Bad Request` | ZIP contains multiple config files with the same name |
| `401 Unauthorized` | Missing or invalid auth token (when security mode is `oidc` or `basic`) |

Error body follows the existing `ErrorView` schema: `{ path, method, status, error, message, traceparent }`.

---

## Side Effects

None. The endpoint is strictly read-only — no entities are created, updated, or deleted.
