# Feature Specification: Node Pool Selector

**Feature Branch**: `016-node-pool-selector`  
**Created**: 2026-04-21  
**Status**: Draft  
**Capability**: deployments  
**Input**: User description: "Design an API for a node pool selector UI. Users select a single node pool for their deployment. A new API returns available node pools with live Kubernetes resource utilization. Deployment create/update/get endpoints are extended to carry the selected node pool."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Available Node Pools (Priority: P1)

An administrator opens the deployment form and sees a list of available node pools. Each pool card displays the pool name, description, max nodes, and per-node spec (CPU, memory, GPU). This allows the administrator to make an informed decision about which pool to place their deployment on.

**Why this priority**: Without the ability to view node pools, the user cannot make an informed selection. This is the foundational read operation that everything else depends on.

**Independent Test**: Can be fully tested by calling the node pools API and verifying that it returns all configured pools with their specs. Delivers immediate value by giving administrators visibility into available pools.

**Acceptance Scenarios**:

1. **Given** the system has node pools configured via `NODE_POOLS` env var, **When** the user requests the list of available node pools, **Then** each pool is returned with its configured spec (name, description, max nodes, CPU, memory, GPU per node).
2. **Given** no node pools are configured (empty `NODE_POOLS`), **When** the user requests the list, **Then** an empty list is returned.

---

### User Story 2 - Select a Node Pool for a Deployment (Priority: P2)

When creating or updating a deployment, the administrator selects exactly one node pool from the available pools. The selected node pool is persisted with the deployment record so that when the deployment is activated, workloads are scheduled onto nodes belonging to that pool.

**Why this priority**: This is the primary write interaction — once the user can view pools (P1), they need to be able to assign one to a deployment. Single selection only; multiple pool selection is explicitly out of scope.

**Independent Test**: Can be tested by creating a deployment with a node pool selection, then retrieving that deployment and verifying the node pool field is persisted and returned.

**Acceptance Scenarios**:

1. **Given** the user is creating a new deployment, **When** they provide a valid node pool name in the request, **Then** the deployment is created with that node pool selection persisted.
2. **Given** the user is updating an existing deployment, **When** they change the node pool to a different valid pool name, **Then** the deployment's node pool selection is updated.
3. **Given** the user provides a node pool name that does not match any configured pool, **When** they attempt to create or update a deployment, **Then** the system rejects the request with a validation error.
4. **Given** a deployment already has a node pool selected, **When** the user retrieves the deployment, **Then** the response includes the selected node pool name.

---

### User Story 3 - Enforce Node Pool Affinity on Deploy (Priority: P2)

When a deployment with a node pool selection is activated (deployed), the system automatically configures Kubernetes node affinity on the workload so that pods are scheduled exclusively onto nodes belonging to the selected pool. When the node pool selection is cleared, the affinity constraint is removed on the next deploy.

**Why this priority**: Same as P2 — selecting a node pool is meaningless if the system does not actually enforce placement. This is the other half of the selection story: persist the choice (Story 2) and enforce it (Story 3).

**Independent Test**: Can be tested by deploying a deployment that has a node pool selected, then inspecting the resulting Kubernetes workload to verify node affinity rules match the pool's label selector. Also testable by deploying without a pool and confirming no pool-specific affinity is set.

**Acceptance Scenarios**:

1. **Given** a deployment has a node pool selected, **When** the deployment is activated (deploy), **Then** the Kubernetes workload is created with node affinity rules that constrain scheduling to nodes matching the pool's configured label selector.
2. **Given** a deployment has a node pool selected and is already running, **When** the user changes the node pool and redeploys (rolling update), **Then** the Kubernetes workload's node affinity is updated to match the new pool's label selector.
3. **Given** a deployment has a node pool selected, **When** the user clears the node pool (sets to null) and redeploys, **Then** the pool-specific node affinity constraint is removed from the Kubernetes workload.
4. **Given** a deployment has no node pool selected, **When** the deployment is activated, **Then** no pool-specific node affinity is applied — existing scheduling behavior is preserved.

---

### User Story 4 - Clear Node Pool Selection (Priority: P3)

An administrator decides that a deployment should no longer be pinned to a specific node pool and clears the selection. The node pool field is optional — deployments can exist without a pool assignment.

**Why this priority**: This supports flexibility — not all deployments require pool pinning, and users may need to remove an assignment. Lower priority because the default (no pool) is the existing behavior.

**Independent Test**: Can be tested by updating a deployment to remove its node pool selection (set to null) and verifying it persists as unset.

**Acceptance Scenarios**:

1. **Given** a deployment has a node pool selected, **When** the user updates the deployment with a null node pool, **Then** the node pool selection is cleared.
2. **Given** a deployment has no node pool selected, **When** the user retrieves the deployment, **Then** the node pool field is absent or null.

---

### Edge Cases

- What happens when a deployment references a node pool that is later removed from configuration? The stored node pool name remains on the deployment record, but it will no longer appear in the available pools list. Validation on create/update still rejects unknown pool names; existing assignments are not retroactively invalidated.
- What happens when a deployment with a node pool is deployed but the pool's nodes are all at capacity? The deployment is activated normally with the affinity constraint; Kubernetes handles pending pod scheduling — the system does not pre-check capacity before deploying.
- What happens when a deployment references a node pool that was removed from configuration and the user redeploys? The deploy operation should fail with a validation error since the pool is no longer configured and the label selector cannot be resolved.
- What happens when `NODE_POOLS` contains invalid JSON or invalid field values? The application fails to start with a descriptive error message (fail-fast validation).

