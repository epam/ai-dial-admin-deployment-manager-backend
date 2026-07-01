# Kubernetes Manifests

## Purpose
This spec describes Kubernetes manifest generation — the strategy-based system that translates deployment configurations into Kubernetes resource definitions. The strategy is selected based on deployment type and enabled feature flags.

Status: **Implemented**

## Key Terms
- **Manifest strategy**: The implementation responsible for generating Kubernetes resources for a specific deployment type (KNative, NIM, KServe).
- **KNative Service**: A serverless Kubernetes workload managed by Knative, used for MCP, Interceptor, Adapter, and Application deployments.
- **KServe InferenceService**: A KServe CRD for model serving, used for Inference deployments.
- **NIM resources**: NVIDIA-specific Kubernetes resources used for NIM deployments.
- **Feature flag**: Spring property (`app.knative.enabled`, `app.nim.enabled`, `app.kserve.enabled`) that controls which backends are available.
- **ProbeConverter**: Base converter that translates domain `ProbeProperties` to a Fabric8 `Probe` object. Used as a foundation by the CRD-specific probe converters.
- **KserveProbeConverter**: Converts domain `ProbeProperties` or a Fabric8 `Probe` into a KServe predictor model `StartupProbe` (targeting KServe's `io.kserve` CRD model). Handles `httpGet` and `tcpSocket` handlers and all timing fields.
- **NimProbeConverter**: Converts domain `ProbeProperties` or a Fabric8 `Probe` into a NIM `StartupProbe` (targeting NVIDIA's `com.nvidia.apps` CRD model). Sets `enabled: true` and populates the inner `probe` object with handler and timing fields.

## Requirements

### Requirement: Auto-provisioned imagePullSecrets for trusted-registry images
When `app.registry.auto-pull-secret-enabled` is `true` (default), the deploy flow SHALL auto-provision a `kubernetes.io/dockerconfigjson` pull secret and reference it from the generated workload whenever an in-scope image is served from a credentialed configured registry (the primary registry or a trusted-private registry with `BASIC` auth). The credential match reuses `DockerHubAliases.sameRegistry(...)` (Docker Hub alias-aware). In-scope images are the deployment image for KNative image-based deployments and the chained transformer container image for Inference deployments; the predictor and NIM workloads are out of scope (NIM keeps its existing `spec.image.pullSecrets`). The reference is injected onto the already-built CRD by the deployment manager — `RevisionSpec.imagePullSecrets` for KNative, `spec.transformer.imagePullSecrets` for KServe — so manifest-generator signatures are unchanged. When no in-scope image matches a credentialed registry, or the feature is disabled, no secret is created and no `imagePullSecrets` is injected (manifest unchanged).

Status: **Implemented** — Implemented via 025-auto-pull-secrets

#### Scenario: KNative image-based deployment from a trusted registry
- **WHEN** an MCP/Interceptor/Adapter/Application deployment whose image host matches a credentialed configured registry is deployed
- **THEN** a `dockerconfigjson` pull secret is created in the deployment namespace and `spec.template.spec.imagePullSecrets` references it

#### Scenario: Chained transformer image from a trusted registry
- **WHEN** an Inference deployment chains a text-classification transformer whose configured image host matches a credentialed configured registry
- **THEN** `spec.transformer.imagePullSecrets` references the provisioned secret and the predictor is left untouched

#### Scenario: Public/unmatched image leaves the manifest unchanged
- **WHEN** a deployment image's host matches no credentialed configured registry, or `AUTO_PULL_SECRET_ENABLED=false`
- **THEN** no pull secret is created and the generated manifest carries no auto-injected `imagePullSecrets`

#### Scenario: Prior pull secret superseded on update
- **WHEN** a deployment served from a credentialed registry is updated/rolling-updated and a new pull secret is provisioned
- **THEN** the previously provisioned pull secret is marked for cleanup (not orphaned), mirroring sensitive-env-secret supersession, while co-located env secrets are left untouched

### Requirement: KNative manifest generated for image-based deployments
When KNative is enabled, the system SHALL generate a KNative Service manifest for MCP, Interceptor, Adapter, and Application deployments. The manifest includes:

- **Container**: image name, container port, resource limits/requests, startup probe (when `probeProperties.enabled`), custom `command`/`args` (when provided)
- **Environment variables**: plain env vars set directly; sensitive env vars via Kubernetes Secret references (`SecretKeyRef`); file-based secrets via volume mounts (at configurable path, default: `/etc/secrets`)
- **Scaling annotations** on RevisionTemplate metadata (from `Scaling` config): `autoscaling.knative.dev/initial-scale` (computed as `max(minReplicas, 1)`), `autoscaling.knative.dev/min-scale`, `autoscaling.knative.dev/max-scale`, `autoscaling.knative.dev/scale-to-zero-pod-retention-period` (when `scaleToZeroDelaySeconds` set), `autoscaling.knative.dev/target` (when `ACTIVE_REQUESTS` strategy). `containerConcurrency` is set on RevisionSpec when strategy is `ACTIVE_REQUESTS`.
- **Progress deadline annotation**: `serving.knative.dev/progress-deadline` set automatically on RevisionTemplate metadata when a startup probe is configured (see [Progress Deadline](#requirement-progress-deadline-annotation-computed-from-startup-probe))
- **Volumes**: Secret volumes for `SECURE_FILE` mount type env vars
- **Naming**: Resources named using the stored `serviceName` from the deployment record (generated via `K8sNamingUtils.generateName()` at first deploy; unified convention for all types)

Status: **Implemented**

#### Scenario: KNative Service creation
- **WHEN** a deploy operation is triggered for an MCP, Interceptor, Adapter, or Application deployment with `app.knative.enabled=true`
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
- **Progress deadline annotation**: `serving.knative.dev/progress-deadline` always set on service metadata — computed from startup probe parameters when a probe is configured, or from the deployment type's `startup-timeout` + buffer when no probe is configured (see [Progress Deadline](#requirement-progress-deadline-annotation-computed-from-startup-probe-or-startup-timeout))
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

### Requirement: Chained predictor + transformer for text-classification inference
When the deploy-time `InferenceTaskDetector` classifies a HuggingFace inference deployment as `TEXT_CLASSIFICATION`, the generated `InferenceService` SHALL contain both a `predictor` and a `transformer` block. When detection returns `NONE`, only the predictor block is emitted — identical to the pre-feature manifest shape. The transformer container template is sourced from `app.text-classification-transformer-container-config` (image required; resource defaults `100m`/`500m`/`256Mi`/`512Mi`, all four env-var-overridable).

For chained deployments:
- The predictor `protocolVersion` is pinned to `v2`.
- The predictor args have `--return_raw_logits` and `--task=sequence_classification` auto-injected (always).
- The transformer container is named `kserve-container`, receives `--model_name=<deploymentName>` and `--predictor_protocol=v2` args, and an `ID2LABEL` env var carrying the detected map serialized as a JSON object with stringified-integer keys.
- The deployment's resolved public URL is the transformer component's URL. The deployment is reported `RUNNING` only once the predictor reports `LOADED+UPTODATE` **and** the transformer component has surfaced a URL on `status.components` — a best-effort signal, since KServe does not surface per-component health for the transformer, so a transformer that previously surfaced a URL but later crashed continues to read as ready until KServe reconciles.
- The per-deployment `CiliumNetworkPolicy` carries three additions over the predictor-only baseline: a chained intra-cluster egress block whose `toEndpoints` list has six narrowed per-app selectors — same-`InferenceService`, `app=istiod` and `app=istio-ingressgateway` in `istio-system`, and `app=activator`, `app=autoscaler`, `app=controller` in `knative-serving` — with no port constraint; a same-`InferenceService` entry appended to the existing ingress `fromEndpoints`; and `8080/TCP` admitted into the ingress `toPorts` via container-port resolution (`InferenceDeploymentManager.getCiliumIngressPorts` injects `DEFAULT_KSERVE_SERVICE_PORT = 8080`), not via a chained-mode dedup literal. Non-chained deployments produce byte-identical policies to the pre-feature shape. *(Implemented via 022-transformer-cilium-policies — see that spec for FR-level detail. The chained-mode signal is read by `InferenceDeploymentManager`'s override of the `buildCiliumNetworkPolicy(...)` hook on `AbstractDeploymentManager`; the abstract base carries no inference-specific concept.)*

Operator-supplied predictor args containing `--return_probabilities` or `--task=<non-sequence_classification>` are rejected at manifest generation with HTTP 400 before any cluster mutation. If the transformer image is unset, the deploy is rejected with HTTP 500 before any cluster mutation.

Status: **Implemented** *(Implemented via 021-inference-task-transformer; chained-mode Cilium policy augmentation via 022-transformer-cilium-policies)*

#### Scenario: Chained manifest for a detected text-classification model
- **WHEN** detection returns `TEXT_CLASSIFICATION` with a valid `id2Label`
- **THEN** the manifest contains both `predictor` (with `protocolVersion: v2`, `--return_raw_logits`, `--task=sequence_classification`) and `transformer` (with the configured image, `ID2LABEL` env, `--model_name=<name>`, `--predictor_protocol=v2`)

#### Scenario: Predictor-only manifest for non-classification models
- **WHEN** detection returns `NONE`
- **THEN** the manifest is predictor-only, unchanged from the pre-feature shape

#### Scenario: Missing transformer image
- **WHEN** detection returns `TEXT_CLASSIFICATION` and the transformer image property is unset
- **THEN** the deploy is rejected with HTTP 500 (`MissingTransformerImageException`) before any cluster mutation

#### Scenario: Chained-mode Cilium policy augmentation
- **WHEN** manifest generation produces a chained `InferenceService` (predictor + transformer) and `app.cilium-network-policies-enabled=true`
- **THEN** the generated `CiliumNetworkPolicy` contains the chained intra-cluster egress block (six narrowed per-app `toEndpoints` — same-`InferenceService`, `istiod`/`istio-ingressgateway`, `activator`/`autoscaler`/`controller`), the same-`InferenceService` ingress `fromEndpoint`, and `8080/TCP` admitted into the ingress `toPorts` via the caller's container-port resolution (`DEFAULT_KSERVE_SERVICE_PORT`). When manifest generation produces predictor-only, the policy is byte-identical to the pre-feature shape. *(Implemented via 022-transformer-cilium-policies)*

### Requirement: NIM-specific resources generated for NIM deployments
When NIM is enabled, the system SHALL generate NIM-specific Kubernetes resources for NIM deployments. The manifest includes:

- **Image**: repository and tag parsed from the NGC `imageRef`
- **Container overrides**: custom `command`/`args` (when provided)
- **Environment variables**: plain + sensitive via Secret references
- **Resources**: limits/requests; default 1 NVIDIA GPU (`nvidia.com/gpu`) added to limits
- **Service expose**: standard port (default: 8000), optional gRPC port, service type `ClusterIP`
- **Startup probe**: converted from domain `ProbeProperties` to NIM `StartupProbe` by `NimProbeConverter` (sets `enabled: true` on the NIM probe wrapper)
- **Progress deadline annotation**: `serving.knative.dev/progress-deadline` always set on service metadata — computed from startup probe parameters when a probe is configured, or from the deployment type's `startup-timeout` + buffer when no probe is configured (see [Progress Deadline](#requirement-progress-deadline-annotation-computed-from-startup-probe-or-startup-timeout))
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

### Requirement: Progress deadline annotation computed from startup probe or startup timeout
The system SHALL automatically compute and set the `serving.knative.dev/progress-deadline` annotation on generated manifests. This prevents Kubernetes from terminating long-starting deployments (e.g., large model downloads) before they are ready.

The annotation is set on:
- **KNative Service**: RevisionTemplate metadata annotations (only when a startup probe is configured)
- **KServe InferenceService**: Service-level metadata annotations (always)
- **NIM Service**: Service-level metadata annotations (always)

**When a startup probe is configured**, the deadline is computed from the probe parameters: `progressDeadline = initialDelaySeconds + ((failureThreshold - 1) * periodSeconds) + timeoutSeconds + bufferSeconds`

When probe fields are not explicitly set, configurable defaults are used (matching Kubernetes defaults): `initialDelaySeconds=0`, `periodSeconds=10`, `failureThreshold=3`. A configurable buffer (default: 30s) is added to account for image pull time, readiness probe, and scheduling overhead.

**When no startup probe is configured** (KServe and NIM only), the deadline falls back to the deployment type's `startup-timeout` value plus buffer: `progressDeadline = startupTimeout + bufferSeconds`. This ensures that the Kubernetes-level timeout aligns with the application-level health check timeout.

For KNative deployments (MCP, Interceptor, Adapter, Application), when no startup probe is configured, no annotation is set and KNative's built-in default of 600s applies.

Status: **Implemented**

#### Scenario: Progress deadline set from startup probe
- **WHEN** a deployment has `probeProperties.enabled = true` with startup probe timing fields
- **THEN** the `serving.knative.dev/progress-deadline` annotation is computed from probe parameters and set on the manifest

#### Scenario: Default probe values used for missing fields
- **WHEN** a startup probe does not specify `initialDelaySeconds`, `periodSeconds`, or `failureThreshold`
- **THEN** the configurable default values are used in the formula

#### Scenario: Fallback deadline for KServe and NIM without startup probe
- **WHEN** a KServe or NIM deployment does not have a startup probe configured
- **THEN** the `serving.knative.dev/progress-deadline` annotation is set to `startupTimeout + bufferSeconds`

#### Scenario: No annotation for KNative without startup probe
- **WHEN** a KNative deployment (MCP, Interceptor, Adapter, Application) does not have a startup probe configured
- **THEN** no `serving.knative.dev/progress-deadline` annotation is set and KNative's built-in default of 600s applies

### Requirement: Strategy selected per deployment type
The system SHALL automatically select the correct manifest generation strategy based on deployment type and enabled feature flags.

Status: **Implemented**

#### Scenario: Strategy routing
- **WHEN** a deploy operation is triggered
- **THEN** the strategy is selected: KNative for MCP/Interceptor/Adapter/Application, KServe for Inference, NIM for NIM

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
- Chained transformer section: `com.epam.aidial.deployment.manager.service.manifest.TextClassificationTransformerSection`
- Transformer container template (env-var-backed): `app.text-classification-transformer-container-config` in `application.yml`; cloned via `AppProperties.cloneTextClassificationTransformerContainerConfig()`
- NIM manifest generator: `com.epam.aidial.deployment.manager.service.manifest.NimManifestGenerator`
- Base probe converter: `com.epam.aidial.deployment.manager.service.manifest.ProbeConverter` — translates `ProbeProperties` to Fabric8 `io.fabric8.kubernetes.api.model.Probe`
- KServe probe converter: `com.epam.aidial.deployment.manager.service.manifest.KserveProbeConverter` — converts Fabric8 `Probe` or `ProbeProperties` to `io.kserve.serving.v1beta1...StartupProbe`
- NIM probe converter: `com.epam.aidial.deployment.manager.service.manifest.NimProbeConverter` — converts Fabric8 `Probe` or `ProbeProperties` to `com.nvidia.apps.v1alpha1.nimservicespec.StartupProbe` (with `enabled: true`)
- Progress deadline calculator: `com.epam.aidial.deployment.manager.service.manifest.ProgressDeadlineCalculator` — computes progress deadline from startup probe config with configurable defaults and buffer, or falls back to startup timeout + buffer when no probe is configured
- Manifest field navigation: `KnativeMappers`, `InferenceMappers`, `NimMappers` (MappingChain pattern)
- KNative client: `com.epam.aidial.deployment.manager.kubernetes.knative.*`
- NIM client: `com.epam.aidial.deployment.manager.kubernetes.nim.*`
- KServe client: `com.epam.aidial.deployment.manager.kubernetes.kserve.*`
- CRD definitions: `src/main/resources/kubernetes/crd/`
- Fabric8 CRD code generator: runs at build time via `io.fabric8.java-generator` Gradle plugin
- Feature flags: `app.knative.enabled`, `app.nim.enabled`, `app.kserve.enabled`
- Related specs: `mcp-deployments`, `interceptor-deployments`, `adapter-deployments`, `application-deployments`, `inference-deployments`, `nim-deployments`
