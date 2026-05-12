# Feature Specification: NIM KServe Migration & Configurable Storage Size

**Feature Branch**: `015-nim-kserve-migration`  
**Created**: 2026-04-20  
**Status**: Implemented
**Capability**: nim-deployments, kubernetes-manifests
**Input**: User description: Migrate NIM CRD generation from standalone inferencePlatform with nginx ingress to kserve inferencePlatform with Knative autoscaling, removing direct ingress management, and adding configurable PVC storage size per deployment.

## Revision 2026-04-24: kserve mode made opt-in

The KServe migration described below is no longer the default: the pre-migration standalone/ingress behavior is restored as the default and KServe mode is enabled via `app.nim.deploy.kserve-mode-enabled` (env `K8S_NIM_DEPLOYMENT_KSERVE_MODE_ENABLED`, default `false`).

- When the flag is `false` (default): NIMService uses `inferencePlatform: standalone`; external exposure uses `expose.ingress` (TLS + nginx rules from `K8S_NIM_CLUSTER_HOST`) when `use-cluster-internal-url=false`; no `expose.router`; no Knative autoscaling annotations.
- When the flag is `true`: behavior matches the original FRs in this spec — `inferencePlatform: kserve`, `expose.router`, and Knative autoscaling annotations derived from the deployment `Scaling`.

FR-002 below is refined by current behavior: `min-scale`, `max-scale`, and `initial-scale` are always set (defaulting to `1/1/1` when the caller passes no `Scaling`); `autoscaling.knative.dev/class`, `autoscaling.knative.dev/metric`, and `autoscaling.knative.dev/target` are emitted only when the `Scaling` carries an `ACTIVE_REQUESTS` strategy, otherwise Knative cluster defaults apply.

The configurable PVC storage size feature (FR-008 – FR-014) is unaffected by the flag and applies in both modes.

FR-001 – FR-007 below are scoped to kserve mode (flag `true`). In standalone mode, the legacy behavior documented in `specs/nim-deployments/spec.md` applies.

## Clarifications

### Session 2026-04-20

- Q: Should `initialScale` be mapped to `autoscaling.knative.dev/initial-scale` annotation, and how should it be computed? -> A: Follow the same logic as KServe InferenceService (`InferenceManifestGenerator.applyScaling()`): compute `initialScale = Math.max(minScale, 1)` and set as `autoscaling.knative.dev/initial-scale` annotation. This ensures at least 1 pod is created on first deploy even when minScale is 0.
- Q: Should `storageSize` accept only binary suffixes or also plain bytes? -> A: Accept any valid Kubernetes resource quantity (binary suffixes, decimal suffixes, and plain bytes). Use Fabric8 `Quantity` parser for validation. Also enforce a configurable upper bound (default 200Gi).

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

### User Story 3 - Configurable Knative autoscaling defaults (Priority: P3)

Operators can configure default Knative autoscaling parameters (autoscaling class, metric, target concurrency) via application configuration, so different environments can tune autoscaling behavior without code changes.

**Why this priority**: The autoscaling defaults are reasonable out of the box (KPA, concurrency, target 10), but production environments may need tuning. This is lower priority because the default values work for initial deployment.

**Independent Test**: Can be tested by changing configuration values and verifying the generated NIMService annotations reflect the updated values.

**Acceptance Scenarios**:

1. **Given** custom autoscaling configuration (e.g., target concurrency of 20), **When** a NIM deployment manifest is generated, **Then** the `autoscaling.knative.dev/target` annotation reflects the configured value.
2. **Given** default configuration with no overrides, **When** a NIM deployment manifest is generated, **Then** the Knative autoscaling annotations use the default values (KPA class, concurrency metric, target 10).

---

### User Story 4 - Default Storage Size (Priority: P1)

An operator creates a NIM deployment without specifying a storage size. The system uses the default PVC size from the application template (20Gi), ensuring backward compatibility with existing deployments.

**Why this priority**: Most deployments will use the default size. Backward compatibility is critical -- existing deployments must continue to work without changes.

**Independent Test**: Can be fully tested by creating a NIM deployment without `storageSize` and verifying the generated NIMService manifest has the default `20Gi` PVC size.

**Acceptance Scenarios**:

1. **Given** a NIM deployment request without `storageSize`, **When** the deployment is created and deployed, **Then** the generated NIMService manifest uses the default PVC size (20Gi).
2. **Given** a NIM deployment request without `storageSize`, **When** the deployment details are retrieved, **Then** `storageSize` is null in the response.

---

### User Story 5 - Custom Storage Size (Priority: P1)

