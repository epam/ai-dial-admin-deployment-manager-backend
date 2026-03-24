# Application Deployments

## Purpose
This spec describes deployments of type APPLICATION — an image-based deployment family member for DIAL applications. Application deployments add no fields beyond the image-based contract; the `type: APPLICATION` discriminator is the sole distinction.

Status: **Implemented**

## Key Terms
- **Application deployment**: A deployment of type `APPLICATION` that runs a DIAL application container. Fully defined by the image-based contract.
- **Image-based contract**: The set of fields inherited from `ImageBasedDeploymentDto`: a `source` field of type `DeploymentSourceDto` (polymorphic, `$type` discriminator) plus all base deployment fields. Two source types are supported: `internal_image` (references a managed image definition by ID or type+name+version triple) and `image_reference` (direct Docker image URI, no image definition required).

## Requirements

### Requirement: Application deployment has no additional fields beyond the image-based contract
An application deployment SHALL carry exactly the image-based contract fields (including the `source` field) and nothing more. There are no application-specific configuration fields.

Status: **Implemented**

#### Scenario: Create application deployment with internal_image source
- **WHEN** `POST /api/v1/deployments` is called with `type: APPLICATION` and `source: { "$type": "internal_image", "imageDefinitionId": "<uuid>" }`
- **THEN** an application deployment is created with only the image-based contract fields

#### Scenario: Create application deployment with image_reference source
- **WHEN** `POST /api/v1/deployments` is called with `type: APPLICATION` and `source: { "$type": "image_reference", "imageReference": "<docker-image>" }`
- **THEN** an application deployment is created using the direct image reference (no image definition required)

#### Scenario: Retrieve application deployment
- **WHEN** `GET /api/v1/deployments/{id}` is called for an APPLICATION deployment
- **THEN** the response body contains image-based fields including `source` and no additional type-specific fields

### Requirement: Application deployment must provide a valid source
An application deployment SHALL provide a `source` object with one of two types:
1. **`internal_image`**: References an image definition by `imageDefinitionId` (UUID) OR by `(imageDefinitionType, imageDefinitionName, imageDefinitionVersion)` triple.
2. **`image_reference`**: Provides a direct Docker image URI via `imageReference` (validated by `@ValidDockerImageName`). No image definition required.

Status: **Implemented**

#### Scenario: Internal image source linked by ID
- **WHEN** an application deployment is created with `source.$type: "internal_image"` and a valid `imageDefinitionId`
- **THEN** the deployment is linked to the specified application image definition

#### Scenario: Internal image source linked by type + name + version
- **WHEN** an application deployment is created with `source.$type: "internal_image"`, `imageDefinitionType: APPLICATION`, `imageDefinitionName`, and `imageDefinitionVersion`
- **THEN** the image definition is resolved by type + name + version and the deployment is created successfully

#### Scenario: Image reference source
- **WHEN** an application deployment is created with `source.$type: "image_reference"` and a valid `imageReference`
- **THEN** the deployment is created with the direct Docker image reference

#### Scenario: Incomplete internal_image source rejected
- **WHEN** `POST /api/v1/deployments` is called with `type: APPLICATION` and an `internal_image` source missing both `imageDefinitionId` and a complete type+name+version triple
- **THEN** the system responds with 400

### Requirement: Application deployment requires KNative enabled
An application deployment SHALL require `app.knative.enabled=true` to deploy. When KNative is disabled, the `KnativeDeploymentManager` bean is not instantiated (`@ConditionalOnProperty`) and no alternative deployment backend exists for image-based types.

Status: **Implemented**

#### Scenario: Deploy with KNative disabled
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called for an APPLICATION deployment with `app.knative.enabled=false`
- **THEN** the deploy operation fails because no suitable deployment manager is available

## Implementation Notes
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.CreateApplicationDeploymentRequestDto` (empty body — extends `CreateImageBasedDeploymentRequestDto`)
- Response DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.ApplicationDeploymentDto` (empty body — extends `ImageBasedDeploymentDto`)
- Kubernetes backend: KNative service (when `app.knative.enabled=true`)
- Related specs: `deployments` (base + lifecycle), `application-image-definitions`, `kubernetes-manifests`
