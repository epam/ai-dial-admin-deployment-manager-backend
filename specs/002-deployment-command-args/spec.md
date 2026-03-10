# Feature Specification: Support Command and Args for All Deployment Types

**Feature Branch**: `002-deployment-command-args`
**Created**: 2026-03-09
**Status**: Draft
**Input**: User description: "Support `command` and `args` configuration for all deployment types"
**Source Issue**: [#204](https://github.com/epam/ai-dial-admin-deployment-manager-backend/issues/204)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure Custom Command for MCP Deployment (Priority: P1)

As a platform operator, I want to specify a custom startup command and arguments when creating or updating an MCP deployment, so that I can override the container's default entrypoint to run the MCP server with specific flags or configuration paths.

**Why this priority**: MCP is the newest and most actively developed deployment type, and custom commands are essential for running MCP servers with different transports, endpoint paths, or runtime modes.

**Independent Test**: Can be fully tested by creating an MCP deployment with a custom command/args via the API and verifying the resulting Knative service manifest contains the specified command and arguments.

**Acceptance Scenarios**:

1. **Given** I am creating a new MCP deployment, **When** I provide `command` and `args` fields in the creation request, **Then** the deployment is created successfully and the container specification includes the specified command and arguments.
2. **Given** I am creating a new MCP deployment, **When** I omit the `command` and `args` fields, **Then** the deployment is created successfully using the container image's default entrypoint (no command/args override).
3. **Given** an existing MCP deployment without custom command/args, **When** I update it to add a command and args, **Then** the deployment is updated and the container specification reflects the new command and arguments.
4. **Given** an existing MCP deployment with custom command/args, **When** I update it to remove the command and args (by setting them to null/empty), **Then** the deployment reverts to using the container image's default entrypoint.

---

### User Story 2 - Configure Custom Command for Adapter Deployment (Priority: P2)

As a platform operator, I want to specify a custom startup command and arguments when creating or updating an Adapter deployment, so that I can customize how the adapter container starts (e.g., passing configuration flags or mode switches).

**Why this priority**: Adapters are commonly used and may need custom startup parameters for different backend integrations.

**Independent Test**: Can be fully tested by creating an Adapter deployment with custom command/args via the API and verifying the Knative service manifest contains the specified values.

**Acceptance Scenarios**:

1. **Given** I am creating a new Adapter deployment, **When** I provide `command` and `args` fields in the creation request, **Then** the deployment is created successfully and the container specification includes the specified command and arguments.
2. **Given** I am creating a new Adapter deployment, **When** I omit the `command` and `args` fields, **Then** the deployment is created successfully using the container image's default entrypoint (no command/args override).
3. **Given** an existing Adapter deployment without custom command/args, **When** I update it to add a command and args, **Then** the deployment is updated and the container specification reflects the new command and arguments.
4. **Given** an existing Adapter deployment with custom command/args, **When** I update it to remove the command and args (by setting them to null/empty), **Then** the deployment reverts to using the container image's default entrypoint.

---

### User Story 3 - Configure Custom Command for Interceptor Deployment (Priority: P3)

As a platform operator, I want to specify a custom startup command and arguments when creating or updating an Interceptor deployment, so that I can control how the interceptor starts up.

**Why this priority**: Interceptors follow the same Knative pattern and should have feature parity with other deployment types.

**Independent Test**: Can be fully tested by creating an Interceptor deployment with custom command/args via the API and verifying the Knative service manifest contains the specified values.

**Acceptance Scenarios**:

1. **Given** I am creating a new Interceptor deployment, **When** I provide `command` and `args` fields in the creation request, **Then** the deployment is created successfully and the container specification includes the specified command and arguments.
2. **Given** I am creating a new Interceptor deployment, **When** I omit the `command` and `args` fields, **Then** the deployment is created successfully using the container image's default entrypoint (no command/args override).
3. **Given** an existing Interceptor deployment without custom command/args, **When** I update it to add a command and args, **Then** the deployment is updated and the container specification reflects the new command and arguments.
4. **Given** an existing Interceptor deployment with custom command/args, **When** I update it to remove the command and args (by setting them to null/empty), **Then** the deployment reverts to using the container image's default entrypoint.

---

### User Story 4 - Configure Custom Command for NIM Deployment (Priority: P3)

As a platform operator, I want to specify a custom startup command and arguments when creating or updating a NIM deployment, so that I can override the container's default entrypoint with custom flags or configuration.

**Why this priority**: NIM deployments are container-based and benefit from the same command/args flexibility as other types. Since command/args are now at the base level, NIM support comes naturally.

**Independent Test**: Can be fully tested by creating a NIM deployment with custom command/args via the API and verifying the resulting manifest contains the specified values.

**Acceptance Scenarios**:

1. **Given** I am creating a new NIM deployment, **When** I provide `command` and `args` fields in the creation request, **Then** the deployment is created successfully and the container specification includes the specified command and arguments.
2. **Given** I am creating a new NIM deployment, **When** I omit the `command` and `args` fields, **Then** the deployment is created successfully using the container image's default entrypoint (no command/args override).
3. **Given** an existing NIM deployment without custom command/args, **When** I update it to add a command and args, **Then** the deployment is updated and the container specification reflects the new command and arguments.
4. **Given** an existing NIM deployment with custom command/args, **When** I update it to remove the command and args (by setting them to null/empty), **Then** the deployment reverts to using the container image's default entrypoint.

---

### User Story 5 - Retrieve Deployment with Command and Args (Priority: P1)

As a platform operator, I want to retrieve any deployment and see its configured command and args in the response, so that I can verify and audit the container startup configuration.

**Why this priority**: Read-back is essential for verifying deployments are configured correctly and is required for all CRUD operations to be complete.

**Independent Test**: Can be fully tested by creating a deployment with command/args, retrieving it via GET, and verifying the response includes the command and args values.

**Acceptance Scenarios**:

1. **Given** a deployment exists with custom command and args, **When** I retrieve it via the API, **Then** the response includes the `command` and `args` fields with their configured values.
2. **Given** a deployment exists without custom command/args, **When** I retrieve it via the API, **Then** the `command` and `args` fields are absent or null in the response.

---

### Edge Cases

- What happens when `command` is provided but `args` is empty? The container should start with only the command and no arguments.
- What happens when `args` is provided but `command` is empty? The args should be passed to the container image's default entrypoint.
- What happens when `command` or `args` contain special characters (quotes, spaces, shell metacharacters)? The system should properly parse and preserve them using the existing shell-like parsing logic.
- What happens when an existing Inference deployment's command/args behavior is tested after the change? It should continue to work identically, with no behavioral regression.
- What happens when command/args values are provided for a deployment that is subsequently updated without command/args fields? The previously set command/args should be cleared (set to null), consistent with the full-replace update semantics used across all deployment types.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST accept optional `command` and `args` string fields in the creation and update requests for all deployment types, exposed at the base API level.
- **FR-002**: System MUST parse the `command` string input into a list of tokens using the existing shell-like parsing logic (handling quoted strings, spaces, and special characters).
- **FR-003**: System MUST parse the `args` string input into a list of tokens using the same parsing logic as `command`.
- **FR-004**: System MUST apply the parsed `command` list to the container specification in the generated Knative service manifest when provided.
- **FR-005**: System MUST apply the parsed `args` list to the container specification in the generated Knative service manifest when provided.
- **FR-006**: System MUST persist `command` and `args` for all deployment types so they survive application restarts.
- **FR-007**: System MUST return the `command` and `args` values (as strings) in deployment retrieval responses for all deployment types that support them.
- **FR-008**: System MUST allow `command` and `args` to be updated (set, changed, or cleared) on existing deployments.
- **FR-009**: System MUST NOT change the existing behavior of `command` and `args` for Inference deployments (backward compatibility).
- **FR-010**: System MUST return a validation error if `command` or `args` string values cannot be parsed (e.g., unmatched quotes).
- **FR-011**: System MUST migrate existing Inference deployment command/args data from the Inference-specific storage to the base Deployment storage, preserving all existing values without data loss.
- **FR-012**: System MUST pass command/args to the container specification exactly as provided for MCP, Adapter, Interceptor, and NIM deployments, with no automatic argument injection or modification.
- **FR-013**: System MUST remove command/args fields from the Inference-specific subtype (DTOs, domain models, entities) after consolidation to the base level, eliminating redundancy.

### Key Entities

- **Deployment**: The base entity representing any managed deployment. Will gain `command` (list of strings, nullable) and `args` (list of strings, nullable) fields at the base level, shared across all deployment types.
- **Knative Service Manifest**: The generated Kubernetes resource specification. The container spec within it will include `command` and `args` arrays when provided by the deployment configuration.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Operators can create MCP, Adapter, Interceptor, and NIM deployments with custom command and args, and the resulting containers start with the specified entrypoint and arguments.
- **SC-002**: All existing Inference deployment command/args functionality continues to work without any changes in behavior.
- **SC-003**: Deployments created before this feature (without command/args) continue to function correctly after the database migration.
- **SC-004**: The API input format for command and args is consistent across all deployment types that support it (same string-based input with shell-like parsing).
- **SC-005**: Round-trip consistency: creating a deployment with specific command/args and retrieving it returns equivalent values.

## Clarifications

### Session 2026-03-10

- Q: Should existing Inference command/args be migrated from the Inference table to the base Deployment table, or coexist separately? → A: Consolidate — migrate Inference command/args data to the base Deployment table and remove the columns from the Inference-specific table.
- Q: When updating a deployment and omitting command/args fields, should they be cleared or left unchanged? → A: Full-replace — omitting command/args in an update clears them (consistent with existing PUT semantics).
- Q: Should the Knative manifest generator for MCP/Adapter/Interceptor have auto-injection behavior (like Inference's --model_name)? → A: No — exact pass-through only. The --model_name auto-injection is valid only for Inference deployments.
- Q: At which DTO level should command/args be exposed in the API? → A: Base DTO level — all deployment types see command/args, aligning with base-level DB consolidation.
- Q: Should Inference-specific command/args fields be removed from the Inference subtype classes? → A: Yes — remove them. Use base-level fields only to avoid redundancy and mapping complexity.

## Assumptions

- The `command` and `args` fields will be consolidated at the base deployment level (shared by all Knative types). Existing Inference-specific command/args data will be migrated to the base table and the Inference-specific columns will be removed.
- The existing `CommandLineUtils.parseCommandline()` logic is sufficient for parsing command and args strings for all deployment types.
- The string-to-list conversion approach (accepting a single string in the API and parsing it into a list) will be reused from the Inference deployment pattern for consistency.
- No breaking changes to the existing API contract for Inference deployments.
