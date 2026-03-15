# Feature Specification: External Registry Reference for Sources

**Feature Branch**: `005-external-registry-ref`
**Created**: 2026-03-13
**Status**: Draft
**Input**: User description: "Store a ref/link to an external storage/registry inside general image/deployment sources (e.g. Docker image source, Git dockerfile source, direct image reference). These functional sources describe how to obtain an artifact but carry no information about where the artifact is catalogued — the external registry reference fills that gap for informational client purposes."

## Background

Several source types in the system are **functionally defined**: they tell the system *how* to obtain or build a container image (a Docker URI, a Git repository + Dockerfile path), but they say nothing about what the artifact *represents* in the wider software ecosystem. A Git dockerfile source for an MCP server has a git URL but no link to its MCP registry card. A direct image reference for a deployment has a Docker URI but no link to a GitHub repository or other catalog page.

Some sources are already **registry-bound**: `HuggingFaceSource` carries a `modelName` that is itself a HuggingFace registry identifier, and `NgcRegistrySource` carries an `imageRef` that is an NGC identifier. These sources already embed their catalog context and are outside the scope of this feature.

This feature adds an optional `externalRegistryRef` field to the **general/functional** source types only.

## Clarifications

### Session 2026-03-14

- Q: Should `ExternalRegistryRef` follow the project's polymorphic OOP pattern (typed subtype per known registry + `GenericRef` fallback) or remain a flat open-type with a free-form string discriminator? → A: Polymorphic with `GenericRef` fallback (Option C).
- Q: Should the deployment API expose the ImageDefinition's `externalRegistryRef` inline (read-only) for `InternalImageSource` deployments, or require clients to fetch the ImageDefinition separately? → A: Out of scope for this iteration; deferred to a follow-up feature.
- Q: Should typed subtype fields (e.g., `GitHubRef.repo`, `GenericRef.url`) be strictly validated against expected formats (regex/URL), or should the system accept any non-empty string? → A: Non-empty only; expected formats are documented conventions, not enforced constraints.
- Q: Should `externalRegistryRef` be included when deployments/image definitions are exported? → A: Included — treated as part of the source definition; preserved in export and restored on import.
- Q: Should `HuggingFaceRef` be included as a typed subtype? → A: No — HuggingFace deployments only support `HuggingFaceSource` (already registry-bound); no general source is used for HuggingFace deployments. Operators referencing HuggingFace from a general source use `GenericRef`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Attach an External Registry Reference When Defining a General Source (Priority: P1)

An operator creates or updates an image definition whose source is a Docker image or a Git repository. To help clients understand what the image represents in the broader ecosystem, the operator optionally attaches an external registry reference — for example, indicating that this Git-built MCP server corresponds to a specific MCP registry card, or that this Docker image source has a canonical GitHub repository.

**Why this priority**: This is the core write path. Without it no reference is ever stored and the feature has no effect.

**Independent Test**: Create an image definition with a Git dockerfile source that includes an `externalRegistryRef` of type `McpRegistryRef` pointing to an MCP registry package. Retrieve the image definition. Assert the response includes the typed external reference with the correct package name.

**Acceptance Scenarios**:

1. **Given** an image definition with a Git dockerfile source, **When** the operator includes an `externalRegistryRef` of type `McpRegistryRef` (e.g., package name `my-mcp-server`), **Then** the system persists the typed reference and returns it in subsequent reads.
2. **Given** an image definition with a Docker image source, **When** the operator includes an `externalRegistryRef` of type `GitHubRef` (e.g., repo `owner/my-project`), **Then** the system persists the typed reference and returns it in subsequent reads.
3. **Given** an image definition with any general source, **When** the operator includes a `GenericRef` with a URL pointing to an unlisted registry, **Then** the system accepts and persists the reference.
4. **Given** an image definition with any general source, **When** the operator omits `externalRegistryRef`, **Then** the system creates the record without any reference and no validation error is raised.
5. **Given** an existing image definition with an `externalRegistryRef`, **When** the operator sends an update request with a different typed reference (including a change of type), **Then** the new reference replaces the old one in subsequent reads.

