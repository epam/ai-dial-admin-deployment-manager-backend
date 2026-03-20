# Feature Specification: Application Image Definition & Deployment Type

**Feature Branch**: `011-application-type`
**Created**: 2026-03-20
**Status**: Draft
**Input**: User description: "Support new image definition and deployment type - Application. It should be identical to Adapter image definition and deployment (for now, can change in the future)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create Application Image Definition (Priority: P1)

An admin creates a new Application image definition so that Application container images can be registered and built in the system, just like Adapter images today.

**Why this priority**: Without an image definition, no Application deployments can be created. This is the foundational building block for the entire feature.

**Independent Test**: Can be fully tested by creating an Application image definition via the API and verifying it appears in the list of image definitions with type "APPLICATION". Delivers the ability to register Application images.

**Acceptance Scenarios**:

1. **Given** an authenticated admin, **When** they submit a valid Application image definition (name, version, source), **Then** the system persists the definition and returns it with a unique ID, type "APPLICATION", and status "NOT_BUILT".
2. **Given** an existing Application image definition, **When** any user lists image definitions filtered by type "APPLICATION", **Then** only Application image definitions are returned.
3. **Given** an existing Application image definition, **When** an admin updates its description or allowed domains, **Then** the changes are persisted and reflected on subsequent retrieval.
4. **Given** an existing Application image definition that is not referenced by any deployment, **When** an admin deletes it, **Then** it is removed from the system.

---

### User Story 2 - Create and Manage Application Deployment (Priority: P1)

An admin creates an Application deployment — either from an Application image definition (internal image source) or from an external source (image reference, registry) — configures its environment variables, resources, and scaling, and manages its lifecycle (deploy, undeploy, delete).

**Why this priority**: Deployments are the core value — allowing Application containers to run on the cluster. This is equally critical as image definitions since together they form the minimum viable feature.

**Independent Test**: Can be fully tested by creating an Application deployment (with or without an image definition) and verifying the deployment appears with type "APPLICATION" and correct configuration. Deploy/undeploy can be tested against a K8s cluster.

**Acceptance Scenarios**:

1. **Given** any valid deployment source (internal image, image reference, or external registry), **When** an admin creates a deployment of type "APPLICATION", **Then** the system persists the deployment with status "NOT_DEPLOYED" and all specified configuration (envs, resources, scaling, probe properties).
2. **Given** an existing Application deployment with status "UNDEPLOYED", **When** an admin triggers deploy, **Then** the system creates a Knative service on the cluster and the deployment status transitions to "DEPLOYING" and eventually "DEPLOYED".
3. **Given** a deployed Application deployment, **When** an admin triggers undeploy, **Then** the Knative service is removed and the deployment status transitions to "NOT_DEPLOYED".
4. **Given** an existing Application deployment, **When** any user lists deployments filtered by type "APPLICATION", **Then** only Application deployments are returned.
5. **Given** an existing Application deployment, **When** an admin updates its environment variables or scaling configuration, **Then** the changes are persisted and applied on the next deploy.
6. **Given** a deployed Application deployment, **When** a user requests pod information or logs, **Then** the system returns the pod list and streams logs identically to how it works for Adapter deployments.

---

### User Story 3 - Application Type in Config Import/Export (Priority: P2)

When importing or exporting system configuration, Application image definitions and deployments are included, following the same rules as Adapter types.

**Why this priority**: Config portability is important for environment migration but is secondary to basic CRUD and deployment operations.

**Independent Test**: Can be tested by exporting a configuration that includes Application image definitions and deployments, then importing it into a clean environment and verifying all Application entities are restored.

**Acceptance Scenarios**:

1. **Given** existing Application image definitions and deployments, **When** a user exports the system configuration, **Then** Application entities are included in the export with type "APPLICATION".
2. **Given** a valid configuration file containing Application image definitions and deployments, **When** an admin imports it, **Then** Application entities are created in the system with correct types and configuration.

---

### Edge Cases

- What happens when a user attempts to create an Application deployment with an internal image source referencing a non-Application image definition? The system should reject it with a validation error.
- What happens when an Application image definition is deleted while a deployment still references it? The system should prevent deletion (same behavior as Adapter).
- What happens when the "APPLICATION" type is used in API filter parameters alongside other types? The system should return results matching all specified types.
- What happens during config import if an Application image definition or deployment conflicts with an existing entry? The system should follow the same conflict resolution rules as Adapter imports.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support "APPLICATION" as a new image definition type, with the same attributes and behavior as "ADAPTER".
- **FR-002**: System MUST support "APPLICATION" as a new deployment type, with the same attributes and behavior as "ADAPTER" deployments.
- **FR-003**: System MUST allow filtering image definitions by the "APPLICATION" type in list endpoints.
- **FR-004**: System MUST allow filtering deployments by the "APPLICATION" type in list endpoints.
- **FR-005**: System MUST deploy Application deployments to the cluster using the same orchestration approach as Adapter deployments (same service type, same default port 8080).
- **FR-006**: System MUST support all standard deployment operations for Application type: create, read, update, delete, deploy, undeploy, duplicate, change-image, pod listing, log streaming, and event streaming.
- **FR-007**: System MUST include Application image definitions and deployments in configuration import and export, with the same validation and sanitization rules as Adapter.
- **FR-008**: System MUST enforce role-based access control for Application operations identically to Adapter (admin-only for write operations, all users for read operations).
- **FR-009**: *(Deferred — parity with existing types)* When an Application deployment uses an internal image source, the system SHOULD validate that the referenced image definition is of type "APPLICATION" (type matching). Deployments using other source types (image reference, external registry) do not require an image definition. **Note**: No existing deployment type enforces cross-type validation today; adding it for Application alone would be inconsistent. This validation should be addressed as a separate cross-cutting feature for all types.
- **FR-010**: System MUST persist Application image definitions and deployments in dedicated storage following the same data inheritance pattern as Adapter.

### Key Entities

- **Application Image Definition**: A container image specification of type "APPLICATION". Shares all attributes with Adapter Image Definition (name, version, source, description, license, topics, allowed domains, build status, image builder). Stored separately from other image definition types.
- **Application Deployment**: A runnable instance of an Application image. Shares all attributes with Adapter Deployment (source, display name, description, environment variables, scaling, resources, probe properties, container port, command, args, allowed domains, topics). Stored separately from other deployment types.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Admins can create, read, update, and delete Application image definitions through the API with the same success rate as Adapter image definitions.
- **SC-002**: Admins can create, deploy, undeploy, and manage Application deployments through the API with the same behavior and response times as Adapter deployments.
- **SC-003**: Application deployments run on the cluster as services with identical operational characteristics to Adapter deployments (scaling, health checks, networking).
- **SC-004**: All existing automated tests for Adapter functionality have equivalent Application counterparts that pass.
- **SC-005**: Configuration import/export round-trips preserve all Application image definitions and deployments without data loss.

## Assumptions

- The Application type intentionally mirrors Adapter in all aspects for now. This design choice enables future divergence — Application-specific attributes or behavior can be added later without affecting the Adapter type.
- The JSON polymorphic discriminator value will be `"application"` (lowercase), following the existing pattern where Adapter uses `"adapter"`.
- The database tables will be named `application_image_definition` and `application_deployment`, following the existing naming convention.
- Application deployments will be handled by the same deployment manager as Adapter deployments, with no behavioral differences.
- No new API endpoints are needed — the existing generic image definition and deployment endpoints will handle the new type through polymorphism.
