# Feature Specification: Store Deployment Service Name

**Feature Branch**: `004-store-service-name`
**Created**: 2026-03-11
**Status**: Implemented
**Input**: User description: "Currently, we often rely on our naming conventions (K8sNamingUtils and IdExtractor) to define deployment by K8s objects and vice versa - it becomes a problem when 'resourceNamePrefix' changes, since existing K8s resources become lost. Deployment service (Knative/NIM/Kserve) name should be stored per each deployment (in deployment table) and used instead of id extraction."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Resilient Deployment Lookup After Prefix Change (Priority: P1)

An operator changes the `resourceNamePrefix` configuration value and restarts the deployment manager. Previously, this caused all existing deployments to become "orphaned" because the system could no longer derive the correct Kubernetes service name from the deployment ID. With this feature, the system retrieves the stored service name from the database, correctly locates existing Kubernetes resources, and continues managing them without interruption.

**Why this priority**: This is the core problem being solved. Without it, changing the prefix is a destructive operation that disconnects all existing deployments from their Kubernetes resources.

**Independent Test**: Can be fully tested by deploying a service, changing the `resourceNamePrefix`, restarting the application, and verifying the deployment is still correctly managed and accessible.

**Acceptance Scenarios**:

1. **Given** a running deployment with service name `old-prefix-my-deploy`, **When** the operator changes `resourceNamePrefix` from `old-prefix` to `new-prefix` and restarts the service, **Then** the system still manages the existing deployment using its stored service name `old-prefix-my-deploy`.
2. **Given** a running deployment with a stored service name, **When** the system receives a Kubernetes event for that service, **Then** the system matches the event to the correct deployment using the stored service name rather than deriving the ID from the resource name.
3. **Given** a deployment whose stored service name does not match the current naming convention, **When** the operator queries the deployment status, **Then** the system returns the correct status by looking up the Kubernetes resource using the stored name.

---

### User Story 2 - New Deployments Persist Service Name (Priority: P1)

When a new deployment is first deployed, the system generates the Kubernetes service name using a unified naming convention (`generateName`) and stores that name in the deployment record. This replaces the legacy split where Knative/NIM used a separate MCP-prefixed convention (`generateMcpPrefixedName`) and Inference used the standard convention. Going forward, all new deployments regardless of type use the same unified naming convention. All subsequent operations (status checks, updates, deletions) use the stored name.

**Why this priority**: Equally critical - this ensures every new deployment captures its service name at creation time, preventing future orphaning. It also simplifies the naming by eliminating the legacy MCP-prefix distinction.

**Independent Test**: Can be tested by creating a new deployment of each type and verifying that the service name follows the unified convention, is persisted in the database, and is used for all subsequent operations.

**Acceptance Scenarios**:

1. **Given** a new deployment request of any type (MCP, Adapter, Interceptor, NIM, Inference), **When** the system creates the Kubernetes service, **Then** the generated service name follows the unified naming convention (`generateName`) and is stored in the deployment record before the service is created.
2. **Given** a deployment with a stored service name, **When** the system needs to update, scale, or delete the deployment, **Then** it uses the stored service name to locate the Kubernetes resource.
3. **Given** a new Knative or NIM deployment created after the upgrade, **When** the service name is generated, **Then** it uses the unified convention (not the legacy MCP-prefixed convention).

---

### User Story 3 - Backward-Compatible Migration for Existing Deployments (Priority: P1)

When the system starts after an upgrade, existing deployments that do not yet have a stored service name are backfilled — but only if they have an active Kubernetes resource (i.e., their status is not NOT_DEPLOYED/STOPPED). Deployments with NOT_DEPLOYED/STOPPED status have no corresponding Kubernetes service, so no service name is populated. The system derives the service name using the currently configured `resourceNamePrefix` and the type-specific naming conventions, and persists it, ensuring no disruption during the transition. When the prefix is configured via `RESOURCE_NAME_PREFIX`, it overrides the defaults for all types; when not set, legacy defaults apply (MCP prefix for Knative/NIM, DM prefix for Inference).

**Why this priority**: Critical for safe upgrades - existing deployments must be migrated seamlessly without manual intervention.

**Independent Test**: Can be tested by upgrading from the previous version (with no service name column) and verifying that all deployed (non-NOT_DEPLOYED/STOPPED) deployments have their service names populated correctly, while NOT_DEPLOYED/STOPPED deployments remain without a service name.

**Acceptance Scenarios**:

1. **Given** an existing deployment with status other than NOT_DEPLOYED/STOPPED and without a stored service name, **When** the system starts after the schema migration, **Then** the service name is computed using the configured `resourceNamePrefix` and stored in the deployment record.
2. **Given** multiple deployments of different types (Knative, NIM, Inference) and a configured `resourceNamePrefix`, **When** backfill runs, **Then** each deployment receives the correct service name derived from the configured prefix and its type-specific naming convention.
3. **Given** no custom `resourceNamePrefix` is configured, **When** backfill runs, **Then** legacy defaults apply (MCP prefix for Knative/NIM, DM prefix for Inference).
4. **Given** an existing deployment with status NOT_DEPLOYED/STOPPED, **When** the migration runs, **Then** its service name remains empty because no Kubernetes resource exists for it.

