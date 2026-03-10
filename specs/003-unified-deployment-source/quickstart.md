# Quickstart: Unified Deployment Source

**Feature**: 003-unified-deployment-source
**Date**: 2026-03-10

## What Changed

The deployment source model has been unified. Previously, Knative deployments (MCP, Adapter, Interceptor) stored image definition references as individual fields, while NIM and Inference had separate source JSON columns on their subtype tables. Now all deployment types store source information as a typed JSON object on the base deployment table.

**New capability**: Knative deployments can now use a direct Docker image reference (`image_reference` source type) without requiring an image definition.

## Key Files to Understand

| Area | Key Files |
|------|-----------|
| Domain model | `model/deployment/Source.java`, `InternalImageSource.java`, `ImageReferenceSource.java`, `HuggingFaceSource.java`, `NgcRegistrySource.java` |
| DTOs (response) | `web/dto/deployment/DeploymentSourceDto.java`, `InternalImageDeploymentSourceDto.java`, `ImageReferenceDeploymentSourceDto.java` |
| DTOs (request) | `web/dto/deployment/CreateDeploymentSourceRequestDto.java`, `CreateInternalImageDeploymentSourceRequestDto.java`, `CreateImageReferenceDeploymentSourceRequestDto.java` |
| Mapper | `web/mapper/DeploymentDtoMapper.java` (source mapping via `@AfterMapping`) |
| Persistence | `dao/entity/deployment/PersistenceSource.java`, `DeploymentEntity.java` (source JSON column) |
| Service | `service/deployment/DeploymentService.java` (`validateSourceForDeploymentType`), `KnativeDeploymentManager.java` (`resolveImageName`) |
| Migration | `db/migration/common/V1_50__UnifyDeploymentSourceBase.java` |
| Export | `configuration/export/InternalImageSourceExportMixIn.java` |

## How to Test

```bash
# Run all tests (includes migration, CRUD, validation, export/import tests)
./gradlew test

# Run specific deployment tests
./gradlew test --tests "*DeploymentFunctionalTest*"
./gradlew test --tests "*DeploymentControllerTest*"
./gradlew test --tests "*KnativeDeploymentManagerTest*"
./gradlew test --tests "*ConfigExportImportFunctionalTest*"

# Verify code style
./gradlew checkstyleMain checkstyleTest
```

## Quick API Examples

**Create MCP deployment with image_reference**:
```bash
curl -X POST /api/v1/deployments -H "Content-Type: application/json" -d '{
  "type": "MCP",
  "name": "my-mcp",
  "displayName": "My MCP Server",
  "source": {
    "$type": "image_reference",
    "imageReference": "registry.example.com/my-mcp:1.0"
  }
}'
```

**Create Adapter deployment with internal_image (by ID)**:
```bash
curl -X POST /api/v1/deployments -H "Content-Type: application/json" -d '{
  "type": "ADAPTER",
  "name": "my-adapter",
  "displayName": "My Adapter",
  "source": {
    "$type": "internal_image",
    "imageDefinitionId": "550e8400-e29b-41d4-a716-446655440000"
  }
}'
```
