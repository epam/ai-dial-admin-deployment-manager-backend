# Inference Deployments

## Purpose
This spec describes deployments of type INFERENCE — a model-source deployment for serving AI inference models via KServe. Unlike image-based deployments, inference deployments do not reference an `ImageDefinition`; they reference a model source directly and support scaling configuration.

Status: **Implemented**

## Key Terms
- **Inference deployment**: A deployment of type `INFERENCE` that serves an AI model using KServe as the backend runtime.
- **Model source**: The origin of the model to serve. Currently the only supported subtype is `InferenceDeploymentHuggingFaceSourceDto` (`$type: "huggingface"`).
- **KServe**: Kubernetes-native model serving framework used as the inference deployment backend.
- **Scaling strategy**: An auto-scaling configuration that specifies the scaling trigger type and threshold.

## ADDED Requirements

### Requirement: Inference deployment uses model source instead of image definition
An inference deployment SHALL reference a model source (not an `imageDefinitionId`) to identify the model to serve. The `imageDefinitionId` field SHALL NOT be present.

Status: **Implemented**

#### Scenario: Create inference deployment with model source
- **WHEN** `POST /api/v1/deployments` is called with `type: INFERENCE` and a model source
- **THEN** an inference deployment is persisted with the model source and base deployment fields

#### Scenario: Retrieve inference deployment
- **WHEN** `GET /api/v1/deployments/{id}` is called for an INFERENCE deployment
- **THEN** the response includes the model source and inference-specific configuration (no `imageDefinitionId`)

### Requirement: Inference deployment supports HuggingFace as model source
An inference deployment SHALL accept a HuggingFace model hub reference as its source. The model name must pass the `@ValidHuggingFaceModelName` format constraint.

Status: **Implemented**

#### Scenario: HuggingFace source
- **WHEN** an inference deployment is created with `source.$type: "huggingface"` and a valid `modelName`
- **THEN** the HuggingFace model reference is persisted and used for KServe manifest generation

#### Scenario: Invalid model name
- **WHEN** an inference deployment is created with a `modelName` that fails `@ValidHuggingFaceModelName` validation
- **THEN** the system responds with 400

### Requirement: Inference deployment supports model format
An inference deployment SHALL carry a required `modelFormat` field that identifies the model serving framework (e.g., `"pytorch"`, `"tensorflow"`).

Status: **Implemented**

#### Scenario: Model format required
- **WHEN** `POST /api/v1/deployments` is called with `type: INFERENCE` without `modelFormat`
- **THEN** the system responds with 400

#### Scenario: Model format stored and returned
- **WHEN** an inference deployment is created with a valid `modelFormat`
- **THEN** `modelFormat` is persisted and included in all deployment responses

### Requirement: Inference deployment supports custom command and arguments
An inference deployment SHALL optionally carry `command` and `args` fields to override the container entrypoint and arguments.

Status: **Implemented**

#### Scenario: Command and args stored
- **WHEN** an inference deployment is created with `command` or `args` values
- **THEN** they are persisted and passed to KServe for manifest generation

### Requirement: Inference deployment supports auto-scaling configuration
An inference deployment SHALL optionally carry a `scaling` object (`ScalingDto`) configuring KServe-specific horizontal scaling behavior. This is separate from the base deployment scale fields (`initialScale`, `minScale`, `maxScale`) which control KNative annotations. The `ScalingDto` maps to KServe predictor-level scaling: `minReplicas`, `maxReplicas`, scale metric, and scale-to-zero delay. Validation: `maxReplicas` must be ≥ 1, `minReplicas` must be ≥ 0, and `strategy` is required when `scaling` is provided.

Status: **Implemented**

#### Scenario: Scaling configuration stored and applied
- **WHEN** an inference deployment is created or updated with a `scaling` object
- **THEN** the scaling parameters are persisted and used for KServe autoscaler configuration

#### Scenario: Scale-to-zero delay
- **WHEN** `scaling.scaleToZeroDelaySeconds` is set (must be ≥1 if provided)
- **THEN** the deployment scales down to zero replicas after the specified idle period

#### Scenario: Scaling strategy set
- **WHEN** `scaling.strategy` is provided with `$type: PENDING_REQUESTS | ACTIVE_REQUESTS | HARDWARE_USAGE` and `threshold ≥1`
- **THEN** the autoscaler uses the specified metric and threshold to trigger scale-out

#### Scenario: Invalid scaling parameters rejected
- **WHEN** `scaling.maxReplicas < 1` or `scaling.minReplicas < 0` or `scaling.strategy.threshold < 1`
- **THEN** the system responds with 400

### Requirement: Inference deployment is backed by KServe
When KServe is enabled (`K8S_KSERVE_ENABLED=true`), the system SHALL generate a KServe `InferenceService` manifest for inference deployments during deploy.

Status: **Implemented**

#### Scenario: KServe manifest created on deploy
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called for an INFERENCE deployment with `K8S_KSERVE_ENABLED=true`
- **THEN** a KServe `InferenceService` resource is created in the Kubernetes namespace

#### Scenario: Deploy with KServe disabled
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called for an INFERENCE deployment with `K8S_KSERVE_ENABLED=false`
- **THEN** the deploy operation fails or is skipped with an appropriate error

## Implementation Notes
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.CreateInferenceDeploymentRequestDto`
  - Fields: `modelFormat` (required String), `source` (required InferenceDeploymentSourceDto), `command` (nullable List<String>), `args` (nullable List<String>), `scaling` (nullable ScalingDto)
- Response DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.InferenceDeploymentDto`
- Source DTO: `com.epam.aidial.deployment.manager.web.dto.InferenceDeploymentSourceDto` ($type: "huggingface")
- HuggingFace source: `com.epam.aidial.deployment.manager.web.dto.InferenceDeploymentHuggingFaceSourceDto` (modelName @ValidHuggingFaceModelName)
- Scaling DTO: `com.epam.aidial.deployment.manager.web.dto.ScalingDto`
  - `minReplicas` (≥0), `maxReplicas` (≥1), `scaleToZeroDelaySeconds` (nullable, ≥1), `strategy` (required ScalingStrategyDto)
- Scaling strategy: `ScalingStrategyDto` — `$type` (ScalingStrategyTypeDto: PENDING_REQUESTS | ACTIVE_REQUESTS | HARDWARE_USAGE), `threshold` (≥1)
- Kubernetes backend: KServe InferenceService (`K8S_KSERVE_ENABLED=true`)
- K8s package: `com.epam.aidial.deployment.manager.kubernetes.kserve.*`
- Related specs: `deployments` (base + lifecycle), `kubernetes-manifests`, `huggingface`
