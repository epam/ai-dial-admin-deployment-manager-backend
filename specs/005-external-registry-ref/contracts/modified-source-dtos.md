# API Contract: Modified Source DTOs

**Feature**: `005-external-registry-ref`

All changes are backward-compatible additions. The `externalRegistryRef` field is optional in all cases.

---

## Image Definition Sources

### DockerImageSourceDto (modified)

Used in `POST /api/v1/images/definitions` and `PUT /api/v1/images/definitions/{id}` request bodies, and in all image definition responses.

**Added field**: `externalRegistryRef` (optional)

```json
{
  "$type": "docker",
  "imageUri": "myrepo/myimage:1.0",
  "entrypoint": ["python", "app.py"],
  "externalRegistryRef": {
    "$type": "mcp-registry",
    "packageName": "my-mcp-server"
  }
}
```

Without ref (existing behaviour unchanged):
```json
{
  "$type": "docker",
  "imageUri": "myrepo/myimage:1.0"
}
```

---

### GitDockerfileImageSourceDto (modified)

**Added field**: `externalRegistryRef` (optional)

```json
{
  "$type": "git",
  "url": "https://github.com/owner/repo",
  "branchName": "main",
  "externalRegistryRef": {
    "$type": "github",
    "repo": "owner/repo"
  }
}
```

---

## Deployment Sources (MCP / Adapter / Interceptor)

### ImageReferenceDeploymentSourceDto (modified — response)

Returned in `GET /api/v1/deployments` and `GET /api/v1/deployments/{id}` for deployments using a direct image reference.

**Added field**: `externalRegistryRef` (optional)

```json
{
  "$type": "image_reference",
  "imageReference": "myrepo/myimage:1.0",
  "externalRegistryRef": {
    "$type": "generic",
    "url": "https://some-registry.example.com/packages/my-tool"
  }
}
```

---

### CreateImageReferenceDeploymentSourceRequestDto (modified — request)

Used in `POST /api/v1/deployments` and `PUT /api/v1/deployments/{id}` for deployments using a direct image reference.

**Added field**: `externalRegistryRef` (optional)

```json
{
  "$type": "image_reference",
  "imageReference": "myrepo/myimage:1.0",
  "externalRegistryRef": {
    "$type": "mcp-registry",
    "packageName": "my-mcp-server"
  }
}
```

---

## Out-of-scope Sources (unchanged)

These source DTOs are **not modified**. The `externalRegistryRef` field does not exist on them.

| DTO | Deployment type | Reason |
|-----|-----------------|--------|
| `InferenceDeploymentHuggingFaceSourceDto` | Inference | Registry-bound; `modelName` IS the reference |
| `NimDeploymentNgcRegistrySourceDto` | NIM | Registry-bound; `imageRef` IS the reference |
| `InternalImageDeploymentSourceDto` | MCP/Adapter/Interceptor | Delegates to ImageDefinition; ref lives on ImageDefinition's source |
