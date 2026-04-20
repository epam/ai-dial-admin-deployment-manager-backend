# Feature Specification: Init Container Logs

**Feature Branch**: `014-init-container-logs`  
**Created**: 2026-04-10  
**Status**: Draft  
**Input**: The pod log endpoint only returns logs from the main container. When an init container fails, users cannot diagnose the root cause through the API — they must have direct Kubernetes cluster access, which most users lack. The API should allow retrieving logs from any container in a pod, including init containers.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Retrieve init container logs for a crashed deployment (Priority: P1)

A user has deployed a model (e.g., a NIM deployment) and the deployment status shows CRASHED. The failure occurred in an init container, but the user has no direct access to the Kubernetes cluster. Using the existing log endpoint, the user sees no useful output because the system only streams logs from the main container. The user needs to specify which container's logs to retrieve so they can diagnose the init container failure.

**Why this priority**: This is the core problem. Without this, users with no cluster access cannot diagnose init container failures at all, leading to support escalation and delayed resolution.

**Independent Test**: Can be fully tested by deploying a model whose init container fails, then requesting logs with a container name parameter targeting the init container. The API returns the init container's log output, enabling self-service diagnosis.

**Acceptance Scenarios**:

1. **Given** a deployment with a pod that has a failed init container, **When** the user calls the log endpoint with the init container's name specified, **Then** the SSE stream returns the init container's log output
2. **Given** a deployment with a pod that has a failed init container, **When** the user calls the log endpoint without specifying a container name, **Then** the system behaves as it does today (returns logs from the main container), preserving backward compatibility
3. **Given** a deployment with a pod, **When** the user specifies a container name that does not exist in the pod, **Then** the system responds with an appropriate error (e.g., 404 or 400)

---

### User Story 2 - List available containers for a pod (Priority: P2)

A user wants to know which containers (including init containers) exist in a pod before requesting logs. Currently, the pod listing endpoint does not expose container names. The user needs a way to discover the available containers so they can request the correct logs.

**Why this priority**: Without container discovery, users must guess or rely on documentation to know which container names to request. This story enables a self-service workflow where users first list containers, then request logs for the relevant one.

**Independent Test**: Can be fully tested by calling the pod list or pod detail endpoint and verifying the response includes container names (both regular and init containers) along with their status.

**Acceptance Scenarios**:

1. **Given** a deployment with a pod that has both init containers and regular containers, **When** the user lists pods for the deployment, **Then** the response includes all container names with a distinction between init containers and regular containers
2. **Given** a deployment with a pod that has only regular containers (no init containers), **When** the user lists pods, **Then** the response includes only the regular container names
3. **Given** a deployment pod where an init container is in a terminated state, **When** the user lists pods, **Then** the init container's status (e.g., terminated, running, waiting) is visible in the response

---

### Edge Cases

- What happens when a pod is still initializing and the init container is actively running (not yet terminated)? The system should still be able to stream logs from a running init container.
- What happens when the pod has been evicted or deleted? The system should return a 404 or appropriate error when the pod no longer exists.
- What happens when requesting `previous` logs for an init container that has not restarted? The system should return empty or an appropriate error.
- What happens when multiple init containers exist and only one has failed? The user should be able to target the specific failed init container by name.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The pod log endpoint MUST accept an optional `containerName` query parameter to specify which container's logs to stream
- **FR-002**: When `containerName` is not provided, the system MUST default to the current behavior (streaming logs from the main container), preserving full backward compatibility
- **FR-003**: When `containerName` is provided, the system MUST stream logs from the specified container, whether it is an init container or a regular container
- **FR-004**: When `containerName` refers to a container that does not exist in the pod, the system MUST return an error response
- **FR-005**: The pod listing response MUST include container information: name, type (`INIT`, `WORKLOAD`, or `SIDECAR`), and current status for each container in each pod. Consumers use the `WORKLOAD` type to identify the main container whose logs should be shown by default.
- **FR-006**: The log endpoint MUST support streaming logs from init containers in both running and terminated states
- **FR-007**: All existing query parameters (`sinceTime`, `sinceSeconds`, `tail`, `previous`) MUST continue to work when `containerName` is specified

### Key Entities *(include if feature involves data)*

- **Container Info**: Represents a container within a pod — includes name, type (`INIT`, `WORKLOAD`, or `SIDECAR`), and current state (running, waiting, terminated). `WORKLOAD` identifies the main business-logic container (exactly one per pod); `SIDECAR` covers helpers like Knative queue-proxy, service-mesh proxies, and K8s 1.29+ `restartPolicy: Always` init containers. This is a read-only projection of Kubernetes pod state, not a persisted entity.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can retrieve init container logs for any deployment pod through the API without requiring direct Kubernetes cluster access
- **SC-002**: Existing integrations that call the log endpoint without a container name parameter continue to work identically (zero breaking changes)
- **SC-003**: Users can discover all container names (init and regular) for a given pod through the API
- **SC-004**: Users can diagnose init container failures within the same workflow they use for main container logs, reducing the need for cluster access escalation

## Assumptions

- Init containers are defined by the Kubernetes operators (e.g., NIM operator) or by custom resource definitions, not by this system. The system reads init container information from the Kubernetes API at query time.
- The Fabric8 Kubernetes client already supports reading logs from specific containers via `.inContainer(name)`, which is used by the existing log implementation.
- The set of containers in a pod (including init containers) can change between pod restarts but is stable for the lifetime of a single pod instance.
- This feature applies to all deployment types that expose the pod log endpoint (NIM, Inference, Knative-based deployments).
