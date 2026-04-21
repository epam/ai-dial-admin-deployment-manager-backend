# NIM Deployments

## Purpose
This spec describes deployments of type NIM — a model-source deployment for NVIDIA Inference Microservices. Like inference deployments, NIM deployments reference a model source (NGC registry) directly rather than an `ImageDefinition`.

Status: **Implemented**

## Key Terms
- **NIM deployment**: A deployment of type `NIM` that runs NVIDIA Inference Microservices for GPU-accelerated model serving.
- **NGC registry**: NVIDIA GPU Cloud model registry — the source for NIM model references. Uses the unified `Source` sealed interface; the only supported variant for NIM is `NgcRegistrySource` (`$type: "ngc_registry"`). The source is stored as a JSON column on the base `deployment` table.
- **NIM**: NVIDIA Inference Microservices — pre-built, optimized containers for serving NVIDIA models.
- **`containerGrpcPort`**: Optional gRPC port for NIM services (1–65535). Used when the NIM container exposes a gRPC inference endpoint alongside the HTTP endpoint.

## Requirements

### Requirement: NIM deployment uses NGC registry as model source
A NIM deployment SHALL reference an NVIDIA NGC registry source to identify the model, instead of an `imageDefinitionId`.

Status: **Implemented**

#### Scenario: Create NIM deployment with NGC source
- **WHEN** `POST /api/v1/deployments` is called with `type: NIM` and a `NimDeploymentSourceDto` with `$type: "ngc_registry"`
- **THEN** a NIM deployment is persisted with the NGC model reference and base deployment fields

#### Scenario: Retrieve NIM deployment
- **WHEN** `GET /api/v1/deployments/{id}` is called for a NIM deployment
- **THEN** the response includes the NGC registry source and NIM-specific configuration (no `imageDefinitionId`)

### Requirement: NGC source image reference must be a valid Docker image name
The NGC registry source SHALL carry an `imageRef` field that passes `@ValidDockerImageName` validation.

Status: **Implemented**

#### Scenario: Valid NGC image reference
- **WHEN** a NIM deployment is created with a valid `source.imageRef` value
- **THEN** the image reference is persisted and used for Kubernetes manifest generation

#### Scenario: Invalid image reference rejected
- **WHEN** a NIM deployment is created with an `imageRef` that fails `@ValidDockerImageName` validation
- **THEN** the system responds with 400

### Requirement: NIM deployment supports an optional gRPC port
A NIM deployment SHALL optionally carry a `containerGrpcPort` (nullable, 1–65535) specifying the gRPC port exposed by the NIM container.

Status: **Implemented**

#### Scenario: gRPC port stored and applied
- **WHEN** a NIM deployment is created or updated with a valid `containerGrpcPort`
- **THEN** the gRPC port is persisted and included in Kubernetes manifest generation

#### Scenario: No gRPC port
- **WHEN** a NIM deployment is created without `containerGrpcPort`
- **THEN** `containerGrpcPort` is null and the deployment is created successfully

#### Scenario: Invalid gRPC port rejected
- **WHEN** `containerGrpcPort` is set to a value outside 1–65535
- **THEN** the system responds with 400

### Requirement: NIM deployment supports an optional PVC storage size override
A NIM deployment SHALL optionally carry a `storageSize` (nullable String, Kubernetes quantity format e.g. `"20Gi"`, `"500Mi"`, or plain bytes e.g. `"21474836480"`) specifying the PVC size for model data storage. When not set, the default from the application template (20Gi) is used.

Status: **Implemented**

#### Scenario: Storage size stored and applied
- **WHEN** a NIM deployment is created or updated with a valid `storageSize` (e.g., `"50Gi"`)
- **THEN** the storage size is persisted and used to override the default PVC size in the Kubernetes manifest

#### Scenario: No storage size (uses default)
- **WHEN** a NIM deployment is created without `storageSize`
- **THEN** the default PVC size from the template (20Gi) is used

#### Scenario: Invalid storage size rejected
- **WHEN** `storageSize` is set to a value that does not match Kubernetes binary quantity format
- **THEN** the system responds with 400

### Requirement: NIM deployment uses base scale fields only
A NIM deployment SHALL use the base deployment scale fields (`initialScale`, `minScale`, `maxScale`) inherited from `DeploymentDto`. NIM deployments do NOT support the `ScalingDto` object used by inference deployments — there is no `scaling` field on `NimDeploymentDto`. Replica count defaults to 1 (from NIM configuration).

Status: **Implemented**

#### Scenario: NIM deployment inherits base scale fields
- **WHEN** a NIM deployment is created with `initialScale`, `minScale`, or `maxScale` values
- **THEN** those values are persisted and available in the deployment response

### Requirement: NIM deployment uses NIM-specific Kubernetes resources
When NIM is enabled (`app.nim.enabled=true`), the system SHALL generate NIM-specific Kubernetes manifests for NIM deployments during deploy.

Status: **Implemented**

#### Scenario: NIM manifest created on deploy
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called for a NIM deployment with `app.nim.enabled=true`
- **THEN** NIM-specific Kubernetes resources are created using the NGC model reference

#### Scenario: Deploy with NIM disabled
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called for a NIM deployment with `app.nim.enabled=false`
- **THEN** the deploy operation fails or is skipped with an appropriate error

## Implementation Notes
- Domain model: `com.epam.aidial.deployment.manager.model.deployment.NimDeployment`
- Source domain model: `com.epam.aidial.deployment.manager.model.deployment.Source` (unified sealed interface; NIM uses `NgcRegistrySource` variant)
- NGC registry source domain model: `com.epam.aidial.deployment.manager.model.deployment.NgcRegistrySource`
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.CreateNimDeploymentRequestDto`
  - Fields: `source` (required `NimDeploymentSourceDto`), `containerGrpcPort` (nullable Integer, `@Min(1) @Max(65535)`), `storageSize` (nullable String, `@ValidStorageSize` — Kubernetes quantity via Fabric8 parser)
- Response DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.NimDeploymentDto`
  - Fields: `source` (required `NimDeploymentSourceDto`), `containerGrpcPort` (nullable Integer), `storageSize` (nullable String)
- Source DTO (interface): `com.epam.aidial.deployment.manager.web.dto.deployment.NimDeploymentSourceDto` (`$type` discriminator)
- NGC source DTO (record): `com.epam.aidial.deployment.manager.web.dto.deployment.NimDeploymentNgcRegistrySourceDto` (`imageRef` with `@NotNull @ValidDockerImageName`)
- Persistence source: stored as `PersistenceNgcRegistrySource` within the unified `PersistenceSource` JSON column on base `DeploymentEntity`
- Deployment manager: `com.epam.aidial.deployment.manager.service.deployment.NimDeploymentManager` (active when `app.nim.enabled=true`)
- Manifest generator: `com.epam.aidial.deployment.manager.service.manifest.NimManifestGenerator`
- Kubernetes backend: NIM-specific `NIMService` resources via `K8sNimClient` (conditioned on `app.nim.enabled=true`)
- K8s package: `com.epam.aidial.deployment.manager.kubernetes.nim.*`
- Related specs: `deployments` (base + lifecycle), `kubernetes-manifests`
