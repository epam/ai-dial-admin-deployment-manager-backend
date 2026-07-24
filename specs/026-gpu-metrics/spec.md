# Feature Specification: GPU Memory & Utilization Metrics in the Model Servings Dashboard

**Feature Branch**: `feat/gpu-metric`
**Created**: 2026-07-23
**Status**: Implemented
**Capability**: deployment-metrics
**Input**: User description: "Wire real GPU telemetry into the existing metrics pipeline so the per-pod gpuUtilization / gpuMemoryBytes fields populate and the Model Servings dashboard GPU-memory gauge shows live data, instead of always rendering 'No Data'. Source the data from the NVIDIA DCGM exporter, only for deployments that actually request GPU, and keep the graceful-degradation contract when the exporter is absent." (GitHub issue #388)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See live GPU memory & utilization for a GPU-served model (Priority: P1)

An operator is running an inference model on a GPU node pool (e.g. the T4 or A100 pool). They open the deployment's **Metrics** panel and want to know how much GPU memory the model is consuming, how close it is to VRAM exhaustion, and how busy the GPU is. Today the GPU-memory gauge always shows "No Data". After this feature, the panel shows the model's live GPU memory used, the total GPU memory available to it, and its GPU utilization.

**Why this priority**: This is the core observability gap the feature exists to close. GPU memory and utilization are the single most important resource signals for a GPU-served model — without them an operator cannot tell whether a model is near VRAM exhaustion, whether a GPU is under- or over-provisioned, or diagnose OOM/scheduling issues from the dashboard. It is independently shippable and delivers the whole user value on its own.

**Independent Test**: Deploy an inference model that requests `nvidia.com/gpu` on a cluster where the DCGM exporter is present, call the metrics snapshot endpoint (or open the Metrics panel), and confirm the response reports non-null GPU utilization, GPU memory used, and GPU memory total for each running pod, with the GPU resource block marked available.

**Acceptance Scenarios**:

1. **Given** a running GPU inference deployment on a cluster with the DCGM exporter present, **When** the metrics snapshot is requested, **Then** each running pod reports GPU utilization (a ratio between 0 and 1), GPU memory used (bytes), and GPU memory total (bytes), and the `resources.gpu` availability block is marked available.
2. **Given** a pod scheduled onto more than one GPU, **When** the snapshot is requested, **Then** the pod's GPU memory used and total are the sums across its GPUs and its utilization is the average across its GPUs.
3. **Given** repeated snapshot requests within the short cache window, **When** the endpoint is polled faster than the cache TTL, **Then** GPU data is served from the same cached snapshot without re-querying the cluster (identical to the existing CPU/memory caching behaviour).

---

### User Story 2 - Snapshot never fails when GPU telemetry is missing (Priority: P1)

An operator requests metrics for a GPU deployment on a cluster that does not have the DCGM exporter installed, or where the exporter is temporarily unreachable. The snapshot must still return successfully with every other block populated; only the GPU block is marked unavailable, with a human-readable reason explaining that GPU telemetry could not be collected.

**Why this priority**: Graceful degradation is a hard contract rule of the metrics capability — a missing or failing telemetry source must never turn a snapshot into an error. GPU telemetry depends on a cluster prerequisite that is not guaranteed to be present, so this is not an edge case but the expected behaviour on many clusters. It ships together with P1 because the two are inseparable halves of the same contract.

**Independent Test**: Request metrics for a GPU deployment on a cluster with no DCGM exporter (or with the exporter unreachable) and confirm the response is HTTP 200, all non-GPU blocks are populated, and `resources.gpu` is unavailable with a clear reason.

**Acceptance Scenarios**:

1. **Given** a GPU deployment on a cluster with no DCGM exporter, **When** the snapshot is requested, **Then** the response is HTTP 200, replica counts and CPU/memory usage are reported, and `resources.gpu` is unavailable with a reason indicating GPU telemetry is unavailable.
2. **Given** a GPU deployment where the exporter exists but its metrics endpoint times out or errors, **When** the snapshot is requested, **Then** the response is HTTP 200, the GPU block is unavailable with a reason, and no other block is affected.
3. **Given** an operator has turned the GPU metrics collection off by configuration, **When** the snapshot is requested for a GPU deployment, **Then** the response is HTTP 200 and `resources.gpu` is unavailable with a reason indicating collection is disabled.

---

### User Story 3 - Non-GPU deployments are handled cleanly (Priority: P2)

An operator requests metrics for a deployment that does not request any GPU (a CPU-only MCP server, adapter, application, interceptor, or a CPU inference model). No GPU collection should be attempted, and the GPU block should carry a reason that makes clear the deployment simply has no GPU — distinct from "the exporter is missing".

**Why this priority**: It avoids wasted cluster round-trips for the common non-GPU case and removes operator confusion between "this workload has no GPU" and "GPU telemetry failed". It is lower priority because it does not deliver new data — it refines the existing placeholder behaviour — but it is small and improves clarity.

**Independent Test**: Request metrics for a deployment whose resources request no `nvidia.com/gpu`, and confirm no GPU scrape is attempted and `resources.gpu` is unavailable with a "deployment does not request GPU" reason.

**Acceptance Scenarios**:

1. **Given** a deployment that requests no GPU, **When** the snapshot is requested, **Then** no GPU telemetry collection is attempted and `resources.gpu` is unavailable with a reason stating the deployment does not request GPU.
2. **Given** a deployment that requests no GPU, **When** the snapshot is requested, **Then** each pod's GPU utilization, GPU memory used, and GPU memory total are null.

---

### Edge Cases

- **Partial pod coverage**: some running pods have GPU telemetry and others do not (e.g. a pod just scheduled, exporter not yet reporting it). Pods with data report it; pods without leave GPU fields null. The `resources.gpu` block is available as long as at least one pod reports GPU data.
- **Multi-GPU pods**: a pod bound to multiple GPUs aggregates memory (sum) and utilization (average) across its GPUs.
- **Shared GPU node**: the node hosting the deployment's pod also runs other tenants' GPU workloads. Only telemetry attributed to this deployment's pods (by pod + namespace identity) is reported; other pods' GPU usage is never leaked into this deployment's snapshot.
- **Deployment requests GPU but has no running pods** (undeployed/stopped/scaled-to-zero): no GPU data; the GPU block degrades with the no-data reason, consistent with the existing no-pods handling for CPU/memory.
- **Exporter reports memory in mebibytes**: the reported GPU memory used/total are exposed to clients in bytes, converted from the exporter's native units.
- **Non-integer / fractional GPU requests**: any request for GPU greater than zero qualifies the deployment for GPU collection.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The metrics snapshot MUST populate per-pod GPU utilization (a ratio between 0 and 1), GPU memory used (bytes), and GPU memory total (bytes) for deployments that request GPU, when GPU telemetry is available.
- **FR-002**: The system MUST attempt GPU telemetry collection only for deployments that request `nvidia.com/gpu` greater than zero; for all other deployments it MUST NOT attempt collection.
- **FR-003**: GPU telemetry per pod MUST be attributed to that pod by its identity (pod name + namespace); a pod's GPU usage MUST NOT be attributed to a different deployment or pod, even when GPUs are shared on a node with other tenants.
- **FR-004**: For a pod bound to multiple GPUs, GPU memory used and total MUST be the sums across the pod's GPUs and utilization MUST be the average across the pod's GPUs.
- **FR-005**: When GPU telemetry is present for at least one running pod, the `resources.gpu` availability block MUST be marked available.
- **FR-006**: When GPU telemetry cannot be collected — the deployment requests no GPU, the collection feature is disabled by configuration, the exporter prerequisite is absent, or the exporter is unreachable/erroring — the snapshot MUST still return HTTP 200 with all other blocks populated and `resources.gpu` marked unavailable with a distinct, human-readable reason for each of those cases.
- **FR-007**: GPU telemetry MUST be collected only in response to a metrics request (never via background polling) and MUST be served from the existing per-deployment response cache on the same terms as the other blocks.
- **FR-008**: Operators MUST be able to enable or disable GPU metrics collection independently of the other metrics blocks; the default MUST be enabled with graceful degradation.
- **FR-009**: Operators MUST be able to configure how the exporter is located and read (its location, selection, port, and metrics path) without code changes, with sensible defaults documented.
- **FR-010**: GPU memory values MUST be reported to clients in bytes regardless of the exporter's native unit, and utilization MUST be reported as a ratio between 0 and 1 regardless of the exporter's native unit.
- **FR-011**: The metrics API contract MUST expose GPU memory total in addition to the existing GPU utilization and GPU memory used fields, as a backward-compatible additive change.
- **FR-012**: The capability documentation and operator configuration reference MUST be updated to describe the new GPU behaviour, the exporter prerequisite, the new configuration properties, and the additional read-only cluster permission required to read the exporter.

### Key Entities *(include if feature involves data)*

- **Per-pod GPU usage**: the GPU consumption attributed to one pod — utilization (ratio 0–1), memory used (bytes), memory total (bytes). Extends the existing per-pod resource-usage record, which already carries CPU and memory.
- **GPU resource availability**: the per-block availability marker for GPU (`available` / `unavailable` + reason), already present in the snapshot contract.
- **GPU telemetry source (exporter)**: the cluster component that reports per-GPU framebuffer memory (used/free) and utilization, labelled with the owning pod and namespace. A cluster prerequisite, not part of this service.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a running GPU inference deployment on a cluster with the exporter present, 100% of snapshot requests return non-null GPU utilization, memory used, and memory total for every running pod that has GPU telemetry.
- **SC-002**: The Model Servings GPU-memory gauge shows live values (used vs total) instead of "No Data" for GPU deployments where telemetry is available.
- **SC-003**: 100% of snapshot requests on clusters without the exporter, or with GPU collection disabled, still succeed (HTTP 200) with all non-GPU blocks populated and a clear GPU-unavailable reason — zero snapshots fail because of missing GPU telemetry.
- **SC-004**: For non-GPU deployments, zero GPU-telemetry cluster round-trips are performed, and the GPU block reason distinguishes "no GPU requested" from "telemetry unavailable".
- **SC-005**: Reported GPU memory (used and total) matches the GPU's actual framebuffer figures within the exporter's reporting granularity, and utilization stays within 0–1.

## Assumptions

- The NVIDIA DCGM exporter is the GPU telemetry source. It is a **cluster prerequisite** deployed on GPU node pools; installing it is an operational/runbook concern, not part of this service. This mirrors how the CPU/memory block depends on the metrics-server already present in managed clusters.
- The exporter is configured to attribute each GPU to the consuming pod (i.e. it emits per-GPU series carrying the pod and namespace labels). Absent that mapping, per-pod attribution is not possible and the GPU block degrades gracefully.
- GPU memory used/free come from the exporter's framebuffer memory series (used and free); total is derived as used + free. Utilization comes from the exporter's GPU-utilization series.
- The default enablement mirrors the existing per-pod resource-usage block: on by default, degrading gracefully when the prerequisite is absent.
- This feature is snapshot-only, consistent with the metrics capability's current scope (no time-series/history; an instantaneous value only). Time-range GPU history remains part of the separate time-range follow-up.
- Multi-pod aggregation across replicas remains out of scope, consistent with the rest of the metrics snapshot: GPU figures are reported per pod.
- Reading the exporter requires an additional read-only cluster permission (reading the exporter's metrics through the existing cluster access mechanism); provisioning it is a runbook/RBAC concern.

## Dependencies

- Builds on the existing deployment-metrics capability (`specs/deployment-metrics/spec.md`, delivered by 023-deployment-metrics-api), reusing its snapshot endpoint, availability model, response cache, and request-triggered collection contract.
- Depends on the DCGM exporter being present on GPU node pools for data to be available; absent it, the feature degrades gracefully.

## Out of Scope

- Time-range / historical GPU metrics (part of the metrics time-range follow-up).
- GPU metrics-driven autoscaling.
- Aggregating GPU figures across replicas into a single deployment-level number.
- Installing, configuring, or managing the DCGM exporter itself.
- GPU metrics for NIM deployments' serving quality (this feature covers the engine-independent resource block; NIM serving metrics remain unsupported as today).
