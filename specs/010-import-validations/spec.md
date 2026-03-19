# Feature Specification: Import Validations

**Feature Branch**: `010-import-validations`
**Created**: 2026-03-19
**Status**: Draft
**Input**: User description: "Config import does not validate deserialized entities. In DTOs there are annotations on fields, some of them are linked to custom validators. Import must validate deserialized entities (domain objects) similarly to how DTOs are validated. Do a research to find the best way to solve this issue."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Invalid imported config is rejected with clear errors (Priority: P1)

An administrator imports a ZIP configuration file that contains entities with invalid field values (e.g., deployment name with special characters, missing required fields, or oversized strings). The system detects these violations during import and rejects the operation with actionable error messages describing which entities failed validation and why.

**Why this priority**: Without validation, malformed data silently enters the system and can cause downstream failures in Kubernetes operations, database constraint violations, or broken deployments. This is the core gap that must be closed.

**Independent Test**: Can be fully tested by uploading a ZIP with known-invalid entities and verifying the import is rejected with descriptive validation errors.

**Acceptance Scenarios**:

1. **Given** a ZIP containing a deployment with a name using uppercase letters, **When** the admin imports the config, **Then** the import is rejected with an error indicating the name must contain only lowercase letters, numbers, and hyphens.
2. **Given** a ZIP containing an image definition with a missing required field (e.g., null version), **When** the admin imports the config, **Then** the import is rejected with an error identifying the missing field.
3. **Given** a ZIP containing a deployment with a display name exceeding 255 characters, **When** the admin imports the config, **Then** the import is rejected with an error stating the size constraint violation.
4. **Given** a ZIP containing multiple entities where some are valid and some are invalid, **When** the admin imports the config, **Then** the import is rejected and the error response lists all validation violations across all entities (not just the first one found).

---

### User Story 2 - Valid imported config passes validation transparently (Priority: P1)

An administrator imports a valid ZIP configuration. Validation runs but finds no issues. The import proceeds exactly as it does today — no change in behavior for valid data.

**Why this priority**: Equal to P1 because validation must not break existing working imports. Backward compatibility with valid configurations is essential.

**Independent Test**: Can be tested by importing a previously exported valid configuration and verifying the import succeeds without errors.

**Acceptance Scenarios**:

1. **Given** a ZIP previously exported from the system, **When** the admin re-imports it, **Then** the import succeeds with no validation errors.
2. **Given** a ZIP with all fields populated within valid ranges and patterns, **When** the admin imports the config, **Then** the import completes and entities are persisted correctly.

---

### User Story 3 - Import preview also validates entities (Priority: P2)

An administrator previews a config import to assess conflicts. The preview also surfaces validation errors for invalid entities so the administrator can fix the config file before attempting the actual import.

**Why this priority**: Preview is a read-only safety net. Surfacing validation errors during preview avoids wasted time on imports that would fail anyway.

**Independent Test**: Can be tested by previewing a ZIP with invalid entities and verifying validation errors appear in the preview response.

**Acceptance Scenarios**:

1. **Given** a ZIP containing entities with validation errors, **When** the admin previews the import, **Then** the preview response includes validation errors alongside the conflict preview.

---

### User Story 4 - Maliciously injected system-managed fields are stripped (Priority: P1)

A malicious or misconfigured user crafts a ZIP containing fields that are normally excluded during export (e.g., `status`, `url`, `serviceName`, `author`, `buildStatus`, `imageName`). The system strips these fields before validation and persistence, ensuring system-managed values cannot be spoofed via import.

**Why this priority**: Without sanitization, a crafted ZIP can inject arbitrary values for system-managed fields (e.g., set `author` to impersonate another user, or set `status` to `DEPLOYED`). This is a data integrity and security concern.

**Independent Test**: Can be tested by importing a ZIP with injected system-managed fields and verifying they are not persisted.

**Acceptance Scenarios**:

1. **Given** a ZIP containing a deployment with an injected `status: DEPLOYED` field, **When** the admin imports the config, **Then** the deployment is created with `NOT_DEPLOYED` status (the injected value is ignored).
2. **Given** a ZIP containing a deployment with an injected `author` field, **When** the admin imports the config, **Then** the deployment's author is set from the authenticated user's identity (the injected value is stripped).
3. **Given** a ZIP containing an image definition with injected `buildStatus`, `imageName`, or `id` fields, **When** the admin imports the config, **Then** those fields are stripped and system-managed defaults are applied.

---

### Edge Cases

- What happens when a nested object (e.g., resources, scaling, probe properties) inside a deployment has invalid values? Validation must cascade into nested objects.
- What happens when the global domain whitelist entries contain invalid domains? Domain-specific validation rules must apply.
- What happens when an entity has multiple validation violations? All violations for that entity must be reported, not just the first.
- What happens when the same field constraint is violated on multiple entities in the bundle? Each entity's violations must be reported independently with the entity identifier.
- What happens when a ZIP contains fields excluded by export MixIns (e.g., `status`, `url`, `serviceName`, `author`, `buildStatus`, `imageName`, `k8sSecretName`)? Those fields must be stripped before validation and persistence.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST sanitize deserialized entities by stripping fields that are excluded during export (system-managed fields like status, url, serviceName, author, buildStatus, imageName, etc.) before validation and persistence.
- **FR-002**: System MUST validate all deserialized entities during config import before persisting any data.
- **FR-003**: System MUST apply the same validation rules to imported entities as are applied to entities received through the REST API (DTOs), including all custom validators (semantic version, docker image name, domain list, topics, resources, scaling, etc.).
- **FR-004**: System MUST validate nested objects within each entity (e.g., resources, scaling, probe properties, metadata, source, transport) — validation must cascade to the full object graph.
- **FR-005**: System MUST collect all validation violations across all entities in the import bundle and report them together, rather than failing on the first violation.
- **FR-006**: System MUST reject the entire import if any entity fails validation — no partial imports of valid entities when others are invalid.
- **FR-007**: Validation error responses MUST identify which entity failed (entity type and name/identifier) and which specific field constraints were violated.
- **FR-008**: System MUST also validate entities during import preview, surfacing validation errors in the preview response.
- **FR-009**: Validation MUST NOT alter the existing import behavior for valid configurations — the feature is purely additive.

### Key Entities

- **ExportConfig**: Root import structure containing maps of deployments, image definitions, and domain whitelist entries — all contents must be validated.
- **Deployment** (and subtypes: MCP, Adapter, Interceptor, NIM, Inference): Primary entities requiring validation of name patterns, size constraints, required fields, and nested object validity.
- **ImageDefinition** (and subtypes: MCP, Adapter, Interceptor): Entities requiring validation of version format, image names, size constraints, and nested objects.
- **Validation Error**: The structured result of failed validation, containing entity type, entity identifier, field path, and constraint violation message.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of validation rules enforced on REST API creation requests are also enforced on imported entities — no validation bypass is possible via import.
- **SC-002**: Valid configurations that were previously importable continue to import without errors.
- **SC-003**: When validation fails, the error response contains all violations (not just the first), enabling administrators to fix all issues in a single pass.
- **SC-004**: Import preview surfaces the same validation errors as the actual import operation.

## Assumptions

- Validation rules are considered the single source of truth as defined on the DTO layer (request DTOs and their custom validators). The import validation reuses these same rules rather than defining a separate set.
- The existing deserialization step (Jackson `readValue`) continues to handle structural/type errors; this feature adds constraint validation on top of successful deserialization.
- The import endpoint remains transactional — if validation fails, no database changes are committed.
