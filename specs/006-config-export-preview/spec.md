# Feature Specification: Config Export Preview

**Feature Branch**: `006-config-export-preview`
**Created**: 2026-03-16
**Status**: Implemented
**Capability**: export-import
**Input**: User description: "Implement endpoint for config export preview. Request body should be the same as on export - ExportRequestDto. Response should be ExportConfigPreviewDto (new class) which has the following fields: List<String> globalImageBuildDomainWhitelist, List<ExportComponentInfoDto> imageDefinitions, List<ExportComponentInfoDto> deployments. New class ExportComponentInfoDto should have the following fields: String name, String displayName, String version, String description, ExportConfigComponentTypeDto type."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Preview Export Contents Before Downloading (Priority: P1)

An administrator wants to see a concise summary of exactly what would be included in a config export before committing to downloading the archive. They submit the same selection criteria they would use for export and receive a structured preview listing all entities that would be exported with their key identifying fields.

**Why this priority**: This is the core value of the feature — the entire feature exists to support this use case. Without it, no value is delivered.

**Independent Test**: Can be fully tested by sending a `POST /api/v1/configs/export/preview` request with a valid `ExportRequestDto` body and verifying that the response contains the correct `ExportConfigPreviewDto` structure with expected `ExportComponentInfoDto` entries.

**Acceptance Scenarios**:

1. **Given** one or more deployments exist, **When** `POST /api/v1/configs/export/preview` is called with a selection that includes specific deployments, **Then** the `deployments` list contains one `ExportComponentInfoDto` per resolved deployment with `id` set to the deployment ID, `displayName` to the deployment display name, `version` empty, `description` to the deployment description, and `type` reflecting the deployment type.

2. **Given** one or more image definitions exist, **When** `POST /api/v1/configs/export/preview` is called with a selection that includes specific image definitions, **Then** the `imageDefinitions` list contains one `ExportComponentInfoDto` per resolved image definition with `id` set to the image definition UUID, `displayName` to the image definition name, `version` to the image definition version, `description` to the image definition description, and `type` reflecting the image definition type.

3. **Given** the `addGlobalImageBuildDomainWhitelist` flag is `true` in the request, **When** `POST /api/v1/configs/export/preview` is called, **Then** the `globalImageBuildDomainWhitelist` field in the response contains the current whitelist entries.

4. **Given** the `addGlobalImageBuildDomainWhitelist` flag is `false`, **When** `POST /api/v1/configs/export/preview` is called, **Then** the `globalImageBuildDomainWhitelist` field in the response is empty.

5. **Given** a deployment references an image definition, **When** `POST /api/v1/configs/export/preview` is called with that deployment selected, **Then** the image definition is automatically included in the `imageDefinitions` list (consistent with auto-inclusion behavior of the export feature).

---

### User Story 2 - Preview Empty Selection (Priority: P2)

An administrator submits a preview request with an empty selection and receives an empty (but valid) response rather than an error.

**Why this priority**: Graceful handling of edge cases improves robustness, but this scenario has low business impact.

**Independent Test**: Send a `POST /api/v1/configs/export/preview` with an empty selection; verify the response has empty lists and HTTP 200.

**Acceptance Scenarios**:

1. **Given** an empty selection is provided, **When** `POST /api/v1/configs/export/preview` is called, **Then** the response returns HTTP 200 with `ExportConfigPreviewDto` containing empty lists for all fields.

---

### Edge Cases

- What happens when a referenced image definition no longer exists? The deployment is still included in `deployments`; the missing image definition is absent from `imageDefinitions`.
- What happens when the selection references entities that do not exist? Those entities are silently omitted from the preview response, consistent with existing export behavior.
- What happens when the request body omits the `addGlobalImageBuildDomainWhitelist` flag? Default applies: `false` — whitelist is not included.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose a `POST /api/v1/configs/export/preview` endpoint that accepts the same `ExportRequestDto` request body as the existing export endpoint.
- **FR-002**: The system MUST return an `ExportConfigPreviewDto` containing `List<String> globalImageBuildDomainWhitelist`, `List<ExportComponentInfoDto> imageDefinitions`, and `List<ExportComponentInfoDto> deployments`.
- **FR-003**: Each `ExportComponentInfoDto` MUST carry: `id` (string), `displayName` (string), `version` (string, empty for deployments), `description` (string), and `type` (`ExportConfigComponentTypeDto` enum value matching the entity type).
- **FR-004**: For image definitions, the `ExportComponentInfoDto` fields MUST be mapped as: `id → id`, `name → displayName`, `version → version`, `description → description`, image definition type → `type`.
- **FR-005**: For deployments, the `ExportComponentInfoDto` fields MUST be mapped as: `id → id`, `displayName → displayName`, `version` set to `null`, `description → description`, deployment type → `type`.
- **FR-006**: The system MUST apply the same auto-inclusion logic as export: when a deployment is included in the selection, its referenced image definition MUST be included in `imageDefinitions` even if not explicitly selected.
- **FR-007**: The system MUST populate `globalImageBuildDomainWhitelist` from the current global whitelist when `addGlobalImageBuildDomainWhitelist` is `true`; otherwise the field MUST be empty.
- **FR-008**: The system MUST return HTTP 200 with an `ExportConfigPreviewDto` (with empty lists) when an empty selection is provided.
- **FR-009**: The system MUST NOT perform any write, download, or archive operation — the preview endpoint is strictly read-only.

### Key Entities

- **ExportConfigPreviewDto**: New response DTO. Contains `List<String> globalImageBuildDomainWhitelist`, `List<ExportComponentInfoDto> imageDefinitions`, `List<ExportComponentInfoDto> deployments`.
- **ExportComponentInfoDto**: New DTO representing a single preview entry for either an image definition or a deployment. Fields: `String id`, `String displayName`, `String version`, `String description`, `ExportConfigComponentTypeDto type`.
- **ExportRequestDto**: Existing polymorphic request DTO (subtype `SelectedItemsExportRequestDto` for custom selections). Carries the `addGlobalImageBuildDomainWhitelist` flag (and `addSecrets`, which has no effect on the preview response).
- **ExportConfigComponentTypeDto**: Existing enum. Values: `MCP_IMAGE_DEFINITION`, `ADAPTER_IMAGE_DEFINITION`, `INTERCEPTOR_IMAGE_DEFINITION`, `MCP_DEPLOYMENT`, `ADAPTER_DEPLOYMENT`, `INTERCEPTOR_DEPLOYMENT`, `NIM_DEPLOYMENT`, `INFERENCE_DEPLOYMENT`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Administrators can preview the full content of a planned export without downloading any archive, completing the preview in under 3 seconds for typical selections (up to 100 entities).
- **SC-002**: Every entity that would appear in the export archive is represented in the preview response — no entity is omitted or added for the same request body.
- **SC-003**: The preview endpoint does not modify any persisted data; repeated calls with the same input always return the same result (idempotent, read-only).

## Clarifications

### Session 2026-03-16

- Q: Should the preview response use `DeploymentInfoDto` / `ImageDefinitionDto` (summary-level fields) or richer export-specific representations mirroring the ZIP archive contents? → A: Use a new lightweight `ExportComponentInfoDto` for both image definitions and deployments, carrying only: `id`, `displayName`, `version`, `description`, `type`.

## Assumptions

- The preview endpoint reuses existing export logic (entity resolution, auto-inclusion) without duplicating it.
- `ExportComponentInfoDto` is a new DTO used only for the preview response; it does not replace existing DTOs.
- The endpoint does not require pagination; all matching entities for the selection are returned in a single response.
- Authorization rules for the preview endpoint match those of the existing export endpoint.
