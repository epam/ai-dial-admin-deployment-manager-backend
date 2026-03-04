# Interceptor Deployments

## Purpose
This spec describes deployments of type INTERCEPTOR — an image-based deployment family member for DIAL interceptors. Interceptor deployments add no fields beyond the image-based contract; the `type: INTERCEPTOR` discriminator is the sole distinction.

Status: **Implemented**

## Key Terms
- **Interceptor deployment**: A deployment of type `INTERCEPTOR` that runs a DIAL interceptor container. Fully defined by the image-based contract.
- **Image-based contract**: The set of fields inherited from `ImageBasedDeploymentDto`: `imageDefinitionId` (nullable), `imageDefinitionName` (nullable), `imageDefinitionVersion` (nullable), `imageDefinitionType` (`ImageTypeDto` — ADAPTER/MCP/INTERCEPTOR; `@NotNull` in response), plus all base deployment fields. The image definition is referenced either by `imageDefinitionId` alone, or by the (`imageDefinitionType`, `imageDefinitionName`, `imageDefinitionVersion`) triple.

## Requirements

### Requirement: Interceptor deployment has no additional fields beyond the image-based contract
An interceptor deployment SHALL carry exactly the image-based contract fields (all base deployment fields plus `imageDefinitionId`, `imageDefinitionName`, `imageDefinitionVersion`, `imageDefinitionType`) and nothing more. There are no interceptor-specific configuration fields.

Status: **Implemented**

#### Scenario: Create interceptor deployment
- **WHEN** `POST /api/v1/deployments` is called with `type: INTERCEPTOR` and a valid `imageDefinitionId`
- **THEN** an interceptor deployment is created with only the image-based contract fields

#### Scenario: Retrieve interceptor deployment
- **WHEN** `GET /api/v1/deployments/{id}` is called for an INTERCEPTOR deployment
- **THEN** the response body contains the image-based fields and no additional type-specific fields

### Requirement: Interceptor deployment must reference an interceptor image definition
An interceptor deployment SHALL reference an image definition via one of two mutually exclusive paths:
1. **By ID**: supply `imageDefinitionId` (UUID).
2. **By type + name + version**: supply `imageDefinitionType` (`INTERCEPTOR`), `imageDefinitionName`, and `imageDefinitionVersion` — all three are required together. The response always includes `imageDefinitionType` (`@NotNull`).

Status: **Implemented**

#### Scenario: Image definition linked by ID
- **WHEN** an interceptor deployment is created with a valid `imageDefinitionId`
- **THEN** the deployment is linked to the specified interceptor image definition and `imageDefinitionType` is populated in the response

#### Scenario: Image definition linked by type + name + version
- **WHEN** an interceptor deployment is created with `imageDefinitionType: INTERCEPTOR`, `imageDefinitionName`, and `imageDefinitionVersion` (no `imageDefinitionId`)
- **THEN** the image definition is resolved by type + name + version and the deployment is created successfully

#### Scenario: Incomplete image reference rejected
- **WHEN** `POST /api/v1/deployments` is called with `type: INTERCEPTOR` without a complete image reference (no `imageDefinitionId` and missing one or more of `imageDefinitionType`/`imageDefinitionName`/`imageDefinitionVersion`)
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