---

### User Story 4 - Kubernetes Event Reconciliation Using Stored Names (Priority: P2)

When the system receives Kubernetes resource events (add, update, delete), it matches events to deployments by looking up which deployment owns the service name from the event, rather than extracting the deployment ID from the resource name using prefix-based conventions.

**Why this priority**: Important for correctness after prefix changes, but only relevant once stored names are in place (depends on P1 stories).

**Independent Test**: Can be tested by triggering Kubernetes events for a service whose name doesn't match the current naming convention and verifying the system still correctly reconciles the deployment.

**Acceptance Scenarios**:

1. **Given** a Kubernetes service event with resource name `old-prefix-my-deploy`, **When** the current prefix is `new-prefix`, **Then** the system finds the deployment by matching the stored service name and reconciles correctly.
2. **Given** a Kubernetes service event with an unknown resource name, **When** no deployment has that stored service name, **Then** the system ignores the event gracefully.

---

### Edge Cases

- What happens when two deployments would produce the same service name? The system must enforce uniqueness on the service name to prevent conflicts.
- What happens when a deployment's stored service name references a Kubernetes resource that no longer exists? The system should treat this as a NOT_DEPLOYED/STOPPED or CRASHED status, consistent with current behavior for missing resources.
- What happens during a rolling upgrade where some instances have the new code and others don't? The stored service name column should be nullable during transition, with the system falling back to the naming convention if the stored name is absent.
- What happens when a deployment is created but the Kubernetes service creation fails? The service name should still be stored, as it represents the intended resource name for retry operations.
- How is `serviceName` handled during config export/import? The service name MUST be excluded from exported configuration (it is a runtime/cluster-specific value). On import with OVERWRITE policy, if a deployment already exists, the existing service name MUST be preserved.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST store the Kubernetes service name as a persistent attribute of each deployment record.
- **FR-002**: System MUST generate and persist the service name at deploy time (when `deploy()` is invoked), before the Kubernetes resource is created. Deployments in NOT_DEPLOYED status have no service name until they are first deployed.
- **FR-003**: System MUST use the stored service name (not a derived name) when performing any Kubernetes operation (create, read, update, delete) on a deployment's service.
- **FR-004**: System MUST populate the service name for existing deployments during migration, but only for deployments whose status is not NOT_DEPLOYED/STOPPED. Deployments with NOT_DEPLOYED/STOPPED status have no Kubernetes resource and receive no service name. The migration MUST use the currently configured `resourceNamePrefix` (from `RESOURCE_NAME_PREFIX` env var) to derive service names. When the prefix is set, it overrides the default prefixes for all deployment types. When the prefix is not set (empty), the legacy defaults apply (MCP prefix for Knative/NIM, DM prefix for Inference).
- **FR-005**: System MUST match incoming Kubernetes events to deployments using the stored service name rather than extracting deployment IDs from resource names.
- **FR-006**: System MUST enforce uniqueness of the service name within the deployment records to prevent conflicts.
- **FR-007**: System MUST use a unified naming convention (`generateName`) for all new deployments regardless of type, replacing the legacy split where Knative/NIM used `generateMcpPrefixedName` and Inference used `generateName`.
- **FR-008**: Existing deployments migrated from the previous version MUST retain their service names as derived from the configured prefix at migration time to match their already-existing Kubernetes resources.

### Key Entities

- **Deployment**: Extended with a new `service_name` attribute that stores the Kubernetes service name. This is the name used in the Kubernetes resource metadata. Each deployment has exactly one service name, and each service name maps to exactly one deployment.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Changing the `resourceNamePrefix` configuration and restarting the system causes zero existing deployments to lose their association with Kubernetes resources.
- **SC-002**: All existing deployments with active Kubernetes resources (status != NOT_DEPLOYED/STOPPED) are automatically backfilled with their service names during upgrade, with zero manual intervention required. NOT_DEPLOYED/STOPPED deployments correctly receive no service name.
- **SC-003**: New deployments created after the upgrade have their service names persisted and used for all subsequent operations.
- **SC-004**: Kubernetes event reconciliation correctly matches events to deployments regardless of whether the resource name prefix has changed since the deployment was created.

## Assumptions

- The `resourceNamePrefix` configured at migration time matches the prefix that was used when the existing Kubernetes resources were originally created. If the prefix was changed before the migration, the derived service names will not match the actual K8s resource names.
- The service name is immutable once assigned - it does not change even if the naming convention or prefix changes later.
- The `DisposableResourceManager` (which also tracks K8s resources by generated names) will be updated to use stored service names consistently.
- The legacy `generateMcpPrefixedName` method is removed (not just deprecated) after this feature, as new deployments all use `generateName` and existing deployments have their names stored in the database.
- When a deployment is updated (via API or config import), the stored service name is preserved from the existing record — it is never overwritten by update operations.
- Manifest generators (`KnativeManifestGenerator`, `NimManifestGenerator`, `InferenceManifestGenerator`) and `CiliumNetworkPolicyCreator` receive the stored service name as a parameter for K8s resource naming.
