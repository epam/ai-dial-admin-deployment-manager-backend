# Feature Specification: Node Pool Selector

**Feature Branch**: `016-node-pool-selector`  
**Created**: 2026-04-21  
**Status**: Draft  
**Input**: User description: "Design an API for a node pool selector UI. Users select a single node pool for their deployment. A new API returns available node pools with live Kubernetes resource utilization. Deployment create/update/get endpoints are extended to carry the selected node pool."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Available Node Pools (Priority: P1)

An administrator opens the deployment form and sees a list of available node pools. Each pool card displays the pool name, description, node spec (CPU, memory, GPU per node), how many nodes are running vs. max allowed, and per-node resource utilization breakdowns. This allows the administrator to make an informed decision about which pool to place their deployment on.

**Why this priority**: Without the ability to view node pools and their utilization, the user cannot make an informed selection. This is the foundational read operation that everything else depends on.

**Independent Test**: Can be fully tested by calling the node pools API and verifying that it returns configured pools enriched with live Kubernetes node data. Delivers immediate value by giving administrators visibility into cluster capacity.

**Acceptance Scenarios**:

1. **Given** the system has node pools configured and the Kubernetes cluster has running nodes, **When** the user requests the list of available node pools, **Then** each pool is returned with its configured spec (max nodes, CPU, memory, GPU per node) and live per-node utilization data (allocatable resources, scheduled/requested resources) for every running node in that pool.
2. **Given** a configured node pool has zero running nodes, **When** the user requests the list of available node pools, **Then** that pool is returned with running nodes count of zero, an empty node list, but still includes the configured spec (max nodes, per-node CPU, memory, GPU) so the user can see what capacity would be available once nodes scale up.
3. **Given** a configured node pool refers to a label selector that matches no Kubernetes nodes, **When** the user requests the list, **Then** the pool appears with zero running nodes and an empty node list — it is not omitted or treated as an error.

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

- What happens when a configured node pool's label selector matches nodes that appear/disappear between requests? The API returns a point-in-time snapshot; stale data is expected and acceptable.
- What happens when the Kubernetes API is unreachable when listing node pools? The system returns an error indicating that live utilization data is temporarily unavailable.
- What happens when a deployment references a node pool that is later removed from configuration? The stored node pool name remains on the deployment record, but it will no longer appear in the available pools list. Validation on create/update still rejects unknown pool names; existing assignments are not retroactively invalidated.
- What happens when a node reports zero allocatable resources for a resource type (e.g., no GPU)? The utilization for that resource type is returned as zero allocatable and zero scheduled.
- What happens when a deployment with a node pool is deployed but the pool's nodes are all at capacity? The deployment is activated normally with the affinity constraint; Kubernetes handles pending pod scheduling — the system does not pre-check capacity before deploying.
- What happens when a deployment references a node pool that was removed from configuration and the user redeploys? The deploy operation should fail with a validation error since the pool is no longer configured and the label selector cannot be resolved.

## Clarifications

### Session 2026-04-21

