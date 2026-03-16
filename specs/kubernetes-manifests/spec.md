# Kubernetes Manifests

## Purpose
This spec describes Kubernetes manifest generation — the strategy-based system that translates deployment configurations into Kubernetes resource definitions. The strategy is selected based on deployment type and enabled feature flags.

Status: **Implemented**

## Key Terms
- **Manifest strategy**: The implementation responsible for generating Kubernetes resources for a specific deployment type (KNative, NIM, KServe).
- **KNative Service**: A serverless Kubernetes workload managed by Knative, used for MCP, Interceptor, and Adapter deployments.
- **KServe InferenceService**: A KServe CRD for model serving, used for Inference deployments.
- **NIM resources**: NVIDIA-specific Kubernetes resources used for NIM deployments.
- **Feature flag**: Spring property (`app.knative.enabled`, `app.nim.enabled`, `app.kserve.enabled`) that controls which backends are available.
- **ProbeConverter**: Base converter that translates domain `ProbeProperties` to a Fabric8 `Probe` object. Used as a foundation by the CRD-specific probe converters.
- **KserveProbeConverter**: Converts domain `ProbeProperties` or a Fabric8 `Probe` into a KServe predictor model `StartupProbe` (targeting KServe's `io.kserve` CRD model). Handles `httpGet` and `tcpSocket` handlers and all timing fields.
- **NimProbeConverter**: Converts domain `ProbeProperties` or a Fabric8 `Probe` into a NIM `StartupProbe` (targeting NVIDIA's `com.nvidia.apps` CRD model). Sets `enabled: true` and populates the inner `probe` object with handler and timing fields.

## Requirements

### Requirement: KNative manifest generated for image-based deployments
When KNative is enabled, the system SHALL generate a KNative Service manifest for MCP, Interceptor, and Adapter deployments. The manifest includes:

- **Container**: image name, container port, resource limits/requests, startup probe (when `probeProperties.enabled`), custom `command`/`args` (when provided)
- **Environment variables**: plain env vars set directly; sensitive env vars via Kubernetes Secret references (`SecretKeyRef`); file-based secrets via volume mounts (at configurable path, default: `/etc/secrets`)
- **Scaling annotations** on RevisionTemplate metadata (from `Scaling` config): `autoscaling.knative.dev/initial-scale` (computed as `max(minReplicas, 1)`), `autoscaling.knative.dev/min-scale`, `autoscaling.knative.dev/max-scale`, `autoscaling.knative.dev/scale-to-zero-pod-retention-period` (when `scaleToZeroDelaySeconds` set), `autoscaling.knative.dev/target` (when `ACTIVE_REQUESTS` strategy). `containerConcurrency` is set on RevisionSpec when strategy is `ACTIVE_REQUESTS`.
- **Progress deadline annotation**: `serving.knative.dev/progress-deadline` set automatically on RevisionTemplate metadata when a startup probe is configured (see [Progress Deadline](#requirement-progress-deadline-annotation-computed-from-startup-probe))
- **Volumes**: Secret volumes for `SECURE_FILE` mount type env vars
- **Naming**: Resources named using the stored `serviceName` from the deployment record (generated via `K8sNamingUtils.generateName()` at first deploy; unified convention for all types)

Status: **Implemented**

#### Scenario: KNative Service creation
- **WHEN** a deploy operation is triggered for an MCP, Interceptor, or Adapter deployment with `app.knative.enabled=true`
- **THEN** a KNative Service resource is created with the container image, env vars, resource limits, and scaling parameters

#### Scenario: Scaling annotations applied from Scaling config
- **WHEN** a deployment has a `scaling` object with `minReplicas` and `maxReplicas`
- **THEN** the KNative Service annotations `min-scale`, `max-scale`, and `initial-scale` (computed as `max(minReplicas, 1)`) are set

#### Scenario: ACTIVE_REQUESTS scaling strategy applied
- **WHEN** a KNative deployment has `scaling.strategy.$type: ACTIVE_REQUESTS` with a `threshold`
- **THEN** `containerConcurrency` is set on the RevisionSpec and `autoscaling.knative.dev/target` annotation is set to the threshold

#### Scenario: Unsupported scaling strategy rejected
- **WHEN** a KNative deployment has a scaling strategy other than `ACTIVE_REQUESTS`
- **THEN** manifest generation fails with an `IllegalArgumentException`

#### Scenario: Scale-to-zero delay applied
- **WHEN** a KNative deployment has `scaling.scaleToZeroDelaySeconds` set
- **THEN** the `autoscaling.knative.dev/scale-to-zero-pod-retention-period` annotation is set (e.g., `"60s"`)

#### Scenario: Sensitive file env vars mounted as volumes
- **WHEN** a deployment has env vars with `mountType: SECURE_FILE`
- **THEN** the KNative manifest includes Secret volumes and volume mounts at the configured secret path

### Requirement: KServe InferenceService generated for inference deployments
When KServe is enabled, the system SHALL generate a KServe InferenceService manifest for Inference deployments. The manifest includes:

- **Predictor model spec**: `storageUri` (model location), `modelFormat` (framework), env vars (plain + sensitive via Secret), resource limits/requests, startup probe, custom `command`/`args`, container port
- **Scaling**: `minReplicas` and `maxReplicas` on predictor (from base `Scaling` config); `autoscaling.knative.dev/initial-scale` annotation on service metadata; scale metric (`CONCURRENCY` for `ACTIVE_REQUESTS` strategy); `autoscaling.knative.dev/scale-to-zero-pod-retention-period` for scale-to-zero delay
- **Progress deadline annotation**: `serving.knative.dev/progress-deadline` set on service metadata when a startup probe is configured (see [Progress Deadline](#requirement-progress-deadline-annotation-computed-from-startup-probe))
- **Model name argument**: `--model_name` automatically added to args if not already present
- **Naming**: Resources named using the stored `serviceName` from the deployment record
- **Startup probe**: converted from domain `ProbeProperties` to KServe `StartupProbe` by `KserveProbeConverter`

Status: **Implemented**

#### Scenario: InferenceService creation
- **WHEN** a deploy operation is triggered for an Inference deployment with `app.kserve.enabled=true`
- **THEN** a KServe InferenceService resource is created with the model source and serving runtime configuration

#### Scenario: Scaling configuration applied to InferenceService
- **WHEN** an inference deployment has a `scaling` object with strategy and thresholds
- **THEN** the InferenceService manifest sets `minReplicas`, `maxReplicas`, scale metric, and scale target on the predictor

#### Scenario: Startup probe applied to KServe predictor
- **WHEN** an inference deployment has `probeProperties.enabled = true`
- **THEN** `KserveProbeConverter` converts the probe to a KServe `StartupProbe` and sets it on the predictor model spec

### Requirement: NIM-specific resources generated for NIM deployments
When NIM is enabled, the system SHALL generate NIM-specific Kubernetes resources for NIM deployments. The manifest includes:

- **Image**: repository and tag parsed from the NGC `imageRef`
- **Container overrides**: custom `command`/`args` (when provided)
- **Environment variables**: plain + sensitive via Secret references
- **Resources**: limits/requests; default 1 NVIDIA GPU (`nvidia.com/gpu`) added to limits
- **Service expose**: standard port (default: 8000), optional gRPC port, service type `ClusterIP`
- **Startup probe**: converted from domain `ProbeProperties` to NIM `StartupProbe` by `NimProbeConverter` (sets `enabled: true` on the NIM probe wrapper)
- **Replicas**: default 1 (from configuration)
- **Storage**: PVC configuration (default: 20Gi)

Status: **Implemented**

#### Scenario: NIM resource creation
- **WHEN** a deploy operation is triggered for a NIM deployment with `app.nim.enabled=true`
- **THEN** NIM-specific Kubernetes resources are created using the NGC model reference

#### Scenario: GPU resource added by default
- **WHEN** a NIM deployment is deployed without explicit GPU resource configuration
- **THEN** the NIM manifest includes a default `nvidia.com/gpu: 1` resource limit

#### Scenario: Startup probe applied to NIM service
- **WHEN** a NIM deployment has `probeProperties.enabled = true`
- **THEN** `NimProbeConverter` converts the probe to a NIM `StartupProbe` (with `enabled: true`) and sets it on the NIM service spec

### Requirement: Progress deadline annotation computed from startup probe
When a deployment has a startup probe configured, the system SHALL automatically compute and set the `serving.knative.dev/progress-deadline` annotation on the generated manifest. This prevents KNative from terminating long-starting deployments (e.g., large model downloads) before they are ready.

The annotation is set on:
- **KNative Service**: RevisionTemplate metadata annotations
- **KServe InferenceService**: Service-level metadata annotations

The formula is: `progressDeadline = initialDelaySeconds + ((failureThreshold - 1) * periodSeconds) + timeoutSeconds + bufferSeconds`

When probe fields are not explicitly set, configurable defaults are used (matching Kubernetes defaults): `initialDelaySeconds=0`, `periodSeconds=10`, `failureThreshold=3`. A configurable buffer (default: 30s) is added to account for image pull time, readiness probe, and scheduling overhead.

When no startup probe is configured, no annotation is set and KNative's built-in default of 600s applies.

Status: **Implemented**

#### Scenario: Progress deadline set from startup probe
- **WHEN** a deployment has `probeProperties.enabled = true` with startup probe timing fields
- **THEN** the `serving.knative.dev/progress-deadline` annotation is computed and set on the manifest

#### Scenario: Default probe values used for missing fields
- **WHEN** a startup probe does not specify `initialDelaySeconds`, `periodSeconds`, or `failureThreshold`
- **THEN** the configurable default values are used in the formula

#### Scenario: No annotation without startup probe
- **WHEN** a deployment does not have a startup probe configured
- **THEN** no `serving.knative.dev/progress-deadline` annotation is set

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
- `ManifestGenerator` interface: `com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator`
- KNative manifest generator: `com.epam.aidial.deployment.manager.service.manifest.KnativeManifestGenerator`
- KServe manifest generator: `com.epam.aidial.deployment.manager.service.manifest.InferenceManifestGenerator`
- NIM manifest generator: `com.epam.aidial.deployment.manager.service.manifest.NimManifestGenerator`
- Base probe converter: `com.epam.aidial.deployment.manager.service.manifest.ProbeConverter` — translates `ProbeProperties` to Fabric8 `io.fabric8.kubernetes.api.model.Probe`
- KServe probe converter: `com.epam.aidial.deployment.manager.service.manifest.KserveProbeConverter` — converts Fabric8 `Probe` or `ProbeProperties` to `io.kserve.serving.v1beta1...StartupProbe`
- NIM probe converter: `com.epam.aidial.deployment.manager.service.manifest.NimProbeConverter` — converts Fabric8 `Probe` or `ProbeProperties` to `com.nvidia.apps.v1alpha1.nimservicespec.StartupProbe` (with `enabled: true`)
- Progress deadline calculator: `com.epam.aidial.deployment.manager.service.manifest.ProgressDeadlineCalculator` — computes KNative progress deadline from startup probe config with configurable defaults and buffer
- Manifest field navigation: `KnativeMappers`, `InferenceMappers`, `NimMappers` (MappingChain pattern)
- KNative client: `com.epam.aidial.deployment.manager.kubernetes.knative.*`
- NIM client: `com.epam.aidial.deployment.manager.kubernetes.nim.*`
- KServe client: `com.epam.aidial.deployment.manager.kubernetes.kserve.*`
- CRD definitions: `src/main/resources/kubernetes/crd/`
- Fabric8 CRD code generator: runs at build time via `io.fabric8.java-generator` Gradle plugin
- Feature flags: `app.knative.enabled`, `app.nim.enabled`, `app.kserve.enabled`
- Related specs: `mcp-deployments`, `interceptor-deployments`, `adapter-deployments`, `inference-deployments`, `nim-deployments`
