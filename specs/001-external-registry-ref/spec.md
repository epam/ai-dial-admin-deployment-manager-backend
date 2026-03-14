# Feature Specification: External Registry Reference for Sources

**Feature Branch**: `001-external-registry-ref`
**Created**: 2026-03-13
**Status**: Draft
**Input**: User description: "Store a ref/link to an external storage/registry inside general image/deployment sources (e.g. Docker image source, Git dockerfile source, direct image reference). These functional sources describe how to obtain an artifact but carry no information about where the artifact is catalogued — the external registry reference fills that gap for informational client purposes."

## Background

Several source types in the system are **functionally defined**: they tell the system *how* to obtain or build a container image (a Docker URI, a Git repository + Dockerfile path), but they say nothing about what the artifact *represents* in the wider software ecosystem. A Git dockerfile source for an MCP server has a git URL but no link to its MCP registry card. A direct image reference for a deployment has a Docker URI but no link to the model page on HuggingFace.

Some sources are already **registry-bound**: `HuggingFaceSource` carries a `modelName` that is itself a HuggingFace registry identifier, and `NgcRegistrySource` carries an `imageRef` that is an NGC identifier. These sources already embed their catalog context and are outside the scope of this feature.

This feature adds an optional `externalRegistryRef` field to the **general/functional** source types only.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Attach an External Registry Reference When Defining a General Source (Priority: P1)

An operator creates or updates an image definition whose source is a Docker image or a Git repository. To help clients understand what the image represents in the broader ecosystem, the operator optionally attaches an external registry reference — for example, indicating that this Docker image corresponds to a specific MCP registry card, or that this Git-built adapter is available on a model hub.

**Why this priority**: This is the core write path. Without it no reference is ever stored and the feature has no effect.

**Independent Test**: Create an image definition with a Git dockerfile source that includes an `externalRegistryRef` pointing to an MCP registry entry. Retrieve the image definition. Assert the response includes the external reference with the correct registry type and item identifier.

**Acceptance Scenarios**:

1. **Given** an image definition with a Git dockerfile source, **When** the operator includes an `externalRegistryRef` (e.g., registry type `mcp-registry`, item identifier `my-mcp-server`), **Then** the system persists the reference and returns it in subsequent reads.
2. **Given** an image definition with a Docker image source, **When** the operator includes an `externalRegistryRef` (e.g., registry type `huggingface`, item identifier `mistralai/Mistral-7B-v0.1`), **Then** the system persists the reference and returns it in subsequent reads.
3. **Given** an image definition with any general source, **When** the operator omits `externalRegistryRef`, **Then** the system creates the record without any reference and no validation error is raised.
4. **Given** an existing image definition with an `externalRegistryRef`, **When** the operator sends an update request with a changed reference, **Then** the new reference replaces the old one in subsequent reads.

---

### User Story 2 - Attach an External Registry Reference to a Direct-Reference Deployment Source (Priority: P1)

An operator creates a deployment whose source is a direct Docker image reference (no image definition). They optionally attach an external registry reference so clients know where to find more information about the deployed software.

**Why this priority**: `ImageReferenceSource` is the other primary general source type targeted by this feature. This story ensures deployments without an `ImageDefinition` chain can also carry registry context.

**Independent Test**: Create a deployment with `ImageReferenceSource` and an `externalRegistryRef`. Retrieve the deployment. Assert the reference is present in the source object of the response.

**Acceptance Scenarios**:

1. **Given** a deployment with a direct image reference source, **When** the operator includes an `externalRegistryRef` (e.g., registry type `docker-hub`, item identifier `library/nginx`), **Then** the reference is persisted on the deployment record.
2. **Given** a deployment with a direct image reference source, **When** the operator omits `externalRegistryRef`, **Then** the deployment is created without a reference and behaves identically to today.

---

### User Story 3 - Read External Registry References from Sources (Priority: P1)

A client application lists or fetches image definitions and deployments. For records that carry an external registry reference, the client reads the registry type and item identifier to compose a catalog URL and surface a "View in registry" link to end users.

**Why this priority**: This is the consumer-facing read path. Its correctness is as critical as the write path.

**Independent Test**: Seed records with and without external references. Call the list and single-fetch endpoints. Assert that records with references include them correctly and records without references have the field absent, with no errors in either case.

**Acceptance Scenarios**:

1. **Given** an image definition with an `externalRegistryRef`, **When** a client calls `GET /api/v1/images/definitions/{id}`, **Then** the response includes the `externalRegistryRef` with `registryType` and `itemId` matching what was stored.
2. **Given** a list of image definitions, some with and some without `externalRegistryRef`, **When** a client calls `GET /api/v1/images/definitions`, **Then** each record correctly includes or omits the field with no errors.
3. **Given** a deployment with source `ImageReferenceSource` carrying an `externalRegistryRef`, **When** a client calls `GET /api/v1/deployments/{id}`, **Then** the source object in the response includes the reference.

---

### User Story 4 - Remove an External Registry Reference (Priority: P2)

An operator needs to clear an external registry reference from a source (e.g., the catalog entry no longer exists or was entered in error).

**Why this priority**: Clearing a reference is important for data accuracy but adds no new capability.

**Independent Test**: Create a record with a reference. Update it by omitting the field. Assert the subsequent GET response has no reference.

**Acceptance Scenarios**:

1. **Given** an image definition with an `externalRegistryRef`, **When** the operator sends a PUT request that omits or nulls the field, **Then** the reference is removed and subsequent reads return no external reference.

---

### Edge Cases