An operator creates a NIM deployment with a specific storage size (e.g., `50Gi`) to accommodate a large model. The system persists the value and uses it when generating the NIMService manifest.

**Why this priority**: This is the core storage feature -- allowing operators to size storage per model requirements.

**Independent Test**: Can be tested by creating a NIM deployment with `storageSize: "50Gi"` and verifying the generated NIMService manifest has the specified PVC size.

**Acceptance Scenarios**:

1. **Given** a NIM deployment request with `storageSize: "50Gi"`, **When** the deployment is created and deployed, **Then** the generated NIMService manifest has `storage.pvc.size: "50Gi"`.
2. **Given** a NIM deployment with `storageSize: "50Gi"`, **When** the deployment details are retrieved, **Then** `storageSize` is `"50Gi"` in the response.
3. **Given** a running NIM deployment with `storageSize: "50Gi"`, **When** the operator updates the deployment to `storageSize: "100Gi"`, **Then** the updated value is persisted and used on next deploy.

---

### User Story 6 - Invalid Storage Size Rejected (Priority: P2)

An operator attempts to create a NIM deployment with an invalid storage size format or a value exceeding the configured maximum. The system rejects the request with a 400 error.

**Why this priority**: Input validation prevents misconfiguration but is secondary to the happy path.

**Independent Test**: Can be tested by sending invalid `storageSize` values and verifying 400 responses.

**Acceptance Scenarios**:

1. **Given** a NIM deployment request with `storageSize: "abc"`, **When** the request is submitted, **Then** the system responds with 400.
2. **Given** a NIM deployment request with `storageSize: "0Gi"` (zero is not valid), **When** the request is submitted, **Then** the system responds with 400.
3. **Given** a NIM deployment request with `storageSize: "21474836480"` (plain bytes, no suffix), **When** the request is submitted, **Then** the system accepts it and persists the value.
4. **Given** a NIM deployment request with `storageSize: "500Gi"` and a configured maximum of `200Gi`, **When** the request is submitted, **Then** the system responds with 400 indicating the value exceeds the maximum.

---

### Edge Cases

- What happens when a NIM deployment has `minScale=0`? The system correctly sets `autoscaling.knative.dev/min-scale: "0"` (enabling Knative scale-to-zero) while `autoscaling.knative.dev/initial-scale` remains `"1"` to ensure at least one pod on first deploy.
- What happens when `maxScale` is not set on the deployment? The system uses the configured default value for `autoscaling.knative.dev/max-scale`.
- What happens when existing NIM deployments (created with standalone/ingress) are redeployed? The new manifest uses kserve mode without ingress, and the NIM operator handles the transition.
- What happens when `storageSize` is null on update? The default template size (20Gi) is used during manifest generation -- the field is simply not overridden.
- What happens when `storageSize` is set then cleared (set to null) on update? The cleared value is persisted as null; subsequent deploys use the template default.
- What happens when `storageSize` uses decimal suffixes like `"20G"`? The system accepts it -- Fabric8 Quantity parser treats `G` as 10^9 (decimal gigabytes), distinct from `Gi` (2^30 binary gibibytes).
- What happens when the configured max storage size is malformed? The system logs a warning and skips the upper bound check, allowing the request through.

## Requirements *(mandatory)*

### Functional Requirements

#### KServe Migration

- **FR-001**: System MUST set `inferencePlatform` to `kserve` on all generated NIMService manifests.
- **FR-002**: System MUST include Knative autoscaling annotations on NIMService metadata: `autoscaling.knative.dev/class`, `autoscaling.knative.dev/metric`, `autoscaling.knative.dev/target`, `autoscaling.knative.dev/min-scale`, `autoscaling.knative.dev/max-scale`, and `autoscaling.knative.dev/initial-scale`.
- **FR-003**: System MUST map the deployment's `minScale` value to the `autoscaling.knative.dev/min-scale` annotation, `maxScale` to the `autoscaling.knative.dev/max-scale` annotation, and compute `initialScale = Math.max(minScale, 1)` for the `autoscaling.knative.dev/initial-scale` annotation (following the same formula used by InferenceService scaling).
- **FR-004**: System MUST NOT generate an `expose.ingress` section on NIMService manifests. Only `expose.service` and `expose.router` should be present.
- **FR-005**: System MUST allow Knative autoscaling defaults (class, metric, target concurrency) to be configurable via application properties.
- **FR-006**: System MUST retain the existing `serving.knative.dev/progress-deadline` annotation alongside the new autoscaling annotations.
- **FR-007**: System MUST continue to set `NIM_SERVED_MODEL_NAME` env var as before (existing behavior preserved).

