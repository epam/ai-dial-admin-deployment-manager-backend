# Interceptor Deployments

## Purpose
This spec describes deployments of type INTERCEPTOR — an image-based deployment family member for DIAL interceptors. Interceptor deployments add no fields beyond the image-based contract; the `type: INTERCEPTOR` discriminator is the sole distinction.

Status: **Implemented**

## Key Terms
- **Interceptor deployment**: A deployment of type `INTERCEPTOR` that runs a DIAL interceptor container. Fully defined by the image-based contract.
- **Image-based contract**: The set of fields inherited from `ImageBasedDeploymentDto`: `imageDefinitionId` (required), `imageDefinitionName` (required), `imageDefinitionVersion` (required), plus all base deployment fields.

## Requirements

### Requirement: Interceptor deployment has no additional fields beyond the image-based contract
An interceptor deployment SHALL carry exactly the image-based contract fields (all base deployment fields plus `imageDefinitionId`, `imageDefinitionName`, `imageDefinitionVersion`) and nothing more. There are no interceptor-specific configuration fields.

Status: **Implemented**

#### Scenario: Create interceptor deployment
- **WHEN** `POST /api/v1/deployments` is called with `type: INTERCEPTOR` and a valid `imageDefinitionId`
- **THEN** an interceptor deployment is created with only the image-based contract fields

#### Scenario: Retrieve interceptor deployment
- **WHEN** `GET /api/v1/deployments/{id}` is called for an INTERCEPTOR deployment
- **THEN** the response body contains the image-based fields and no additional type-specific fields

### Requirement: Interceptor deployment must reference an interceptor image definition
An interceptor deployment SHALL reference an interceptor image definition via `imageDefinitionId`.

Status: **Implemented**

#### Scenario: Image definition linked
- **WHEN** an interceptor deployment is created with a valid `imageDefinitionId`
- **THEN** the deployment is linked to the specified interceptor image definition

#### Scenario: Missing imageDefinitionId rejected
- **WHEN** `POST /api/v1/deployments` is called with `type: INTERCEPTOR` without `imageDefinitionId`
- **THEN** the system responds with 400

### Requirement: Interceptor deployment requires KNative enabled
An interceptor deployment SHALL require `K8S_KNATIVE_ENABLED=true` to deploy. When KNative is disabled, the `KnativeDeploymentManager` bean is not instantiated (`@ConditionalOnProperty`) and no alternative deployment backend exists for image-based types.

Status: **Implemented**

#### Scenario: Deploy with KNative disabled
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called for an INTERCEPTOR deployment with `K8S_KNATIVE_ENABLED=false`
- **THEN** the deploy operation fails because no suitable deployment manager is available

## Implementation Notes
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.CreateInterceptorDeploymentRequestDto` (empty body — extends `CreateImageBasedDeploymentRequestDto`)
- Response DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.InterceptorDeploymentDto` (empty body — extends `ImageBasedDeploymentDto`)
- Kubernetes backend: KNative service (when `K8S_KNATIVE_ENABLED=true`)
- Related specs: `deployments` (base + lifecycle), `interceptor-image-definitions`, `kubernetes-manifests`
