# Feature Specification: NIM Served Model Name Override

**Feature Branch**: `013-nim-served-model-name`  
**Created**: 2026-04-08  
**Status**: Draft  
**Capability**: nim-deployments  
**Input**: User description: "NIM containers serve models under a name derived from the image. Users want to override the model name exposed via the NIM API (/v1/models, /v1/chat/completions). NVIDIA NIM supports the NIM_SERVED_MODEL_NAME environment variable for this."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Default Model Name Set to Deployment ID (Priority: P1)

An administrator creates a new NIM deployment without explicitly setting a served model name. The system automatically configures the NIM container to use the deployment's own identifier as the model name. This ensures a predictable, consistent model name that matches the deployment identity, rather than relying on the opaque name derived from the container image.

**Why this priority**: This is the primary use case. Most deployments should "just work" with a sensible default model name that matches the deployment identity, without requiring administrators to know about NIM-specific configuration.

**Independent Test**: Can be fully tested by creating a NIM deployment without any served model name override and verifying the generated container configuration includes the deployment identifier as the model name.

**Acceptance Scenarios**:

1. **Given** a NIM deployment request with no served model name in the environment variables, **When** the deployment is created, **Then** the system automatically sets the model name to the deployment identifier.
2. **Given** a NIM deployment request with no served model name in the environment variables, **When** the deployment details are retrieved, **Then** the automatically set model name is visible in the deployment's environment variable configuration.

---

### User Story 2 - Explicit Model Name Override (Priority: P2)

An administrator creates or updates a NIM deployment and explicitly provides a served model name through the environment variables. The system respects this explicit override and does not replace it with the default.

**Why this priority**: Some administrators need full control over the model name (e.g., to match an existing consumer contract or to use a human-friendly alias). This must work but is secondary to the default behavior.

**Independent Test**: Can be tested by creating a NIM deployment with an explicit served model name in the environment variables and verifying the system preserves the administrator's choice.

**Acceptance Scenarios**:

1. **Given** a NIM deployment request with an explicit served model name set in environment variables, **When** the deployment is created, **Then** the system preserves the administrator-provided name and does not override it.
2. **Given** a running NIM deployment with an explicit served model name, **When** the administrator updates the deployment and changes the served model name in environment variables, **Then** the deployment is reconfigured with the new name.
3. **Given** a running NIM deployment with an explicit served model name, **When** the administrator updates the deployment and removes the served model name from environment variables, **Then** the system falls back to using the deployment identifier as the model name.

---

### Edge Cases

- What happens when the administrator sets the served model name via both simple and sensitive environment variable types? The system should check both types for the presence of the override before injecting a default.
- What happens on update when the administrator previously had an explicit override and now submits without it? The system should re-apply the default (deployment identifier), consistent with create behavior.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When creating a NIM deployment, if no served model name is present in the provided environment variables, the system MUST automatically set the model name to the deployment identifier.
- **FR-002**: When creating a NIM deployment, if a served model name is already present in the provided environment variables, the system MUST preserve the administrator's value and NOT override it.
- **FR-003**: When updating a NIM deployment, the same default/override logic MUST apply: inject the default if absent, preserve if explicitly provided.
- **FR-004**: The system MUST check for the served model name in both simple and sensitive environment variable types before deciding to inject the default.
- **FR-005**: The automatically injected or explicitly provided served model name MUST be visible in the deployment's configuration when retrieved.
- **FR-006**: This behavior MUST only apply to NIM deployments; other deployment types are unaffected.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every newly created NIM deployment has a predictable model name — either the deployment identifier (default) or the administrator's explicit choice — with no reliance on the container image's internal naming.
- **SC-002**: Administrators who previously relied on generic environment variables to set the model name experience no behavioral change; their explicit values continue to be respected.
- **SC-003**: 100% of existing NIM deployments (created before this feature) continue to function without any behavioral change, since the default injection only applies at manifest generation time for new create/update operations.

## Assumptions

- The NIM runtime (NVIDIA NIM operator / NIMService CRD) correctly supports the `NIM_SERVED_MODEL_NAME` environment variable as documented by NVIDIA. The system passes the value through; runtime behavior is owned by NVIDIA.
- The approach mirrors the existing inference deployment pattern where `--model_name` is auto-injected into args if not explicitly provided, adapted for NIM's environment-variable-based mechanism.
- No new API fields, database columns, or migrations are required. The feature is implemented entirely at the manifest generation layer using the existing environment variable pass-through mechanism.
