# Feature Specification: Unified Deployment Source Model

**Feature Branch**: `003-unified-deployment-source`
**Created**: 2026-03-10
**Status**: Draft
**Input**: User description: "Unified deployment source model with direct imageReference support for Knative deployments"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Deploy a Knative service using a direct image reference (Priority: P1)

An operator wants to deploy an MCP, Adapter, or Interceptor service using a pre-built container image (e.g., from a public or private Docker registry) without first creating an image definition in the system.

**Why this priority**: This is the core new capability. It removes a mandatory dependency on image definitions for Knative-based deployments, enabling faster onboarding for teams with existing container images.

**Independent Test**: Can be tested by creating a new MCP/Adapter/Interceptor deployment with a direct `imageReference` source and verifying it deploys successfully to Kubernetes.

**Acceptance Scenarios**:

1. **Given** a valid Docker image reference, **When** an operator creates a Knative deployment (MCP, Adapter, or Interceptor) with source type `image_reference`, **Then** the deployment is created successfully without requiring an image definition.
2. **Given** a Knative deployment created with an `image_reference` source, **When** the operator triggers deploy, **Then** the system uses the provided image reference directly to create the Kubernetes service.
3. **Given** an invalid Docker image reference (fails format validation), **When** the operator attempts to create a deployment with that image reference, **Then** the system rejects the request with a validation error.

---

### User Story 2 - Create and manage deployments using the unified source model (Priority: P1)

An operator continues to create and manage deployments of all types (MCP, Adapter, Interceptor, Inference, NIM) through a consistent source-based API, where each deployment type declares its source as a typed object rather than scattered fields.

**Why this priority**: This is the foundational data model change that all deployment types rely on. Without it, the API contract is inconsistent across deployment types.

**Independent Test**: Can be tested by creating deployments of each type with their respective source types and verifying CRUD operations return the correct typed source objects.

**Acceptance Scenarios**:

1. **Given** an operator creating a Knative deployment with an `internal_image` source (referencing an image definition), **When** the deployment is retrieved, **Then** the response contains a `source` object with `$type: "internal_image"` and the image definition details.
2. **Given** an operator creating an Inference deployment with a `huggingface` source, **When** the deployment is retrieved, **Then** the response contains the source with `$type: "huggingface"` and the model name.
3. **Given** an operator creating a NIM deployment with an `ngc_registry` source, **When** the deployment is retrieved, **Then** the response contains the source with `$type: "ngc_registry"` and the image reference.
4. **Given** an operator providing an incompatible source type for a deployment (e.g., `ngc_registry` for an MCP deployment), **When** the create request is submitted, **Then** the system rejects it with a validation error.

---

### User Story 3 - Migrate existing deployments seamlessly (Priority: P1)

Existing deployments created before the unified source model must continue to work without operator intervention after the system is upgraded. Legacy data (scattered image definition fields and per-subtype source columns) is automatically migrated to the unified source format.

**Why this priority**: Data integrity and backward compatibility are critical for production upgrades. If migration fails, existing deployments become inaccessible.

**Independent Test**: Can be tested by populating a database with pre-migration deployment data, running the migration, and verifying all deployments are accessible with correctly structured source objects.

**Acceptance Scenarios**:

1. **Given** existing Knative deployments with `imageDefinitionType`, `imageDefinitionName`, and `imageDefinitionVersion` columns, **When** the system is upgraded, **Then** these deployments have an `internal_image` source containing the original image definition reference.
2. **Given** existing NIM deployments with a source JSON column on the `nim_deployment` table, **When** the system is upgraded, **Then** the source data is migrated to the base `deployment` table in the unified format.
3. **Given** existing Inference deployments with a source JSON column on the `inference_deployment` table, **When** the system is upgraded, **Then** the source data is migrated to the base `deployment` table in the unified format.
4. **Given** a migrated deployment of any type, **When** retrieved via the API, **Then** the response matches the new unified source format.

---

### User Story 4 - Export and import deployments with unified sources (Priority: P2)

An operator exports deployments (including their source configuration) and imports them into another environment. Internal image sources are exported without the image definition UUID (resolved by type + name + version on import).

**Why this priority**: Export/import is important for environment replication but is secondary to core CRUD and deployment functionality.

**Independent Test**: Can be tested by exporting a set of deployments, importing them into a clean environment, and verifying source data is preserved and image definitions are correctly resolved.

**Acceptance Scenarios**:

