# Feature Specification: NIM KServe Migration

**Feature Branch**: `019-nim-kserve-migration`  
**Created**: 2026-04-20  
**Status**: Draft  
**Input**: User description: Migrate NIM CRD generation from standalone inferencePlatform with nginx ingress to kserve inferencePlatform with Knative autoscaling, removing direct ingress management and adding NIM_CACHE_PATH environment variable.

## Clarifications

### Session 2026-04-20

- Q: Should `initialScale` be mapped to `autoscaling.knative.dev/initial-scale` annotation, and how should it be computed? → A: Follow the same logic as KServe InferenceService (`InferenceManifestGenerator.applyScaling()`): compute `initialScale = Math.max(minScale, 1)` and set as `autoscaling.knative.dev/initial-scale` annotation. This ensures at least 1 pod is created on first deploy even when minScale is 0.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Deploy NIM model with Knative/KServe serving (Priority: P1)

An operator deploys a NIM model. The system creates a NIMService resource configured for kserve inference platform with Knative autoscaling annotations, allowing Knative Serving to manage routing and scaling instead of using standalone mode with nginx ingress.

**Why this priority**: This is the core change -- switching the inference platform determines how the NIM operator manages the deployment's networking, scaling, and lifecycle. Without this, the NIMService won't use Knative Serving.

**Independent Test**: Can be fully tested by deploying a NIM model and verifying the generated NIMService manifest has `inferencePlatform: kserve` and the correct Knative autoscaling annotations on the metadata.

**Acceptance Scenarios**:

1. **Given** a NIM deployment with valid NGC source and resource configuration, **When** the system generates the NIMService manifest, **Then** the `inferencePlatform` field is set to `kserve`.
2. **Given** a NIM deployment, **When** the NIMService manifest is generated, **Then** the metadata annotations include Knative autoscaling annotations with configurable values for class, metric, target, min-scale, max-scale, and initial-scale.
3. **Given** a NIM deployment with `minScale` and `maxScale` values in the deployment configuration, **When** the NIMService manifest is generated, **Then** the `autoscaling.knative.dev/min-scale` and `autoscaling.knative.dev/max-scale` annotations reflect those deployment values, and `autoscaling.knative.dev/initial-scale` is set to `Math.max(minScale, 1)`.
4. **Given** a NIM deployment with `minScale=0`, **When** the NIMService manifest is generated, **Then** `autoscaling.knative.dev/min-scale` is `"0"` and `autoscaling.knative.dev/initial-scale` is `"1"` (ensures at least 1 pod on initial deploy).

---

### User Story 2 - NIM deployment without ingress (Priority: P1)

When deploying a NIM model, the system no longer generates ingress configuration on the NIMService resource. Knative Serving handles external routing through its own router mechanism, so the NIMService only needs ClusterIP service exposure.

**Why this priority**: Ingress is tightly coupled to the inference platform. With kserve mode, Knative manages routing -- generating an ingress section would conflict with Knative's own routing and cause deployment issues.

**Independent Test**: Can be fully tested by deploying a NIM model and verifying the generated NIMService manifest has no `expose.ingress` section, only `expose.service` and `expose.router`.

**Acceptance Scenarios**:

1. **Given** a NIM deployment, **When** the NIMService manifest is generated, **Then** the `expose` section contains only `service` (with port and type) and `router` -- no `ingress` section is present.
2. **Given** a NIM deployment that previously would have had external URL exposure, **When** the NIMService manifest is generated, **Then** no ingress annotations, TLS configuration, or ingress rules are included.

---

### User Story 3 - NIM_CACHE_PATH environment variable (Priority: P2)

The system automatically sets the `NIM_CACHE_PATH` environment variable to `/tmp` on every NIM deployment, ensuring the NIM container uses the correct cache directory without requiring operators to configure it manually.

**Why this priority**: Required for correct NIM operation under kserve mode, but secondary to the core platform switch. Without this, the NIM container may attempt to use a non-existent or wrong cache path.

**Independent Test**: Can be fully tested by deploying a NIM model and verifying the generated NIMService manifest includes `NIM_CACHE_PATH=/tmp` in the env vars list.

**Acceptance Scenarios**:

1. **Given** a NIM deployment, **When** the NIMService manifest is generated, **Then** the env vars list includes `NIM_CACHE_PATH` with value `/tmp`.
2. **Given** a NIM deployment where the operator has not explicitly set `NIM_CACHE_PATH`, **When** the NIMService manifest is generated, **Then** `NIM_CACHE_PATH=/tmp` is automatically added.

---

### User Story 4 - Configurable Knative autoscaling defaults (Priority: P3)

Operators can configure default Knative autoscaling parameters (autoscaling class, metric, target concurrency) via application configuration, so different environments can tune autoscaling behavior without code changes.

