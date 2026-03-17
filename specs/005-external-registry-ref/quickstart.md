# Quickstart: External Registry Reference for Sources

**Feature**: `005-external-registry-ref`

---

## Attaching a registry reference to an image definition

### Git dockerfile source → MCP registry

```http
POST /api/v1/images/definitions
Content-Type: application/json

{
  "$type": "mcp",
  "name": "my-mcp-server",
  "version": "1.0.0",
  "source": {
    "$type": "git",
    "url": "https://github.com/owner/my-mcp-server",
    "branchName": "main",
    "externalRegistryRef": {
      "$type": "mcp-registry",
      "packageName": "my-mcp-server"
    }
  }
}
```

### Docker image source → GitHub reference

```http
POST /api/v1/images/definitions
Content-Type: application/json

{
  "$type": "adapter",
  "name": "my-adapter",
  "version": "2.1.0",
  "source": {
    "$type": "docker",
    "imageUri": "myorg/my-adapter:2.1.0",
    "externalRegistryRef": {
      "$type": "github",
      "repo": "myorg/my-adapter"
    }
  }
}
```

---

## Attaching a registry reference to a deployment

### Direct image reference source

```http
POST /api/v1/deployments
Content-Type: application/json

{
  "$type": "mcp",
  "name": "my-deployment",
  "displayName": "My MCP Server",
  "source": {
    "$type": "image_reference",
    "imageReference": "myorg/my-mcp:latest",
    "externalRegistryRef": {
      "$type": "mcp-registry",
      "packageName": "my-mcp-server"
    }
  },
  ...
}
```

---

## Using GenericRef for unlisted registries

```http
PUT /api/v1/images/definitions/{id}
Content-Type: application/json

{
  "$type": "mcp",
  "source": {
    "$type": "git",
    "url": "https://github.com/owner/repo",
    "externalRegistryRef": {
      "$type": "generic",
      "url": "https://custom-registry.internal/tools/my-tool"
    }
  },
  ...
}
```

---

## Clearing a registry reference

Send the source without the `externalRegistryRef` field:

```http
PUT /api/v1/images/definitions/{id}
Content-Type: application/json

{
  "$type": "mcp",
  "source": {
    "$type": "git",
    "url": "https://github.com/owner/repo",
    "branchName": "main"
  },
  ...
}
```

The stored reference is removed. Subsequent GET responses will not include `externalRegistryRef`.

---

## Reading the registry reference

```http
GET /api/v1/images/definitions/{id}

HTTP/1.1 200 OK
{
  "$type": "mcp",
  "name": "my-mcp-server",
  "source": {
    "$type": "git",
    "url": "https://github.com/owner/my-mcp-server",
    "branchName": "main",
    "externalRegistryRef": {
      "$type": "mcp-registry",
      "packageName": "my-mcp-server"
    }
  },
  ...
}
```

---

## Building a catalog URL client-side

The backend returns the typed reference; clients compute display URLs using registry-specific templates:

| `$type` | URL pattern | Example |
|---------|-------------|---------|
| `mcp-registry` | `https://registry.mcp.io/servers/{packageName}` | `https://registry.mcp.io/servers/my-mcp-server` |
| `github` | `https://github.com/{repo}` | `https://github.com/owner/my-project` |
| `generic` | `{url}` (used directly) | `https://custom-registry.internal/tools/my-tool` |
