# Contracts: Application Type

No new API endpoints are introduced. The existing polymorphic endpoints automatically handle the new "APPLICATION" type via Jackson `@JsonSubTypes` deserialization.

## Affected Existing Endpoints

All existing image definition and deployment endpoints accept/return the new `$type: "application"` discriminator:

- `POST /api/v1/images/definitions` — accepts `{"$type": "application", ...}`
- `GET /api/v1/images/definitions?type=APPLICATION` — filters by APPLICATION type
- `POST /api/v1/deployments` — accepts `{"$type": "application", ...}`
- `GET /api/v1/deployments?type=APPLICATION` — filters by APPLICATION type
- `POST /api/v1/config/export` / `POST /api/v1/config/import` — includes application maps

## JSON Examples

### Application Image Definition Request

```json
{
  "$type": "application",
  "name": "my-application",
  "version": "1.0.0",
  "description": "An application image",
  "source": {
    "$type": "docker",
    "uri": "https://registry.example.com/my-app",
    "entrypoint": ["python", "main.py"]
  },
  "imageBuilder": "BUILDKIT_ROOTLESS"
}
```

### Application Deployment Request

```json
{
  "$type": "application",
  "name": "my-app-deployment",
  "displayName": "My Application",
  "description": "Application deployment",
  "source": {
    "$type": "internal_image",
    "imageDefinitionId": "<uuid>"
  },
  "metadata": {
    "envVarDefinitions": []
  }
}
```
