# Adapter Deployments

## Purpose
This spec describes deployments of type ADAPTER — an image-based deployment family member for DIAL adapters. Adapter deployments add no fields beyond the image-based contract; the `type: ADAPTER` discriminator is the sole distinction.

Status: **Implemented**

## Key Terms
- **Adapter deployment**: A deployment of type `ADAPTER` that runs a DIAL adapter container. Fully defined by the image-based contract.
- **Image-based contract**: The set of fields inherited from `ImageBasedDeploymentDto`: `imageDefinitionId` (required), `imageDefinitionName` (required), `imageDefinitionVersion` (required), plus all base deployment fields.

## Requirements

### Requirement: Adapter deployment has no additional fields beyond the image-based contract
An adapter deployment SHALL carry exactly the image-based contract fields and nothing more. There are no adapter-specific configuration fields.

Status: **Implemented**

#### Scenario: Create adapter deployment
- **WHEN** `POST /api/v1/deployments` is called with `type: ADAPTER` and a valid `imageDefinitionId`
- **THEN** an adapter deployment is created with only the image-based contract fields

#### Scenario: Retrieve adapter deployment
- **WHEN** `GET /api/v1/deployments/{id}` is called for an ADAPTER deployment
- **THEN** the response body contains image-based fields and no additional type-specific fields

### Requirement: Adapter deployment must reference an adapter image definition
An adapter deployment SHALL reference an adapter image definition via `imageDefinitionId`.

Status: **Implemented**

#### Scenario: Image definition linked
- **WHEN** an adapter deployment is created with a valid `imageDefinitionId`
- **THEN** the deployment is linked to the specified adapter image definition

#### Scenario: Missing imageDefinitionId rejected
- **WHEN** `POST /api/v1/deployments` is called with `type: ADAPTER` without `imageDefinitionId`
- **THEN** the system responds with 400

### Requirement: Adapter deployment requires KNative enabled
An adapter deployment SHALL require `K8S_KNATIVE_ENABLED=true` to deploy. When KNative is disabled, the `KnativeDeploymentManager` bean is not instantiated (`@ConditionalOnProperty`) and no alternative deployment backend exists for image-based types.

Status: **Implemented**

#### Scenario: Deploy with KNative disabled
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called for an ADAPTER deployment with `K8S_KNATIVE_ENABLED=false`
- **THEN** the deploy operation fails because no suitable deployment manager is available

## Implementation Notes
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.CreateAdapterDeploymentRequestDto` (empty body — extends `CreateImageBasedDeploymentRequestDto`)
- Response DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.AdapterDeploymentDto` (empty body — extends `ImageBasedDeploymentDto`)
- Kubernetes backend: KNative service (when `K8S_KNATIVE_ENABLED=true`)
- Related specs: `deployments` (base + lifecycle), `adapter-image-definitions`, `kubernetes-manifests`
