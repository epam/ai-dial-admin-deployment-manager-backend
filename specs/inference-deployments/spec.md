# Inference Deployments

## Purpose
This spec describes deployments of type INFERENCE — a model-source deployment for serving AI inference models via KServe. Unlike image-based deployments, inference deployments do not reference an `ImageDefinition`; they reference a model source directly and support scaling configuration.

Status: **Implemented**

## Key Terms
- **Inference deployment**: A deployment of type `INFERENCE` that serves an AI model using KServe as the backend runtime.
- **Model source**: The origin of the model to serve. Uses the unified `Source` sealed interface; the only supported variant for inference is `HuggingFaceSource` (`$type: "huggingface"`). The source is stored as a JSON column on the base `deployment` table.
- **KServe**: Kubernetes-native model serving framework used as the inference deployment backend.
- **Scaling strategy**: An auto-scaling configuration that specifies the scaling trigger type and threshold.

## Requirements

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
An inference deployment uses the base-level `command` and `args` fields (see `deployments` spec) to override the container entrypoint and arguments. These fields were consolidated from the Inference-specific level to the base Deployment level to enable all deployment types to use them.

Status: **Implemented** (consolidated to base level)

#### Scenario: Command and args stored
- **WHEN** an inference deployment is created with `command` or `args` values
- **THEN** they are persisted and passed to KServe for manifest generation

### Requirement: Inference deployment uses base scaling configuration for KServe
An inference deployment uses the base `scaling` field (see `deployments` spec) to configure KServe-specific horizontal scaling behavior. The `ScalingDto` maps to KServe predictor-level scaling: `minReplicas`, `maxReplicas`, scale metric, and scale-to-zero delay. KServe supports all scaling strategy types (`PENDING_REQUESTS`, `ACTIVE_REQUESTS`, `HARDWARE_USAGE`).

Status: **Implemented**

#### Scenario: Scaling configuration applied to KServe
- **WHEN** an inference deployment is created or updated with a `scaling` object
- **THEN** the scaling parameters are used for KServe autoscaler configuration (predictor replicas, scale metric, scale-to-zero)

### Requirement: Inference deployment is backed by KServe
When KServe is enabled (`app.kserve.enabled=true`), the system SHALL generate a KServe `InferenceService` manifest for inference deployments during deploy.

Status: **Implemented**

#### Scenario: KServe manifest created on deploy
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called for an INFERENCE deployment with `app.kserve.enabled=true`
- **THEN** a KServe `InferenceService` resource is created in the Kubernetes namespace

#### Scenario: Deploy with KServe disabled
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called for an INFERENCE deployment with `app.kserve.enabled=false`
- **THEN** the deploy operation fails or is skipped with an appropriate error

### Requirement: Auto-detect text-classification task at deploy time
On every deploy of a HuggingFace-sourced inference deployment, the system SHALL fetch the model's HF Hub metadata and compute a transient `task` (one of `TEXT_CLASSIFICATION` / `NONE`) plus an optional `id2Label` map. The detection result is not persisted; it flows directly into manifest generation. There is no operator-facing `task` or `id2label` field on the API.

Status: **Implemented** *(Implemented via 021-inference-task-transformer)*

#### Scenario: Text-classification model detected
- **WHEN** a deploy is requested for a model whose `pipeline_tag` is `text-classification` OR whose `architectures` list contains an entry matching `.*ForSequenceClassification`
- **THEN** detection returns `TEXT_CLASSIFICATION` and the model's `config.json` `id2label` is parsed into the `id2Label` map

#### Scenario: Non-classification model detected
- **WHEN** a deploy is requested for a model whose HF metadata satisfies neither signal
- **THEN** detection returns `NONE` and the manifest is generated predictor-only (unchanged from pre-feature behavior)

### Requirement: Reject unusable model metadata at deploy time
When detection classifies a model as `TEXT_CLASSIFICATION` but its `config.json` `id2label` fails the structural contract (missing, non-integer keys, sparse keys, empty values, or all values matching `^LABEL_\d+$`), the deploy SHALL be rejected with HTTP 400 and an actionable message naming the model and the specific failed condition. Model-not-found / unauthorized HF responses are also surfaced as HTTP 400; transient HF Hub failures surface as HTTP 502 with a retryable message. No cluster mutation occurs.

Status: **Implemented** *(Implemented via 021-inference-task-transformer)*

