# Adapter Deployments

## Purpose
This spec describes deployments of type ADAPTER — an image-based deployment family member for DIAL adapters. Adapter deployments add no fields beyond the image-based contract; the `type: ADAPTER` discriminator is the sole distinction.

Status: **Implemented**

## Key Terms
- **Adapter deployment**: A deployment of type `ADAPTER` that runs a DIAL adapter container. Fully defined by the image-based contract.
- **Image-based contract**: The set of fields inherited from `ImageBasedDeploymentDto`: `imageDefinitionId` (nullable), `imageDefinitionName` (nullable), `imageDefinitionVersion` (nullable), `imageDefinitionType` (`ImageTypeDto` — ADAPTER/MCP/INTERCEPTOR; `@NotNull` in response), plus all base deployment fields. The image definition is referenced either by `imageDefinitionId` alone, or by the (`imageDefinitionType`, `imageDefinitionName`, `imageDefinitionVersion`) triple.

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
An adapter deployment SHALL reference an image definition via one of two mutually exclusive paths:
1. **By ID**: supply `imageDefinitionId` (UUID).
2. **By type + name + version**: supply `imageDefinitionType` (`ADAPTER`), `imageDefinitionName`, and `imageDefinitionVersion` — all three are required together. The response always includes `imageDefinitionType` (`@NotNull`).

Status: **Implemented**

#### Scenario: Image definition linked by ID
- **WHEN** an adapter deployment is created with a valid `imageDefinitionId`
- **THEN** the deployment is linked to the specified adapter image definition and `imageDefinitionType` is populated in the response

#### Scenario: Image definition linked by type + name + version
- **WHEN** an adapter deployment is created with `imageDefinitionType: ADAPTER`, `imageDefinitionName`, and `imageDefinitionVersion` (no `imageDefinitionId`)
- **THEN** the image definition is resolved by type + name + version and the deployment is created successfully

#### Scenario: Incomplete image reference rejected
- **WHEN** `POST /api/v1/deployments` is called with `type: ADAPTER` without a complete image reference (no `imageDefinitionId` and missing one or more of `imageDefinitionType`/`imageDefinitionName`/`imageDefinitionVersion`)
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