## Clarifications

### Session 2026-04-21

- Q: Should node affinity be a hard constraint (pods stay Pending if no matching nodes) or soft constraint (prefer but fall back)? → A: Hard constraint — pods only schedule on matching pool nodes.
- Q: Should node pool affinity apply to all deployment types or only a subset? → A: All deployment types (MCP, Interceptor, Adapter, Application, NIM, Inference).
- Q: Should the node pools endpoint be public or internal API? → A: Public API (`/api/v1/node-pools`), same auth as deployment endpoints.
- Q: Should K8s node utilization data be included? → A: No — the node pools API returns only configured data (no K8s queries). Live utilization deferred to a future iteration.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a public API endpoint (`/api/v1/node-pools`) to list all configured node pools. The endpoint follows the same authentication rules as deployment endpoints. No Kubernetes API calls are made — the response is purely configuration-driven.
- **FR-002**: Each node pool in the response MUST include: pool name, description (if configured), max nodes, and per-node spec (CPU in millicores, memory in bytes, GPU count).
- **FR-003**: Node pool configuration MUST be provided via a single `NODE_POOLS` environment variable containing a JSON array, with a cluster-wide `NODE_POOL_LABEL_KEY` for Kubernetes node label identification. Each pool specifies: name, description, max nodes, CPU (millicores), memory (bytes), and GPU count. The label selector is derived as `{labelKey: poolName}`. Configuration MUST be validated at startup (JSON format, required fields, positive numbers, no duplicate names). `NODE_POOL_LABEL_KEY` MUST be non-blank whenever `NODE_POOLS` is non-empty; otherwise it MAY be blank.
- **FR-004**: Deployment create and update endpoints MUST accept an optional node pool name field.
- **FR-005**: The system MUST validate on create and update that the provided node pool name matches a configured pool; invalid names MUST be rejected with a 400 error.
- **FR-006**: Deployment retrieval endpoints (get by ID, list) MUST return the selected node pool name if one is set.
- **FR-007**: Node pool selection MUST be limited to a single pool per deployment; the field is a single value, not a list.
- **FR-008**: The system MUST persist the selected node pool name in the deployment record in the database.
- **FR-009**: When a deployment with a node pool selection is activated (deployed), the system MUST constrain scheduling to nodes matching the pool's label selector. For Knative deployments, this is enforced via **hard node affinity** (`requiredDuringSchedulingIgnoredDuringExecution`) on the RevisionSpec. For NIM and KServe (Inference) deployments, this is enforced via **`nodeSelector`** on the respective CRD spec. If no matching nodes are available, pods remain in Pending state.
- **FR-010**: When a deployment without a node pool selection is activated, the system MUST NOT apply any pool-specific node affinity or nodeSelector — default scheduling behavior is preserved.
- **FR-011**: When a deployment's node pool selection is changed and the deployment is redeployed (rolling update), the system MUST update the workload's node affinity or nodeSelector to reflect the new pool's label selector.
- **FR-012**: When a deployment's node pool selection is cleared (set to null) and the deployment is redeployed, the system MUST remove any previously applied pool-specific node affinity or nodeSelector from the workload.
- **FR-013**: The deploy operation MUST validate that the deployment's selected node pool (if set) still exists in the current configuration; if the pool has been removed, the deploy MUST fail with a server error (500) since this represents a configuration inconsistency — the node pool was valid when the deployment was saved but has since been removed.

### Key Entities

- **Node Pool (configured)**: A named group of Kubernetes nodes defined via `NODE_POOLS` JSON env var. Attributes: name, description, max nodes, CPU (millicores), memory (bytes), GPU count. Node label selector derived from cluster-wide `NODE_POOL_LABEL_KEY` + pool name.
- **Deployment (extended)**: The existing deployment entity extended with an optional node pool name field.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Administrators can view all configured node pools with their specs in a single request, enabling informed placement decisions.
- **SC-002**: Administrators can assign a node pool to a deployment during creation or update, and the selection is correctly persisted and returned on retrieval.
- **SC-003**: Invalid node pool selections (non-existent pool names) are rejected at the API level before any deployment record is modified.
- **SC-004**: Deployments with a node pool selection are constrained to run on the correct pool's nodes when activated — verified by inspecting the Kubernetes workload's node affinity rules.
- **SC-005**: Changing or clearing a deployment's node pool and redeploying correctly updates or removes the node affinity constraint on the workload.
- **SC-006**: Invalid `NODE_POOLS` configuration (malformed JSON, missing fields, negative numbers, duplicate names) prevents application startup with a descriptive error.

## Assumptions

- The Kubernetes cluster's node labels are already set up correctly to identify which nodes belong to which pool; this feature does not manage node labeling.
- Node pool configuration is provided by the platform operator via the `NODE_POOLS` JSON environment variable and does not change at high frequency; configuration changes require an application restart.
- GPU resources are reported by Kubernetes nodes via the standard `nvidia.com/gpu` extended resource; nodes without GPUs report zero for GPU-related fields.
- All deployment types (MCP, Interceptor, Adapter, Application, NIM, Inference) support node pool selection and affinity enforcement. For CRD-based types (NIM via NIMService, Inference via KServe), affinity is injected into the CRD's pod template spec.
- Node pool affinity is enforced via Kubernetes node affinity rules applied at deploy time using the pool's label selector. The system does not manage tolerations — if pool nodes have taints, the operator is responsible for configuring tolerations separately.
- Duplication of a deployment copies the source deployment's node pool selection to the new deployment.
- The "change image" bulk operation does not alter node pool selections.