---

### User Story 2 - Attach an External Registry Reference to a Direct-Reference Deployment Source (Priority: P1)

An operator creates a deployment whose source is a direct Docker image reference (no image definition). They optionally attach a typed external registry reference so clients know where to find more information about the deployed software.

**Why this priority**: `ImageReferenceSource` is the other primary general source type targeted by this feature. This story ensures deployments without an `ImageDefinition` chain can also carry registry context.

**Independent Test**: Create a deployment with `ImageReferenceSource` and a `GitHubRef`. Retrieve the deployment. Assert the typed reference is present in the source object of the response with the correct `repo`.

**Acceptance Scenarios**:

1. **Given** a deployment with a direct image reference source, **When** the operator includes a `McpRegistryRef` (e.g., package name `my-mcp-server`), **Then** the typed reference is persisted on the deployment record.
2. **Given** a deployment with a direct image reference source, **When** the operator includes a `GenericRef` with a URL, **Then** the reference is persisted and returned correctly.
3. **Given** a deployment with a direct image reference source, **When** the operator omits `externalRegistryRef`, **Then** the deployment is created without a reference and behaves identically to today.

---

### User Story 3 - Read External Registry References from Sources (Priority: P1)

A client application lists or fetches image definitions and deployments. For records that carry an external registry reference, the client reads the typed reference and uses the well-known structure of each type to compose a catalog URL and surface a "View in registry" link to end users.

**Why this priority**: This is the consumer-facing read path. Its correctness is as critical as the write path.

**Independent Test**: Seed records with typed external references (`McpRegistryRef`, `GitHubRef`, `GenericRef`) and without. Call the list and single-fetch endpoints. Assert each record's external reference is returned with its correct type discriminator and typed fields. Assert records without references have the field absent.

**Acceptance Scenarios**:

1. **Given** an image definition with a `McpRegistryRef` external reference, **When** a client calls `GET /api/v1/images/definitions/{id}`, **Then** the response includes the `externalRegistryRef` as a `McpRegistryRef` with the correct `packageName`.
2. **Given** a list of image definitions, some with typed references and some without, **When** a client calls `GET /api/v1/images/definitions`, **Then** each record correctly includes or omits the field with no errors.
3. **Given** a deployment with `ImageReferenceSource` carrying a `GitHubRef`, **When** a client calls `GET /api/v1/deployments/{id}`, **Then** the source object in the response includes the `GitHubRef` with the correct `repo`.
4. **Given** a record with a `GenericRef`, **When** the record is fetched, **Then** the response includes the `GenericRef` with the stored URL.

---

### User Story 4 - Remove an External Registry Reference (Priority: P2)

An operator needs to clear an external registry reference from a source (e.g., the catalog entry no longer exists or was entered in error).

**Why this priority**: Clearing a reference is important for data accuracy but adds no new capability.

**Independent Test**: Create a record with a typed reference. Update it by omitting the field. Assert the subsequent GET response has no `externalRegistryRef`.

**Acceptance Scenarios**:

1. **Given** an image definition with a `McpRegistryRef`, **When** the operator sends a PUT request that omits or nulls the `externalRegistryRef` field, **Then** the reference is removed and subsequent reads return no external reference.

---

### Edge Cases

