# Quickstart: Init Container Logs

## What This Feature Does

Extends the deployment pod log streaming endpoint to support retrieving logs from any container in a pod (including init containers), and adds container discovery to the pod listing endpoint.

## Key Changes

1. **Log endpoint** (`GET /api/v1/deployments/{id}/pods/{podId}/logs`): New optional `containerName` query parameter
2. **Pod listing** (`GET /api/v1/deployments/{id}/pods`): Response now includes container details (name, type, state)
3. **Loggability rules**: Relaxed for init containers — terminated init containers are loggable without `previous=true`

## Files to Modify

### Web Layer
- `DeploymentController.java` — add `containerName` query param to `subscribeToLogs`

### Service Layer
- `DeploymentLogsService.java` — pass `containerName` through
- `DeploymentManager.java` (interface) — add `containerName` parameter to `getContainerResourceForLogs`
- `AbstractDeploymentManager.java` — accept `containerName`, search init container statuses, relax loggability rules for init containers
- `DeploymentService.java` — no changes (pod listing already delegates to deployment manager)

### Model Layer
- `ContainerDetails.java` — new model class
- `ContainerType.java` — new enum (INIT, REGULAR)
- `PodInfo.java` — add `containers` field

### DTO Layer
- `ContainerDetailsDto.java` — new DTO record
- `PodInfoDto.java` — add `containers` field

### Mapper Layer
- `DeploymentDtoMapper.java` — add mapping for `ContainerDetails` → `ContainerDetailsDto`

### Tests
- `DeploymentLogsServiceTest.java` — update mocks for new parameter
- `NimDeploymentManagerTest.java` — add init container log tests
- `KnativeDeploymentManagerTest.java` — add init container log tests
- `InferenceDeploymentManagerTest.java` — add init container log tests

## Verification

```bash
./gradlew testFast
./gradlew checkstyleMain checkstyleTest
```
