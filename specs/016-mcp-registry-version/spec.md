# Feature Specification: Add Version to McpRegistryRef

**Feature Branch**: `016-mcp-registry-version`  
**Created**: 2026-04-10  
**Status**: Draft  
**Input**: User description: "Add version field to externalImageRef for MCP registry, because some MCPs have multiple versions"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Specify a Version When Attaching an MCP Registry Reference (Priority: P1)

An operator creates or updates an image definition or deployment whose source carries an `externalRegistryRef` of type `McpRegistryRef`. Because the MCP registry hosts multiple versions of the same server package, the operator needs to record which specific version the source artifact corresponds to. The operator provides an optional `version` field alongside the existing `packageName`.

**Why this priority**: This is the core capability. Without it, `McpRegistryRef` can only point to a package but not to the exact version deployed, which is ambiguous when the registry lists dozens of versions.

**Independent Test**: Create an image definition with a Git dockerfile source that includes a `McpRegistryRef` with both `packageName` and `version`. Retrieve the image definition. Assert the response includes the `McpRegistryRef` with both fields populated.

**Acceptance Scenarios**:

1. **Given** an image definition with a general source, **When** the operator includes a `McpRegistryRef` with `packageName: "github/github"` and `version: "2025.4.1"`, **Then** the system persists both fields and returns them in subsequent reads.
2. **Given** a deployment with an `ImageReferenceSource`, **When** the operator includes a `McpRegistryRef` with `packageName: "my-mcp-server"` and `version: "1.0.0"`, **Then** both fields are persisted and returned correctly.
3. **Given** any source that supports `externalRegistryRef`, **When** the operator includes a `McpRegistryRef` with `packageName: "my-mcp-server"` and omits `version`, **Then** the system accepts the request — `version` is null/absent in the response, and the reference points to the package without specifying a version.
4. **Given** an existing image definition with a `McpRegistryRef` that has no version, **When** the operator updates it to include a `version`, **Then** subsequent reads return both `packageName` and the new `version`.

---

### User Story 2 - Read Version from Existing MCP Registry References (Priority: P1)

A client application fetches image definitions or deployments and reads the `McpRegistryRef` from their source. When a version is present, the client uses it to compose a direct link to the specific version page in the MCP registry (e.g., a "View version in registry" link). When absent, the client links to the package overview page.

**Why this priority**: The read path is equally critical — clients must be able to distinguish version-specific references from package-level references.

**Independent Test**: Seed records with `McpRegistryRef` references — some with `version`, some without. Call list and single-fetch endpoints. Assert the version field is present when set and absent when not set.

**Acceptance Scenarios**:

1. **Given** an image definition with a `McpRegistryRef` that has both `packageName` and `version`, **When** a client fetches it, **Then** the response includes `version` in the `externalRegistryRef` object.
2. **Given** a list of records, some with `version` and some without, **When** a client calls the list endpoint, **Then** each record correctly includes or omits `version`.
3. **Given** a legacy record created before this feature (no `version` stored), **When** it is fetched, **Then** `version` is absent from the response with no errors.

---

### User Story 3 - Clear or Update Version on an MCP Registry Reference (Priority: P2)

An operator needs to change the version of an existing `McpRegistryRef` (e.g., after upgrading the deployed MCP server to a newer registry version) or remove it to revert to a package-level reference.

**Why this priority**: Updates and removals are important for lifecycle management but build on the core write/read path.

**Independent Test**: Create a record with `McpRegistryRef` including a version. Update it with a different version. Assert the new version is returned. Update again omitting version. Assert version is absent.

**Acceptance Scenarios**:

1. **Given** an image definition with `McpRegistryRef { packageName: "my-server", version: "1.0.0" }`, **When** the operator updates `version` to `"2.0.0"`, **Then** subsequent reads return `version: "2.0.0"`.
2. **Given** an image definition with `McpRegistryRef { packageName: "my-server", version: "1.0.0" }`, **When** the operator sends an update with `McpRegistryRef { packageName: "my-server" }` (no version), **Then** `version` is cleared and absent in subsequent reads.

---

### Edge Cases

- What happens when `version` is an empty string? The system must reject it with a validation error — if provided, `version` must be non-blank. Omitting it entirely (null) is valid.
- What happens when a legacy `McpRegistryRef` record (no version column/field) is read after the migration? The `version` field is absent/null in the response with no errors or data corruption.
- What happens during export/import of a record with a versioned `McpRegistryRef`? The `version` field is included in the export payload and restored on import, consistent with existing `externalRegistryRef` export behavior.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The `McpRegistryRef` type MUST support an optional `version` field (String) alongside the existing `packageName` field.
- **FR-002**: The `version` field MUST be optional. Omitting it is valid and indicates a package-level reference without a specific version.
- **FR-003**: When `version` is provided, it MUST be a non-blank string. The system MUST reject requests where `version` is an empty or whitespace-only string.
- **FR-004**: The `version` field MUST be persisted to the database and survive application restarts.
- **FR-005**: The `version` field MUST be returned in all API responses that include `McpRegistryRef` (GET single, GET list, POST and PUT responses), when present.
- **FR-006**: The `version` field MUST be included in export payloads and restored on import, consistent with existing `externalRegistryRef` export/import behavior.
- **FR-007**: Legacy records created before this feature (with no `version` stored) MUST continue to work — the `version` field is absent/null in their API responses, with no errors.
- **FR-008**: The `version` field MUST NOT influence deployment behavior, image build pipelines, or any functional system logic. It remains purely informational metadata, consistent with the overall `externalRegistryRef` design.

### Key Entities

- **McpRegistryRef** (existing, extended): Field `packageName` (String, non-blank, required) — identifies an MCP registry package. New field `version` (String, non-blank when provided, optional) — identifies a specific published version of the package.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can specify a version when attaching an `McpRegistryRef` in a single API call with no additional steps.
- **SC-002**: 100% of API responses for records carrying a versioned `McpRegistryRef` include the `version` field alongside `packageName`.
- **SC-003**: All existing records without a `version` continue to function identically — no migration errors, no broken reads.
- **SC-004**: The feature introduces no regression in existing `externalRegistryRef` CRUD or export/import scenarios, as verified by the full test suite.

## Assumptions

- The `version` field is a free-form non-blank string when provided. No semantic versioning format is enforced — the system stores whatever version identifier the MCP registry uses. Clients are responsible for interpreting version strings.
- The `version` field is stored as part of the existing JSON persistence mechanism for `externalRegistryRef` (no new database column needed for the ref itself, though migration may be needed if the ref is stored in a structured column).
- Only `McpRegistryRef` is extended in this feature. Other `ExternalRegistryRef` subtypes (`GitHubRef`, `GenericRef`) are not affected.
- The version field does not introduce any cross-validation with the MCP registry service — the system does not verify that the version exists in the registry.