#### Configurable Storage Size

- **FR-008**: NIM deployment SHALL optionally accept a `storageSize` field (nullable String) in create and update requests.
- **FR-009**: When `storageSize` is provided, the system MUST use it as the PVC size in the generated NIMService manifest, overriding the template default.
- **FR-010**: When `storageSize` is null or not provided, the system MUST use the default PVC size from the application template (20Gi).
- **FR-011**: The `storageSize` field MUST be persisted in the database and returned in GET responses.
- **FR-012**: The `storageSize` field MUST be validated as a valid Kubernetes resource quantity using the Fabric8 `Quantity` parser. Accepted formats: plain bytes (e.g., `"21474836480"`), binary suffixes (`Ki`, `Mi`, `Gi`, `Ti`, `Pi`, `Ei`), and decimal suffixes (`k`, `M`, `G`, `T`). The value must be positive (> 0).
- **FR-013**: Invalid `storageSize` values MUST be rejected with HTTP 400.
- **FR-014**: A configurable upper bound (`app.validation.resources.max-storage-size`, env `RESOURCES_STORAGE_MAX_SIZE`, default `200Gi`) MUST be enforced. Storage sizes exceeding this limit are rejected with HTTP 400.

### Key Entities *(include if feature involves data)*

- **NIMService manifest**: The Kubernetes custom resource generated for NIM deployments. Key structural changes: metadata annotations gain Knative autoscaling entries; `inferencePlatform` is set to `kserve`; `expose.ingress` is removed; `storage.pvc.size` is optionally overridden by `storageSize`.
- **Knative autoscaling annotations**: A set of metadata annotations that configure Knative Serving's Pod Autoscaler (KPA) behavior -- class, metric, target, min-scale, max-scale, initial-scale.
- **`storageSize`**: Optional String field on NIM deployments. Stored in the `nim_deployment` table as `storage_size VARCHAR(64)` (migration V1.57). Validated via `@ValidStorageSize` custom constraint backed by `StorageSizeValidator` which delegates parsing to Fabric8 `Quantity` (through `KubernetesQuantityParser` utility). The validator reads the max from `app.validation.resources.max-storage-size` (default `200Gi`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All newly generated NIMService manifests use `inferencePlatform: kserve` and contain no ingress configuration.
- **SC-002**: Every NIMService manifest includes all six Knative autoscaling annotations (class, metric, target, min-scale, max-scale, initial-scale) with valid values.
- **SC-003**: The deployment's `minScale` and `maxScale` values correctly propagate to the corresponding Knative annotations, and `initial-scale` is always at least 1.
- **SC-004**: Autoscaling defaults are configurable without code changes (via application configuration).
- **SC-005**: NIM deployments with explicit `storageSize` generate NIMService manifests with the specified PVC size.
- **SC-006**: NIM deployments without `storageSize` generate NIMService manifests with the default 20Gi PVC size (backward compatible).
- **SC-007**: Invalid `storageSize` values are rejected at the API layer with 400.
- **SC-008**: Storage sizes exceeding the configured maximum (default 200Gi) are rejected at the API layer with 400.
- **SC-009**: The `storageSize` field is correctly persisted, updated, and returned through the full CRUD lifecycle.
- **SC-010**: All existing NIM deployment creation and update flows continue to work without regression -- all existing functional tests pass.

## Assumptions

- Knative Serving is installed and available in the target Kubernetes cluster. The system does not verify Knative availability.
- The NIM operator (NVIDIA) supports `inferencePlatform: kserve` and correctly handles the Knative autoscaling annotations on NIMService resources.
- The `expose.router` field (empty object) is required by the NIM operator for kserve mode and should remain in the manifest.
- The transition from standalone to kserve does not require database migration -- this is purely a manifest generation change. The `storageSize` field requires migration V1.57.
- Default autoscaling values (KPA class, concurrency metric, target 10) are reasonable starting defaults for NIM workloads.
- Validation uses Fabric8's `Quantity` parser which supports the full Kubernetes quantity format: binary suffixes (`Ki`, `Mi`, `Gi`, `Ti`, `Pi`, `Ei`), decimal suffixes (`k`, `M`, `G`, `T`), and plain bytes. The value must be positive.
- The PVC size is only applied during manifest generation (deploy time). Changing `storageSize` on an already-deployed NIM service requires redeployment to take effect.
- No migration of existing storage data is needed -- existing NIM deployments will have `storage_size = NULL`, which means "use default".
- The default maximum storage size of 200Gi is sufficient for most NIM models. Operators can override via `RESOURCES_STORAGE_MAX_SIZE` environment variable.