**Why this priority**: The autoscaling defaults are reasonable out of the box (KPA, concurrency, target 10), but production environments may need tuning. This is lower priority because the default values work for initial deployment.

**Independent Test**: Can be tested by changing configuration values and verifying the generated NIMService annotations reflect the updated values.

**Acceptance Scenarios**:

1. **Given** custom autoscaling configuration (e.g., target concurrency of 20), **When** a NIM deployment manifest is generated, **Then** the `autoscaling.knative.dev/target` annotation reflects the configured value.
2. **Given** default configuration with no overrides, **When** a NIM deployment manifest is generated, **Then** the Knative autoscaling annotations use the default values (KPA class, concurrency metric, target 10).

---

### Edge Cases

- What happens when a NIM deployment has `minScale=0`? The system should correctly set `autoscaling.knative.dev/min-scale: "0"` (enabling Knative scale-to-zero) while `autoscaling.knative.dev/initial-scale` remains `"1"` to ensure at least one pod on first deploy.
- What happens when `maxScale` is not set on the deployment? The system should use the configured default value for `autoscaling.knative.dev/max-scale`.
- What happens when existing NIM deployments (created with standalone/ingress) are redeployed? The new manifest should use kserve mode without ingress, and the NIM operator should handle the transition.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST set `inferencePlatform` to `kserve` on all generated NIMService manifests.
- **FR-002**: System MUST include Knative autoscaling annotations on NIMService metadata: `autoscaling.knative.dev/class`, `autoscaling.knative.dev/metric`, `autoscaling.knative.dev/target`, `autoscaling.knative.dev/min-scale`, `autoscaling.knative.dev/max-scale`, and `autoscaling.knative.dev/initial-scale`.
- **FR-003**: System MUST map the deployment's `minScale` value to the `autoscaling.knative.dev/min-scale` annotation, `maxScale` to the `autoscaling.knative.dev/max-scale` annotation, and compute `initialScale = Math.max(minScale, 1)` for the `autoscaling.knative.dev/initial-scale` annotation (following the same formula used by InferenceService scaling).
- **FR-004**: System MUST NOT generate an `expose.ingress` section on NIMService manifests. Only `expose.service` and `expose.router` should be present.
- **FR-005**: System MUST automatically add `NIM_CACHE_PATH=/tmp` to the NIMService env vars for every NIM deployment.
- **FR-006**: System MUST allow Knative autoscaling defaults (class, metric, target concurrency) to be configurable via application properties.
- **FR-007**: System MUST retain the existing `serving.knative.dev/progress-deadline` annotation alongside the new autoscaling annotations.
- **FR-008**: System MUST continue to set `NIM_SERVED_MODEL_NAME` env var as before (existing behavior preserved).

### Key Entities *(include if feature involves data)*

- **NIMService manifest**: The Kubernetes custom resource generated for NIM deployments. Key structural changes: metadata annotations gain Knative autoscaling entries; `inferencePlatform` changes from `standalone` to `kserve`; `expose.ingress` is removed; `NIM_CACHE_PATH` env var is added.
- **Knative autoscaling annotations**: A set of metadata annotations that configure Knative Serving's Pod Autoscaler (KPA) behavior -- class, metric, target, min-scale, max-scale, initial-scale.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All newly generated NIMService manifests use `inferencePlatform: kserve` and contain no ingress configuration.
- **SC-002**: Every NIMService manifest includes all six Knative autoscaling annotations (class, metric, target, min-scale, max-scale, initial-scale) with valid values.
- **SC-003**: The deployment's `minScale` and `maxScale` values correctly propagate to the corresponding Knative annotations, and `initial-scale` is always at least 1.
- **SC-004**: Every NIMService manifest includes `NIM_CACHE_PATH=/tmp` in the environment variables.
- **SC-005**: Existing NIM deployment creation and update flows continue to work without regression -- all existing functional tests pass.
- **SC-006**: Autoscaling defaults are configurable without code changes (via application configuration).

## Assumptions

- Knative Serving is installed and available in the target Kubernetes cluster. The system does not verify Knative availability.
- The NIM operator (NVIDIA) supports `inferencePlatform: kserve` and correctly handles the Knative autoscaling annotations on NIMService resources.
- The `expose.router` field (empty object) is required by the NIM operator for kserve mode and should remain in the manifest.
- The `NIM_CACHE_PATH=/tmp` value is appropriate for all deployment scenarios under kserve mode. If an operator needs a different cache path, they can override it via explicit env var configuration on the deployment.
- The transition from standalone to kserve does not require database migration -- this is purely a manifest generation change.
- Default autoscaling values (KPA class, concurrency metric, target 10) are reasonable starting defaults for NIM workloads.
- The `useExternalUrl` / `clusterHost` configuration properties related to ingress generation can be deprecated or ignored for NIM deployments, since Knative handles routing.
