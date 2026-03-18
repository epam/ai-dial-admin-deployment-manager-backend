# Contract: Authorization Behavior Changes

## @FullAdminOnly Annotated Endpoints

All endpoints below return **403 Forbidden** for READ_ONLY_ADMIN users. FULL_ADMIN users are unaffected.

### DeploymentController (`/api/v1/deployments`)

| Method | Path | Operation |
|---|---|---|
| POST | `/` | createDeployment |
| POST | `/duplicate` | duplicateDeployment |
| POST | `/change-image` | changeImage |
| PUT | `/{id}` | updateDeployment |
| DELETE | `/{id}` | deleteDeployment |
| POST | `/{id}/deploy` | deploy |
| POST | `/{id}/undeploy` | undeploy |

### ConfigController (`/api/v1/configs`)

| Method | Path | Operation |
|---|---|---|
| POST | `/import` | importConfig |

### ImageDefinitionController (`/api/v1/images/definitions`)

| Method | Path | Operation |
|---|---|---|
| POST | `/` | createImageDefinition |
| PUT | `/{id}` | updateImageDefinition |
| DELETE | `/{id}` | deleteImageDefinition |

### ImageBuildController (`/api/v1/images/builds`)

| Method | Path | Operation |
|---|---|---|
| POST | `/` | buildImage |

### DisposableResourceController (`/api/v1/disposable`)

| Method | Path | Operation |
|---|---|---|
| POST | `/clean` | clean |

### GlobalDomainWhitelistController (`/api/v1/global-whitelist`)

| Method | Path | Operation |
|---|---|---|
| POST | `/image-build` | updateDomainWhitelistForImageBuild |

## Endpoints NOT annotated (read-only, accessible to all authenticated users)

- All GET endpoints across all controllers
- `POST /api/v1/configs/export-preview` (read-only POST)
- `POST /api/v1/configs/export` (read-only POST)
- `POST /api/v1/configs/import-preview` (read-only POST, computes preview only)
- All McpRegistryController endpoints (read-only)
- All HuggingFaceController endpoints (read-only)
- All TopicController endpoints (read-only)
- `GET /api/v1/deployments/mcp/{deploymentId}/tools|resources|prompts`

## Unaffected endpoints

- `/api/v1/health/**` — public, no auth
- `/api/internal/**` — public, no auth
- `/swagger-ui/**` and `/v3/api-docs/**` — conditionally public

## Error Response (403)

```json
{
  "path": "/api/v1/deployments",
  "method": "POST",
  "status": 403,
  "error": "Forbidden",
  "message": "Access Denied",
  "traceparent": "00-..."
}
```