#### Scenario: Missing id2label
- **WHEN** a deploy is requested for a sequence-classification model whose `config.json` lacks an `id2label`
- **THEN** the deploy fails with HTTP 400 and a message naming the model

#### Scenario: Stub labels
- **WHEN** every value in the model's `id2label` matches HuggingFace's auto-stub pattern `^LABEL_\d+$`
- **THEN** the deploy fails with HTTP 400

#### Scenario: HF Hub unreachable
- **WHEN** the HF Hub responds with 5xx / DNS / timeout / network error during deploy
- **THEN** the deploy fails with HTTP 502 and a retryable error message

#### Scenario: Operator-supplied predictor arg conflicts with chained contract
- **WHEN** a `TEXT_CLASSIFICATION`-detected deployment carries `--return_probabilities` or `--task=<non-sequence_classification>` in its predictor args
- **THEN** the deploy fails with HTTP 400 before any cluster mutation

## Implementation Notes
- Domain model: `com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment`
  - Fields: `modelFormat` (String). Note: `source`, `command`, and `args` are inherited from the base `Deployment` model.
- Source domain model: `com.epam.aidial.deployment.manager.model.deployment.Source` (unified sealed interface; inference uses `HuggingFaceSource` variant)
- HuggingFace source domain model: `com.epam.aidial.deployment.manager.model.deployment.HuggingFaceSource`
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.CreateInferenceDeploymentRequestDto`
  - Fields: `modelFormat` (required String), `source` (required `InferenceDeploymentSourceDto`). Note: `command` and `args` are inherited from the base request DTO.
- Response DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.InferenceDeploymentDto`
  - Fields: `modelFormat` (required String), `source` (required `InferenceDeploymentSourceDto`). Note: `command` and `args` are inherited from the base response DTO.
- Source DTO (interface): `com.epam.aidial.deployment.manager.web.dto.deployment.InferenceDeploymentSourceDto` (`$type` discriminator)
- HuggingFace source DTO (record): `com.epam.aidial.deployment.manager.web.dto.deployment.InferenceDeploymentHuggingFaceSourceDto` (`modelName` with `@NotNull @ValidHuggingFaceModelName`)
- Persistence source: stored as `PersistenceHuggingFaceSource` within the unified `PersistenceSource` JSON column on base `DeploymentEntity`
- Probe converter: `com.epam.aidial.deployment.manager.service.manifest.KserveProbeConverter`
- Deployment manager: `com.epam.aidial.deployment.manager.service.deployment.InferenceDeploymentManager` (active when `app.kserve.enabled=true`)
- Manifest generator: `com.epam.aidial.deployment.manager.service.manifest.InferenceManifestGenerator`
- Detector: `com.epam.aidial.deployment.manager.service.detection.InferenceTaskDetector` (runs at deploy time; not persisted)
  - Exception hierarchy: `InferenceTaskDetectionException` (base), `ModelMetadataMissingException` / `ModelMetadataUnusableException` / `ModelNotFoundException` → HTTP 400; `HuggingFaceUpstreamException` → HTTP 502
- Missing-image gate: `com.epam.aidial.deployment.manager.service.deployment.MissingTransformerImageException` → HTTP 500
- Transformer manifest section: `com.epam.aidial.deployment.manager.service.manifest.TextClassificationTransformerSection` (emits the `transformer` block when detection returns `TEXT_CLASSIFICATION`)
- Chained-mode `CiliumNetworkPolicy` augmentation: the per-deployment policy carries an intra-cluster egress block (same-`InferenceService` + `istio-system` + `knative-serving`), a same-`InferenceService` entry in the ingress `fromEndpoints`, and `8080/TCP` on ingress `toPorts` — emitted for any chained deployment; baseline shape preserved for predictor-only. See `specs/kubernetes-manifests/spec.md` § "Chained predictor + transformer". *(Implemented via 022-transformer-cilium-policies)*
- Chained-mode signal threading: `com.epam.aidial.deployment.manager.service.deployment.DeployContext` carries the chained boolean from `InferenceDeploymentManager.prepareServiceSpec` to `AbstractDeploymentManager.deploy` / `updateCiliumNetworkPolicy` without persistence or partial-manifest re-parsing.
- Kubernetes backend: KServe `InferenceService` resources via `K8sKserveClient` (conditioned on `app.kserve.enabled=true`)
- K8s package: `com.epam.aidial.deployment.manager.kubernetes.kserve.*`
- Related specs: `deployments` (base + lifecycle), `kubernetes-manifests`, `huggingface`