- Q: Should node affinity be a hard constraint (pods stay Pending if no matching nodes) or soft constraint (prefer but fall back)? → A: Hard constraint — pods only schedule on matching pool nodes.
- Q: Should node pool affinity apply to all deployment types or only a subset? → A: All deployment types (MCP, Interceptor, Adapter, Application, NIM, Inference).
- Q: Should the node pools endpoint be public or internal API? → A: Public API (`/api/v1/node-pools`), same auth as deployment endpoints.
- Q: Should K8s node utilization data be cached? → A: No cache — always query live. Caching deferred until needed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a public API endpoint (`/api/v1/node-pools`) to list all configured node pools enriched with live Kubernetes node utilization data. The endpoint follows the same authentication rules as deployment endpoints.
- **FR-002**: Each node pool in the response MUST include: pool name, description (if configured), max nodes, per-node spec (CPU, memory, GPU), number of running nodes, and a list of individual running nodes with their resource utilization.
- **FR-003**: Each running node entry MUST include: node name, allocatable resources (CPU, memory, GPU), and currently scheduled/requested resources (CPU, memory, GPU).
- **FR-004**: Node pool configuration MUST be provided via a single `NODE_POOLS` environment variable containing a JSON array, with a cluster-wide `NODE_POOL_LABEL_KEY` for Kubernetes node label identification. Each pool specifies: name, description, max nodes, CPU (millicores), memory (bytes), and GPU count. The label selector is derived as `{labelKey: poolName}`. Configuration MUST be validated at startup (JSON format, required fields, positive numbers, no duplicate names).
- **FR-005**: The system MUST query the Kubernetes API live (no caching) to list nodes matching each pool's derived label selector to determine running node count and resource utilization.
- **FR-006**: Deployment create and update endpoints MUST accept an optional node pool name field.
- **FR-007**: The system MUST validate on create and update that the provided node pool name matches a configured pool; invalid names MUST be rejected with a 400 error.
- **FR-008**: Deployment retrieval endpoints (get by ID, list) MUST return the selected node pool name if one is set.
- **FR-009**: Node pool selection MUST be limited to a single pool per deployment; the field is a single value, not a list.
- **FR-010**: The system MUST persist the selected node pool name in the deployment record in the database.
- **FR-011**: Resource quantities (CPU, memory) MUST be returned in a consistent, machine-parseable format (e.g., CPU in millicores, memory in bytes) so clients can perform their own aggregation and display formatting.
- **FR-012**: When a node pool has zero running nodes, the system MUST still return the pool with its configured spec and an empty node list.
- **FR-013**: When a deployment with a node pool selection is activated (deployed), the system MUST apply a **hard** node affinity constraint (required during scheduling) to the workload, so pods are scheduled exclusively on nodes matching the pool's label selector. If no matching nodes are available, pods remain in Pending state.
- **FR-014**: When a deployment without a node pool selection is activated, the system MUST NOT apply any pool-specific node affinity — default scheduling behavior is preserved.
- **FR-015**: When a deployment's node pool selection is changed and the deployment is redeployed (rolling update), the system MUST update the workload's node affinity to reflect the new pool's label selector.
- **FR-016**: When a deployment's node pool selection is cleared (set to null) and the deployment is redeployed, the system MUST remove any previously applied pool-specific node affinity from the workload.
- **FR-017**: The deploy operation MUST validate that the deployment's selected node pool (if set) still exists in the current configuration; if the pool has been removed, the deploy MUST fail with a validation error.

### Key Entities

- **Node Pool (configured)**: A named group of Kubernetes nodes defined via `NODE_POOLS` JSON env var. Attributes: name, description, max nodes, CPU (millicores), memory (bytes), GPU count. Node label selector derived from cluster-wide `NODE_POOL_LABEL_KEY` + pool name.
- **Node Pool (runtime response)**: The configured pool enriched with live data: running node count and a list of individual node utilization entries.
- **Node Utilization**: Per-node resource snapshot: node name, allocatable CPU/memory/GPU, scheduled (requested) CPU/memory/GPU.
- **Deployment (extended)**: The existing deployment entity extended with an optional node pool name field.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Administrators can view all configured node pools with live resource utilization in a single request, enabling informed placement decisions.
- **SC-002**: Administrators can assign a node pool to a deployment during creation or update, and the selection is correctly persisted and returned on retrieval.
- **SC-003**: The node pool list response provides enough per-node detail for the client to render the UI shown in the design: pool name, running/max nodes, per-node and aggregate CPU/memory/GPU utilization.
- **SC-004**: Invalid node pool selections (non-existent pool names) are rejected at the API level before any deployment record is modified.
- **SC-005**: The system gracefully handles pools with zero running nodes by returning configured spec data without errors.
- **SC-006**: Deployments with a node pool selection are constrained to run on the correct pool's nodes when activated — verified by inspecting the Kubernetes workload's node affinity rules.
- **SC-007**: Changing or clearing a deployment's node pool and redeploying correctly updates or removes the node affinity constraint on the workload.

## Assumptions

- The Kubernetes cluster's node labels are already set up correctly to identify which nodes belong to which pool; this feature does not manage node labeling.
- Node pool configuration is provided by the platform operator via the `NODE_POOLS` JSON environment variable and does not change at high frequency; configuration changes require an application restart.
- GPU resources are reported by Kubernetes nodes via the standard `nvidia.com/gpu` extended resource; nodes without GPUs report zero for GPU-related fields.
- All deployment types (MCP, Interceptor, Adapter, Application, NIM, Inference) support node pool selection and affinity enforcement. For CRD-based types (NIM via NIMService, Inference via KServe), affinity is injected into the CRD's pod template spec.
- Node pool affinity is enforced via Kubernetes node affinity rules applied at deploy time using the pool's label selector. The system does not manage tolerations — if pool nodes have taints, the operator is responsible for configuring tolerations separately.
- Duplication of a deployment copies the source deployment's node pool selection to the new deployment.
- The "change image" bulk operation does not alter node pool selections.
