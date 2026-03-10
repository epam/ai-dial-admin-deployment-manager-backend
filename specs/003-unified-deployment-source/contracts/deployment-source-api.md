# API Contract: Deployment Source

**Feature**: 003-unified-deployment-source
**Date**: 2026-03-10
**Base path**: `/api/v1/deployments`

## Source Object Schemas

### DeploymentSourceDto (response, Knative deployments)

Discriminator: `$type` property.

**`internal_image`**:
```json
{
  "$type": "internal_image",
  "imageDefinitionId": "uuid-string",
  "imageDefinitionName": "my-adapter",
  "imageDefinitionVersion": "1.0.0"
}
```

**`image_reference`**:
```json
{
  "$type": "image_reference",
  "imageReference": "registry.example.com/my-image:latest"
}
```

### CreateDeploymentSourceRequestDto (request, Knative deployments)

**`internal_image` (by ID)**:
```json
{
  "$type": "internal_image",
  "imageDefinitionId": "uuid-string"
}
```

**`internal_image` (by type + name + version)**:
```json
{
  "$type": "internal_image",
  "imageDefinitionType": "MCP",
  "imageDefinitionName": "my-mcp-server",
  "imageDefinitionVersion": "2.1.0"
}
```

**`image_reference`**:
```json
{
  "$type": "image_reference",
  "imageReference": "registry.example.com/my-image:latest"
}
```

### NimDeploymentSourceDto (unchanged)

```json
{
  "$type": "ngc_registry",
  "imageRef": "nvcr.io/nvidia/nim:latest"
}
```

### InferenceDeploymentSourceDto (unchanged)

```json
{
  "$type": "huggingface",
  "modelName": "meta-llama/Llama-2-7b"
}
```

## Affected Endpoints

### POST /api/v1/deployments (Create)

**Before** (Knative types — MCP, Adapter, Interceptor):
```json
{
  "type": "MCP",
  "name": "my-mcp",
  "displayName": "My MCP Server",
  "imageDefinitionId": "uuid-string"
}
```

**After** (Knative types):
```json
{
  "type": "MCP",
  "name": "my-mcp",
  "displayName": "My MCP Server",
  "source": {
    "$type": "internal_image",
    "imageDefinitionId": "uuid-string"
  }
}
```

**New** (Knative types with direct image reference):
```json
{
  "type": "MCP",
  "name": "my-mcp",
  "displayName": "My MCP Server",
  "source": {
    "$type": "image_reference",
    "imageReference": "registry.example.com/my-mcp:1.0"
  }
}
```

**NIM and Inference**: Request body unchanged.

### PUT /api/v1/deployments/{id} (Update)

Same contract changes as Create for Knative types. NIM and Inference unchanged.

### GET /api/v1/deployments/{id} (Get)

**Before** (Knative types):
```json
{
  "type": "MCP",
  "name": "my-mcp",
  "imageDefinitionId": "uuid-string",
  "imageDefinitionType": "MCP",
  "imageDefinitionName": "my-mcp-server",
  "imageDefinitionVersion": "1.0.0",
  ...
}
```

**After** (Knative types with internal_image):
```json
{
  "type": "MCP",
  "name": "my-mcp",
  "source": {
    "$type": "internal_image",
    "imageDefinitionId": "uuid-string",
    "imageDefinitionName": "my-mcp-server",
    "imageDefinitionVersion": "1.0.0"
  },
  ...
}
```

**After** (Knative types with image_reference):
```json
{
  "type": "MCP",
  "name": "my-mcp",
  "source": {
    "$type": "image_reference",
    "imageReference": "registry.example.com/my-mcp:1.0"
  },
  ...
}
```

**NIM and Inference**: Response body unchanged.

### GET /api/v1/deployments (List)

Same response format changes as Get. Each deployment in the list uses the new source format.

## Validation Rules

| Source Type | Validation | Error |
|-------------|-----------|-------|
| `internal_image` | Either `imageDefinitionId` OR (`imageDefinitionType` + `imageDefinitionName` + `imageDefinitionVersion`) must be provided | 400: "Either imageDefinitionId or (imageDefinitionType, imageDefinitionName, imageDefinitionVersion) must be set" |
| `image_reference` | `imageReference` must be a valid Docker image name | 400: Docker image name validation error |
| Source type mismatch | Source `$type` must be compatible with deployment type | 400: Source type validation error |
| Missing source | Knative deployments require a non-null `source` | 400: Source is required |

## Export/Import Contract

**Export** (internal_image source):
```json
{
  "$type": "internal_image",
  "imageDefinitionType": "MCP",
  "imageDefinitionName": "my-mcp-server",
  "imageDefinitionVersion": "1.0.0"
}
```
Note: `imageDefinitionId` is excluded from export.

**Import**: System resolves `imageDefinitionId` from (`imageDefinitionType`, `imageDefinitionName`, `imageDefinitionVersion`) triple.