- What happens when the operator sends a `GenericRef` with a non-URL string (e.g., a plain name instead of a URL)? The system accepts it — `GenericRef.url` requires only a non-empty string; URL format is a documented convention, not enforced.
- What happens when a typed subtype field is empty or blank (e.g., `McpRegistryRef { packageName: "" }`)? The system must reject the request with a clear validation error.
- What happens when an operator attempts to set `externalRegistryRef` on a registry-bound source (`HuggingFaceSource`, `NgcRegistrySource`)? The field is silently discarded — these source DTOs do not declare it, so Jackson's default `ignoreUnknown` behavior drops it. No error is raised; no data is stored.
- What happens when a legacy record (created before this feature) is read? The field is absent from the response; no error is raised.
- What happens when the operator sends an unknown type discriminator (not one of the defined subtypes)? The system should reject it — unknown discriminators are not forwarded; `GenericRef` is the intended extensibility path.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST support an optional `externalRegistryRef` field on `DockerImageSource` (used in image definitions with a pre-built container image as source).
- **FR-002**: The system MUST support an optional `externalRegistryRef` field on `GitDockerfileImageSource` (used in image definitions built from a Git repository and Dockerfile).
- **FR-003**: The system MUST support an optional `externalRegistryRef` field on `ImageReferenceSource` (used in deployments that reference a Docker image directly, without an image definition).
- **FR-004**: The `externalRegistryRef` field MUST NOT exist on registry-bound source types (`HuggingFaceSource`, `NgcRegistrySource`). These types use separate DTO hierarchies that do not declare the field; if a client includes it in a request payload, the field is silently discarded by the deserializer (standard Jackson `ignoreUnknown` behavior). No explicit rejection or validation error is raised — the field simply has no effect.
- **FR-005**: The `externalRegistryRef` MUST be a **polymorphic discriminated value**. The system MUST support the following typed subtypes, each with its own named field(s):
  - **`McpRegistryRef`**: `{ packageName: String }` — identifies a package/card in the MCP registry.
  - **`GitHubRef`**: `{ repo: String }` — identifies a GitHub repository (expected format: `owner/repo`).
  - **`GenericRef`**: `{ url: String }` — a URL pointing to any registry or catalog not covered by a typed subtype. This is the extensibility path for registries that do not yet have a dedicated subtype, including HuggingFace when referenced from a general source.
- **FR-006**: For all typed subtypes, the identifying field MUST be a non-empty string; the system MUST reject requests where it is blank. No further format validation is applied — expected formats (e.g., `owner/repo` for `GitHubRef.repo`, a fully-qualified URL for `GenericRef.url`) are documented conventions enforced by clients, not by the system.
- **FR-007**: The system MUST reject requests that send an unknown or unrecognised type discriminator for `externalRegistryRef`. Operators MUST use `GenericRef` to reference registries not yet represented by a typed subtype.
- **FR-008**: The `externalRegistryRef` MUST be returned in all API responses that include affected source types (GET single, GET list, POST and PUT responses), preserving the original type discriminator and all typed fields.
- **FR-009**: The `externalRegistryRef` MUST be optional. Omitting it is valid for all in-scope source types. No default value is applied.
- **FR-010**: The `externalRegistryRef` MUST NOT influence deployment behavior, image build pipelines, scheduling, or any functional system logic. It is purely informational metadata.
- **FR-011**: The `externalRegistryRef` MUST be persisted to the database and survive application restarts.
- **FR-012**: The `externalRegistryRef` MUST be included in export payloads for deployments and image definitions, and MUST be restored on import. It is treated as an integral part of the source definition, not as runtime-only metadata.

### Key Entities

- **ExternalRegistryRef** (abstract, polymorphic): A discriminated informational value attached to a general source, identifying the source artifact's entry in an external catalog or registry. Subtypes:
  - **`McpRegistryRef`**: Field `packageName` (String, non-empty) — identifies an MCP registry package or tool card.
  - **`GitHubRef`**: Field `repo` (String, non-empty; format `owner/repo`) — identifies a GitHub repository used as the canonical catalog or documentation reference.
  - **`GenericRef`**: Field `url` (String, non-empty; conventionally a fully-qualified URL) — catch-all for any registry not covered by a typed subtype; enables reference of new registries without system code changes.
