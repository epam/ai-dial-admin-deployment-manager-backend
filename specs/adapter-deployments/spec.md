# Adapter Deployments

## Purpose
This spec describes deployments of type ADAPTER — an image-based deployment family member for DIAL adapters. Adapter deployments add no fields beyond the image-based contract; the `type: ADAPTER` discriminator is the sole distinction.

Status: **Implemented**

## Key Terms
- **Adapter deployment**: A deployment of type `ADAPTER` that runs a DIAL adapter container. Fully defined by the image-based contract.
- **Image-based contract**: The set of fields inherited from `ImageBasedDeploymentDto`: a `source` field of type `DeploymentSourceDto` (polymorphic, `$type` discriminator) plus all base deployment fields. Two source types are supported: `internal_image` (references a managed image definition by ID or type+name+version triple) and `image_reference` (direct Docker image URI, no image definition required).

## Requirements

### Requirement: Adapter deployment has no additional fields beyond the image-based contract
An adapter deployment SHALL carry exactly the image-based contract fields (including the `source` field) and nothing more. There are no adapter-specific configuration fields.

Status: **Implemented**

#### Scenario: Create adapter deployment with internal_image source
- **WHEN** `POST /api/v1/deployments` is called with `type: ADAPTER` and `source: { "$type": "internal_image", "imageDefinitionId": "<uuid>" }`
- **THEN** an adapter deployment is created with only the image-based contract fields

#### Scenario: Create adapter deployment with image_reference source
- **WHEN** `POST /api/v1/deployments` is called with `type: ADAPTER` and `source: { "$type": "image_reference", "imageReference": "<docker-image>" }`
- **THEN** an adapter deployment is created using the direct image reference (no image definition required)

#### Scenario: Retrieve adapter deployment
- **WHEN** `GET /api/v1/deployments/{id}` is called for an ADAPTER deployment
- **THEN** the response body contains image-based fields including `source` and no additional type-specific fields

### Requirement: Adapter deployment must provide a valid source
An adapter deployment SHALL provide a `source` object with one of two types:
1. **`internal_image`**: References an image definition by `imageDefinitionId` (UUID) OR by `(imageDefinitionType, imageDefinitionName, imageDefinitionVersion)` triple.
2. **`image_reference`**: Provides a direct Docker image URI via `imageReference` (validated by `@ValidDockerImageName`). No image definition required.

Status: **Implemented**

#### Scenario: Internal image source linked by ID
- **WHEN** an adapter deployment is created with `source.$type: "internal_image"` and a valid `imageDefinitionId`
- **THEN** the deployment is linked to the specified adapter image definition

#### Scenario: Internal image source linked by type + name + version
- **WHEN** an adapter deployment is created with `source.$type: "internal_image"`, `imageDefinitionType: ADAPTER`, `imageDefinitionName`, and `imageDefinitionVersion`
- **THEN** the image definition is resolved by type + name + version and the deployment is created successfully

#### Scenario: Image reference source
- **WHEN** an adapter deployment is created with `source.$type: "image_reference"` and a valid `imageReference`
- **THEN** the deployment is created with the direct Docker image reference

#### Scenario: Incomplete internal_image source rejected
- **WHEN** `POST /api/v1/deployments` is called with `type: ADAPTER` and an `internal_image` source missing both `imageDefinitionId` and a complete type+name+version triple
- **THEN** the system responds with 400

### Requirement: Adapter deployment requires KNative enabled
An adapter deployment SHALL require `app.knative.enabled=true` to deploy. When KNative is disabled, the `KnativeDeploymentManager` bean is not instantiated (`@ConditionalOnProperty`) and no alternative deployment backend exists for image-based types.

Status: **Implemented**

#### Scenario: Deploy with KNative disabled
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called for an ADAPTER deployment with `app.knative.enabled=false`
- **THEN** the deploy operation fails because no suitable deployment manager is available

## Implementation Notes
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.CreateAdapterDeploymentRequestDto` (empty body — extends `CreateImageBasedDeploymentRequestDto`)
- Response DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.AdapterDeploymentDto` (empty body — extends `ImageBasedDeploymentDto`)
- Kubernetes backend: KNative service (when `app.knative.enabled=true`)
- Related specs: `deployments` (base + lifecycle), `adapter-image-definitions`, `kubernetes-manifests`
