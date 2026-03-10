# Interceptor Deployments

## Purpose
This spec describes deployments of type INTERCEPTOR — an image-based deployment family member for DIAL interceptors. Interceptor deployments add no fields beyond the image-based contract; the `type: INTERCEPTOR` discriminator is the sole distinction.

Status: **Implemented**

## Key Terms
- **Interceptor deployment**: A deployment of type `INTERCEPTOR` that runs a DIAL interceptor container. Fully defined by the image-based contract.
- **Image-based contract**: The set of fields inherited from `ImageBasedDeploymentDto`: a `source` field of type `DeploymentSourceDto` (polymorphic, `$type` discriminator) plus all base deployment fields. Two source types are supported: `internal_image` (references a managed image definition by ID or type+name+version triple) and `image_reference` (direct Docker image URI, no image definition required).

## Requirements

### Requirement: Interceptor deployment has no additional fields beyond the image-based contract
An interceptor deployment SHALL carry exactly the image-based contract fields (including the `source` field) and nothing more. There are no interceptor-specific configuration fields.

Status: **Implemented**

#### Scenario: Create interceptor deployment with internal_image source
- **WHEN** `POST /api/v1/deployments` is called with `type: INTERCEPTOR` and `source: { "$type": "internal_image", "imageDefinitionId": "<uuid>" }`
- **THEN** an interceptor deployment is created with only the image-based contract fields

#### Scenario: Create interceptor deployment with image_reference source
- **WHEN** `POST /api/v1/deployments` is called with `type: INTERCEPTOR` and `source: { "$type": "image_reference", "imageReference": "<docker-image>" }`
- **THEN** an interceptor deployment is created using the direct image reference (no image definition required)

#### Scenario: Retrieve interceptor deployment
- **WHEN** `GET /api/v1/deployments/{id}` is called for an INTERCEPTOR deployment
- **THEN** the response body contains the image-based fields including `source` and no additional type-specific fields

### Requirement: Interceptor deployment must provide a valid source
An interceptor deployment SHALL provide a `source` object with one of two types:
1. **`internal_image`**: References an image definition by `imageDefinitionId` (UUID) OR by `(imageDefinitionType, imageDefinitionName, imageDefinitionVersion)` triple.
2. **`image_reference`**: Provides a direct Docker image URI via `imageReference` (validated by `@ValidDockerImageName`). No image definition required.

Status: **Implemented**

#### Scenario: Internal image source linked by ID
- **WHEN** an interceptor deployment is created with `source.$type: "internal_image"` and a valid `imageDefinitionId`
- **THEN** the deployment is linked to the specified interceptor image definition

#### Scenario: Internal image source linked by type + name + version
- **WHEN** an interceptor deployment is created with `source.$type: "internal_image"`, `imageDefinitionType: INTERCEPTOR`, `imageDefinitionName`, and `imageDefinitionVersion`
- **THEN** the image definition is resolved by type + name + version and the deployment is created successfully

#### Scenario: Image reference source
- **WHEN** an interceptor deployment is created with `source.$type: "image_reference"` and a valid `imageReference`
- **THEN** the deployment is created with the direct Docker image reference

#### Scenario: Incomplete internal_image source rejected
- **WHEN** `POST /api/v1/deployments` is called with `type: INTERCEPTOR` and an `internal_image` source missing both `imageDefinitionId` and a complete type+name+version triple
- **THEN** the system responds with 400

### Requirement: Interceptor deployment requires KNative enabled
An interceptor deployment SHALL require `app.knative.enabled=true` to deploy. When KNative is disabled, the `KnativeDeploymentManager` bean is not instantiated (`@ConditionalOnProperty`) and no alternative deployment backend exists for image-based types.

Status: **Implemented**

#### Scenario: Deploy with KNative disabled
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called for an INTERCEPTOR deployment with `app.knative.enabled=false`
- **THEN** the deploy operation fails because no suitable deployment manager is available

## Implementation Notes
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.CreateInterceptorDeploymentRequestDto` (empty body — extends `CreateImageBasedDeploymentRequestDto`)
- Response DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.InterceptorDeploymentDto` (empty body — extends `ImageBasedDeploymentDto`)
- Kubernetes backend: KNative service (when `app.knative.enabled=true`)
- Related specs: `deployments` (base + lifecycle), `interceptor-image-definitions`, `kubernetes-manifests`
