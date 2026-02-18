# Kubernetes Manifests

## Purpose
This spec describes Kubernetes manifest generation — the strategy-based system that translates deployment configurations into Kubernetes resource definitions. The strategy is selected based on deployment type and enabled feature flags.

Status: **Implemented**

## Key Terms
- **Manifest strategy**: The implementation responsible for generating Kubernetes resources for a specific deployment type (KNative, NIM, KServe).
- **KNative Service**: A serverless Kubernetes workload managed by Knative, used for MCP, Interceptor, and Adapter deployments.
- **KServe InferenceService**: A KServe CRD for model serving, used for Inference deployments.
- **NIM resources**: NVIDIA-specific Kubernetes resources used for NIM deployments.
- **Feature flag**: Environment variable (`K8S_KNATIVE_ENABLED`, `K8S_NIM_ENABLED`, `K8S_KSERVE_ENABLED`) that controls which backends are available.

## Requirements

### Requirement: KNative manifest generated for image-based deployments
When KNative is enabled, the system SHALL generate a KNative Service manifest for MCP, Interceptor, and Adapter deployments. The manifest includes:

- **Container**: image name, container port, resource limits/requests, startup probe (when `probeProperties.enabled`)
- **Environment variables**: plain env vars set directly; sensitive env vars via Kubernetes Secret references (`SecretKeyRef`); file-based secrets via volume mounts (at configurable path, default: `/etc/secrets`)
- **Scaling annotations** on RevisionTemplate metadata: `autoscaling.knative.dev/initial-scale`, `autoscaling.knative.dev/min-scale`, `autoscaling.knative.dev/max-scale`
- **Volumes**: Secret volumes for `SECURE_FILE` mount type env vars
- **Naming**: Resources named via `K8sNamingUtils.generateMcpPrefixedName()`

Status: **Implemented**

#### Scenario: KNative Service creation
- **WHEN** a deploy operation is triggered for an MCP, Interceptor, or Adapter deployment with `K8S_KNATIVE_ENABLED=true`
- **THEN** a KNative Service resource is created with the container image, env vars, resource limits, and scaling parameters

#### Scenario: Scaling annotations applied
- **WHEN** a deployment specifies `minScale` and `maxScale`
- **THEN** the KNative Service annotations for autoscaling reflect those values

#### Scenario: Sensitive file env vars mounted as volumes
- **WHEN** a deployment has env vars with `mountType: SECURE_FILE`
- **THEN** the KNative manifest includes Secret volumes and volume mounts at the configured secret path

### Requirement: KServe InferenceService generated for inference deployments
When KServe is enabled, the system SHALL generate a KServe InferenceService manifest for Inference deployments. The manifest includes:

- **Predictor model spec**: `storageUri` (model location), `modelFormat` (framework), env vars (plain + sensitive via Secret), resource limits/requests, startup probe, custom `command`/`args`, container port
- **Scaling**: `minReplicas` and `maxReplicas` on predictor; `autoscaling.knative.dev/initial-scale` annotation on service metadata; scale metric (`CONCURRENCY` for `ACTIVE_REQUESTS` strategy); `autoscaling.knative.dev/scale-to-zero-pod-retention-period` for scale-to-zero delay
- **Model name argument**: `--model_name` automatically added to args if not already present
- **Naming**: Resources named via `K8sNamingUtils.generateName()`

Status: **Implemented**

#### Scenario: InferenceService creation
- **WHEN** a deploy operation is triggered for an Inference deployment with `K8S_KSERVE_ENABLED=true`
- **THEN** a KServe InferenceService resource is created with the model source and serving runtime configuration

#### Scenario: Scaling configuration applied to InferenceService
- **WHEN** an inference deployment has a `scaling` object with strategy and thresholds
- **THEN** the InferenceService manifest sets `minReplicas`, `maxReplicas`, scale metric, and scale target on the predictor

### Requirement: NIM-specific resources generated for NIM deployments
When NIM is enabled, the system SHALL generate NIM-specific Kubernetes resources for NIM deployments. The manifest includes:

- **Image**: repository and tag parsed from the NGC `imageRef`
- **Environment variables**: plain + sensitive via Secret references
- **Resources**: limits/requests; default 1 NVIDIA GPU (`nvidia.com/gpu`) added to limits
- **Service expose**: standard port (default: 8000), optional gRPC port, service type `ClusterIP`
- **Startup probe**: applied when `probeProperties.enabled`
- **Replicas**: default 1 (from configuration)
- **Storage**: PVC configuration (default: 20Gi)

Status: **Implemented**

#### Scenario: NIM resource creation
- **WHEN** a deploy operation is triggered for a NIM deployment with `K8S_NIM_ENABLED=true`
- **THEN** NIM-specific Kubernetes resources are created using the NGC model reference

#### Scenario: GPU resource added by default
- **WHEN** a NIM deployment is deployed without explicit GPU resource configuration
- **THEN** the NIM manifest includes a default `nvidia.com/gpu: 1` resource limit

### Requirement: Strategy selected per deployment type
The system SHALL automatically select the correct manifest generation strategy based on deployment type and enabled feature flags.

Status: **Implemented**

#### Scenario: Strategy routing
- **WHEN** a deploy operation is triggered
- **THEN** the strategy is selected: KNative for MCP/Interceptor/Adapter, KServe for Inference, NIM for NIM

### Requirement: Environment variables injected into manifests
The system SHALL inject plain and sensitive environment variables from the deployment configuration into generated Kubernetes manifests.

Status: **Implemented**

#### Scenario: Plain env vars in manifest
- **WHEN** a deployment has plain environment variables configured
- **THEN** those are set as container environment variables in the generated manifest

#### Scenario: Sensitive env vars via Kubernetes Secrets
- **WHEN** a deployment has sensitive environment variables configured
- **THEN** those are injected via Kubernetes Secrets (not as plain text in the manifest)

### Requirement: Resource limits and requests applied to manifests
The system SHALL apply CPU and memory resource limits/requests from the deployment `resources` configuration to generated manifests.

Status: **Implemented**

#### Scenario: Resource limits in manifest
- **WHEN** a deployment specifies CPU and memory `resources`
- **THEN** the generated manifest includes those values under `resources.limits` and `resources.requests`

## Implementation Notes
- KNative manifest generator: `com.epam.aidial.deployment.manager.service.manifest.KnativeManifestGenerator`
- KServe manifest generator: `com.epam.aidial.deployment.manager.service.manifest.InferenceManifestGenerator`
- NIM manifest generator: `com.epam.aidial.deployment.manager.service.manifest.NimManifestGenerator`
- Probe converters: `ProbeConverter`, `KserveProbeConverter`, `NimProbeConverter`
- Manifest field navigation: `KnativeMappers`, `InferenceMappers`, `NimMappers` (MappingChain pattern)
- KNative client: `com.epam.aidial.deployment.manager.kubernetes.knative.*`
- NIM: `com.epam.aidial.deployment.manager.kubernetes.nim.*`
- KServe: `com.epam.aidial.deployment.manager.kubernetes.kserve.*`
- CRD definitions: `src/main/resources/kubernetes/crd/`
- Feature flags: `K8S_KNATIVE_ENABLED`, `K8S_NIM_ENABLED`, `K8S_KSERVE_ENABLED`
- Related specs: `mcp-deployments`, `interceptor-deployments`, `adapter-deployments`, `inference-deployments`, `nim-deployments`