1. **Given** a deployment with an `internal_image` source, **When** exported, **Then** the export omits the `imageDefinitionId` but includes `imageDefinitionType`, `imageDefinitionName`, and `imageDefinitionVersion`.
2. **Given** an exported deployment with an `internal_image` source, **When** imported, **Then** the system resolves the image definition by type + name + version and populates the `imageDefinitionId`.
3. **Given** a deployment with an `image_reference` source, **When** exported and re-imported, **Then** the image reference is preserved exactly as-is.

---

### Edge Cases

- What happens when a Knative deployment has a `null` source? The system rejects deployment with an error indicating no image source is defined.
- What happens when an operator provides an `image_reference` source for an Inference or NIM deployment? The system rejects it with a source type validation error.
- What happens when an operator provides a `huggingface` source for a Knative deployment? The system rejects it with a source type validation error.
- What happens when image definition resolution fails during import (type + name + version not found)? The import fails with an appropriate error message.
- What happens to the `imageDefinitionId` field on the base deployment record? It is retained as a separate indexed column for query efficiency (e.g., filtering deployments by image definition), independent of the source JSON.

## Clarifications

### Session 2026-03-10

- Q: Is the API contract change (replacing individual image definition fields with a typed `source` object) a breaking change, and how should it be handled? → A: Breaking change is intentional and acceptable; no backward-compatible shim or API version bump is needed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST support a unified source model where every deployment carries a typed `source` object identifying its container image or model origin.
- **FR-002**: Knative-based deployments (MCP, Adapter, Interceptor) MUST accept two source types: `internal_image` (referencing an image definition) and `image_reference` (direct Docker image name).
- **FR-003**: Inference deployments MUST accept `huggingface` as their source type.
- **FR-004**: NIM deployments MUST accept `ngc_registry` as their source type.
- **FR-005**: The system MUST validate that the source type is compatible with the deployment type on both create and update operations.
- **FR-006**: When deploying a Knative service with an `image_reference` source, the system MUST use the provided image reference directly without requiring an image definition lookup.
- **FR-007**: When deploying a Knative service with an `internal_image` source, the system MUST resolve the image name from the referenced image definition.
- **FR-008**: The `image_reference` source MUST validate the image reference string as a valid Docker image name.
- **FR-009**: The `internal_image` source on creation MUST accept either an `imageDefinitionId` or a complete (`imageDefinitionType`, `imageDefinitionName`, `imageDefinitionVersion`) triple.
- **FR-010**: The system MUST migrate all existing deployment source data from legacy columns and subtype tables into the unified source column on upgrade, preserving all source information.
- **FR-011**: Legacy columns (`image_definition_type`, `image_definition_name`, `image_definition_version` on the deployment table; `source` on NIM and Inference subtype tables) MUST be removed after migration.
- **FR-012**: On export, `internal_image` sources MUST omit the `imageDefinitionId` (it is resolved by type + name + version on import).
- **FR-013**: The system MUST store the unified source as a JSON column on the base deployment record, supporting all four source type variants.
- **FR-014**: The API response for Knative deployments MUST return the source as a typed object with `$type` discriminator (`internal_image` or `image_reference`).
- **FR-015**: The API response for `internal_image` sources MUST include `imageDefinitionId`, `imageDefinitionName`, and `imageDefinitionVersion` as non-null fields.
- **FR-016**: The API contract change (replacing individual image definition fields with a typed `source` object on Knative deployment endpoints) is an intentional breaking change. No backward-compatible shim or API version bump is required.

### Key Entities

- **Source**: A polymorphic value object representing the origin of a deployment's container image or model. Four variants exist: `InternalImage` (references a managed image definition), `ImageReference` (direct Docker image URI), `NgcRegistry` (NVIDIA NGC model reference), and `HuggingFace` (HuggingFace model reference).
- **Deployment**: The base deployment record, now carrying a single `source` field (JSON) instead of scattered image definition columns. The `imageDefinitionId` is retained as a separate indexed column for query efficiency.
- **Source-to-deployment-type mapping**: Knative types accept `InternalImage` or `ImageReference`; Inference accepts `HuggingFace`; NIM accepts `NgcRegistry`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Operators can create and deploy Knative-based services using a direct image reference in a single request, without pre-creating an image definition.
- **SC-002**: All existing deployments continue to function correctly after upgrade with zero manual intervention required.
- **SC-003**: The API returns consistent typed source objects for all deployment types, with 100% of responses containing a valid `$type` discriminator.
- **SC-004**: Source type validation rejects 100% of incompatible source/deployment-type combinations at request time.
- **SC-005**: Data migration completes successfully for all three supported database vendors (H2, PostgreSQL, SQL Server) with no data loss.
- **SC-006**: Export/import round-trips preserve all source information, with internal image definitions correctly resolved by type + name + version on import.