- What happens when `registryType` is an unrecognized or custom string? The system must accept it — the set of registry types is open-ended.
- What happens when `externalRegistryRef` is provided but `itemId` is empty or blank? The system must reject the request with a clear validation error.
- What happens when an operator attempts to set `externalRegistryRef` on a registry-bound source (`HuggingFaceSource`, `NgcRegistrySource`)? The system must reject such a request — these sources are out of scope for this field.
- What happens when a legacy record (created before this feature) is read? The field is absent from the response; no error is raised.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST support an optional `externalRegistryRef` field on `DockerImageSource` (used in image definitions with a pre-built container image as source).
- **FR-002**: The system MUST support an optional `externalRegistryRef` field on `GitDockerfileImageSource` (used in image definitions built from a Git repository and Dockerfile).
- **FR-003**: The system MUST support an optional `externalRegistryRef` field on `ImageReferenceSource` (used in deployments that reference a Docker image directly, without an image definition).
- **FR-004**: The `externalRegistryRef` MUST NOT be accepted on registry-bound source types (`HuggingFaceSource`, `NgcRegistrySource`). Requests that include it on these types MUST be rejected with a validation error.
- **FR-005**: The `externalRegistryRef` MUST be a flat structure with exactly two required fields when present: `registryType` (non-empty string identifying the registry, e.g., `mcp-registry`, `docker-hub`, `huggingface`, `github`) and `itemId` (non-empty string identifying the item within that registry, e.g., a model name, a package name, or a `owner/repo` path).
- **FR-006**: The system MUST accept any non-empty string value for `registryType` to allow extension without system changes.
- **FR-007**: The `externalRegistryRef` MUST be returned in all API responses that include affected source types (GET single, GET list, POST and PUT responses).
- **FR-008**: The `externalRegistryRef` MUST be optional. Omitting it is valid for all in-scope source types. No default value is applied.
- **FR-009**: The `externalRegistryRef` MUST NOT influence deployment behavior, image build pipelines, scheduling, or any functional system logic. It is purely informational metadata.
- **FR-010**: The `externalRegistryRef` MUST be persisted to the database and survive application restarts.

### Key Entities

- **ExternalRegistryRef**: A flat, two-field informational value attached to a general source. Fields: `registryType` (non-empty string — the name of the external registry or catalog, e.g., `mcp-registry`, `docker-hub`, `huggingface`, `github`) and `itemId` (non-empty string — the identifier of the item within that registry; compound identifiers such as `owner/repo` or `org/model-name` are expressed as a single slash-delimited string). The structure is intentionally flat and open-typed — see Assumptions for design rationale.
- **DockerImageSource** (existing): Extended to carry an optional `ExternalRegistryRef`.
- **GitDockerfileImageSource** (existing): Extended to carry an optional `ExternalRegistryRef`.
- **ImageReferenceSource** (existing): Extended to carry an optional `ExternalRegistryRef`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can associate an external registry reference with any in-scope source in a single API call with no additional steps.
- **SC-002**: 100% of API responses for image definitions and deployments that carry an external registry reference include the complete reference data (`registryType` and `itemId`).
- **SC-003**: The presence or absence of an external registry reference has zero impact on deployment behavior — all existing records continue to function without modification.
- **SC-004**: Any non-empty string `registryType` value is accepted without error, enabling new registry types to be used without system changes.
- **SC-005**: The feature introduces no regression in existing image definition or deployment CRUD scenarios, as verified by the full test suite.
- **SC-006**: Attempting to set `externalRegistryRef` on a registry-bound source type (`HuggingFaceSource`, `NgcRegistrySource`) results in a clear rejection, preventing silent data inconsistency.

## Assumptions

**On scope (which sources are in scope):**
`HuggingFaceSource` and `NgcRegistrySource` are excluded because they are already intrinsically registry-bound — `modelName` on `HuggingFaceSource` and `imageRef` on `NgcRegistrySource` ARE the external identifiers. Adding a separate `externalRegistryRef` to these types would create redundant, potentially conflicting data. `InternalImageSource` is also excluded: it points to an `ImageDefinition`, which itself (via its underlying `DockerImageSource` or `GitDockerfileImageSource`) now carries the `externalRegistryRef`. The registry context is thus accessible without duplication.

**On structure (why flat, not polymorphic):**
Making `ExternalRegistryRef` polymorphic (e.g., `HuggingFaceLink { modelId }`, `McpRegistryLink { packageName }`, `GitHubLink { owner, repo }`) was considered. It was rejected because:
(a) The field is informational — clients display it, not process it structurally.
(b) Every per-type "structured" field reduces to a string identifier anyway; compound identifiers like `owner/repo` are already the universal convention across Docker Hub, HuggingFace, GitHub, and the MCP registry.
(c) The set of registry types is explicitly open-ended; a closed polymorphic hierarchy would need to be extended for every new registry, while a flat open-discriminator model (`registryType` string + `itemId` string) accepts any future registry with no code changes.
(d) Polymorphism adds persistence complexity (discriminator-based JSON deserialization) for no functional benefit over the flat model.
If specific registry types later need per-type validation or derived URL generation, that logic can be added to clients or as a computed enrichment layer without changing the stored data model.

**On persistence:**
The `externalRegistryRef` will be stored as part of the existing JSON column used for source persistence (no new column needed). This assumption should be revisited only if filter/search on registry type or item identifier becomes a future requirement.

**On backward compatibility:**
No database migration is required for existing rows. The field defaults to absent/null for all pre-existing records.