- **DockerImageSource** (existing): Extended to carry an optional `ExternalRegistryRef`.
- **GitDockerfileImageSource** (existing): Extended to carry an optional `ExternalRegistryRef`.
- **ImageReferenceSource** (existing): Extended to carry an optional `ExternalRegistryRef`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can associate a typed external registry reference with any in-scope source in a single API call with no additional steps.
- **SC-002**: 100% of API responses for image definitions and deployments that carry an external registry reference include the complete typed reference (discriminator and all typed fields).
- **SC-003**: The presence or absence of an external registry reference has zero impact on deployment behavior — all existing records continue to function without modification.
- **SC-004**: Operators can reference any external registry without system code changes by using `GenericRef`.
- **SC-005**: The feature introduces no regression in existing image definition or deployment CRUD scenarios, as verified by the full test suite.
- **SC-006**: Registry-bound source types (`HuggingFaceSource`, `NgcRegistrySource`) do not declare the `externalRegistryRef` field. If a client includes it, the value is silently discarded — no data is stored, preventing data inconsistency.
- **SC-007**: Attempting to set an unknown type discriminator results in a clear validation error; `GenericRef` is the documented path for unlisted registries.

## Assumptions

**On scope (which sources are in scope):**
`HuggingFaceSource` and `NgcRegistrySource` are excluded because they are already intrinsically registry-bound — `modelName` on `HuggingFaceSource` and `imageRef` on `NgcRegistrySource` ARE the external identifiers. Adding a separate `externalRegistryRef` to these types would create redundant, potentially conflicting data. Furthermore, HuggingFace deployments only support `HuggingFaceSource` (a registry-bound type); general sources are never used for HuggingFace deployments, making a dedicated `HuggingFaceRef` subtype a dead code path. Operators who need to reference HuggingFace from a general source (e.g., a Docker image that packages an HF model) use `GenericRef` instead. `InternalImageSource` is excluded from carrying a settable `externalRegistryRef`: it points to an `ImageDefinition`, which itself (via its underlying `DockerImageSource` or `GitDockerfileImageSource`) now carries the `externalRegistryRef`. The registry context is accessible without duplication. Exposing the ImageDefinition's `externalRegistryRef` inline in deployment API responses for `InternalImageSource` deployments is explicitly deferred to a follow-up feature — clients needing the ref for such deployments must fetch the referenced ImageDefinition separately in this iteration.

**On structure (polymorphic, not flat):**
`ExternalRegistryRef` is modelled as a polymorphic discriminated type, consistent with all other polymorphic types in the project (`Source`, `ImageSource`, `Deployment`). Each known registry type gets a named typed subtype with fields that match the registry's identity model. `GenericRef { url }` is the designated escape hatch for registries that do not yet have a typed subtype — this preserves extensibility without requiring an open-string discriminator that clients cannot reliably interpret. An earlier flat model (`registryType: String`, `itemId: String`) was considered and rejected: it conflates structurally distinct identifiers into a single opaque field and introduces a normalisation problem (free-form discriminator strings with no canonical values).

**On URL resolution:**
The system does not resolve or return a navigable URL for typed subtypes. Clients compute the full catalog URL from the typed fields using their own URL templates per registry type. This keeps the backend decoupled from external registry URL structures, which may change independently. `GenericRef` is the exception — it stores the URL directly.

**On persistence:**
The `externalRegistryRef` will be stored as part of the existing JSON column used for source persistence (no new column needed). This assumption should be revisited only if filter/search on registry type or item identifier becomes a future requirement.

**On backward compatibility:**
No database migration is required for existing rows. The field defaults to absent/null for all pre-existing records.

**On initial registry type set:**
The three initial subtypes (`McpRegistryRef`, `GitHubRef`, `GenericRef`) cover the primary use cases identified. Additional typed subtypes can be introduced in future iterations without breaking existing records or clients that use `GenericRef`.
