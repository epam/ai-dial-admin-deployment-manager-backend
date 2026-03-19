# Feature Specification: Config Import Preview

**Feature Branch**: `007-config-import-preview`
**Created**: 2026-03-16
**Status**: Draft
**Input**: User description: "Implement preview for config import. New endpoint POST /api/v1/configs/import/preview — same inputs as /import (multipart/form-data: file + resolutionPolicy), returns ImportConfigPreviewDto."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Preview Import Outcome Before Committing (Priority: P1)

An administrator has a config archive they intend to import. Before importing, they want to see exactly what would happen to each entity — whether it would be created, updated, skipped, or cause a failure — given the chosen conflict resolution policy. They submit the same ZIP file and `resolutionPolicy` as they would for a real import and receive a structured preview showing the before/after state for every entity.

**Why this priority**: This is the entire purpose of the feature. Every other scenario depends on this being correct.

**Independent Test**: Can be fully tested by sending a `POST /api/v1/configs/import/preview` request with a valid ZIP file and a `resolutionPolicy` and verifying that the response lists each entity with the expected action, `prev` (existing state), and `next` (incoming state).

**Acceptance Scenarios**:

1. **Given** a ZIP file containing entities that do not exist in the current environment, **When** `POST /api/v1/configs/import/preview` is called with any `resolutionPolicy`, **Then** each entity in the response carries `importAction = CREATE`, `prev = null`, and `next = <incoming entity>`.

2. **Given** a ZIP file containing entities that already exist and `resolutionPolicy = OVERWRITE`, **When** `POST /api/v1/configs/import/preview` is called, **Then** each conflicting entity carries `importAction = UPDATE`, `prev = <existing entity>`, and `next = <incoming entity>`.

3. **Given** a ZIP file containing entities that already exist and `resolutionPolicy = SKIP_IF_EXISTS`, **When** `POST /api/v1/configs/import/preview` is called, **Then** each conflicting entity carries `importAction = SKIP`, `prev = <existing entity>`, and `next = null`.

4. **Given** a ZIP file containing entities that already exist and `resolutionPolicy = FAIL_IF_EXISTS`, **When** `POST /api/v1/configs/import/preview` is called, **Then** each conflicting entity carries `importAction = FAIL`, `prev = <existing entity>`, and `next = <incoming entity>`.

5. **Given** the ZIP file includes a `globalImageBuildDomainWhitelist` section, **When** `POST /api/v1/configs/import/preview` is called, **Then** the response contains a single `ImportComponentDto<List<String>>` for the whitelist as a whole, with `prev` = current whitelist and `next` = incoming whitelist (or `null` for SKIP), applying the same conflict resolution semantics as other entities.

---

### User Story 2 - Preview Covers All Eight Entity Types (Priority: P1)

An administrator imports a ZIP that contains a mix of entity types (MCP image definitions, adapter image definitions, interceptor image definitions, MCP deployments, adapter deployments, interceptor deployments, NIM deployments, inference deployments) and expects the preview to report actions for each type independently.

**Why this priority**: The import touches all eight entity types; missing any would make the preview incomplete and misleading.

**Independent Test**: Send a ZIP containing at least one entity of each type; verify the preview response has entries in each corresponding typed list.

**Acceptance Scenarios**:

1. **Given** a ZIP containing entities from multiple types, **When** `POST /api/v1/configs/import/preview` is called, **Then** the response contains a non-empty list for each entity type present in the ZIP, with correct `importAction` values per type.

2. **Given** a ZIP containing only deployment entries (no image definitions), **When** `POST /api/v1/configs/import/preview` is called, **Then** image definition lists in the response are empty, and deployment lists reflect the correct actions.

---

### User Story 3 - No Side Effects from Preview (Priority: P1)

An administrator calls the preview endpoint, then calls it again with the same inputs. The state of the system is identical between calls, and any subsequent real import is unaffected by the preview calls.

**Why this priority**: A preview that mutates data is not a preview — it defeats the purpose and could corrupt the environment.

**Independent Test**: Call the preview endpoint twice with the same input; verify that a subsequent `/import` produces exactly the results predicted by the preview.

**Acceptance Scenarios**:

1. **Given** any valid preview request, **When** the endpoint is called multiple times with the same inputs, **Then** no entities are created, updated, or deleted in the database, and responses are identical.

---

### User Story 4 - Invalid or Malformed ZIP Is Rejected (Priority: P2)

An administrator accidentally uploads a non-ZIP file or a ZIP that lacks the expected config file. The preview endpoint reports the error rather than returning an empty preview.

**Why this priority**: Same validation as `/import`; a silently empty response would mislead the user into thinking the ZIP was valid.

**Independent Test**: Upload a plain text file as the ZIP; verify the response is a 4xx error with a meaningful message.

**Acceptance Scenarios**:

1. **Given** a file that is not a valid ZIP archive, **When** `POST /api/v1/configs/import/preview` is called, **Then** the response returns a 4xx error with a message indicating the file is invalid.

2. **Given** a valid ZIP that does not contain the expected config file, **When** `POST /api/v1/configs/import/preview` is called, **Then** the response returns a 4xx error indicating no valid config was found.

---

### Edge Cases

- What happens when the ZIP contains an entity type that has no entries in the current database? All entities of that type yield `importAction = CREATE`.
- What happens when the `resolutionPolicy` parameter is missing? The request is rejected with a 4xx error (required parameter, same as `/import`).
- What happens when the ZIP's `globalImageBuildDomainWhitelist` is empty or absent? The whitelist `ImportComponentDto` is absent from the response (no component emitted for an empty/absent list).
- What happens when the same entity key appears multiple times in the ZIP? Behaviour mirrors real import deserialisation — last occurrence wins (consistent with `ExportConfig` Jackson deserialisation).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose a `POST /api/v1/configs/import/preview` endpoint accepting `multipart/form-data` with the same `file` and `resolutionPolicy` parameters as the existing `/api/v1/configs/import` endpoint.
- **FR-002**: The endpoint MUST return `ImportConfigPreviewDto` containing typed lists of `ImportComponentDto<T>` for all eight entity types (three image-definition types, five deployment types) plus a single `ImportComponentDto<List<String>>` for the global domain whitelist.
- **FR-003**: Each `ImportComponentDto<T>` MUST carry `importAction` (one of `CREATE`, `UPDATE`, `SKIP`, `FAIL`), `prev` (the existing value or `null`), and `next` (the incoming value or `null`).
- **FR-004**: The system MUST apply the following action semantics per `ConflictResolutionPolicy`:
  - Entity does not exist in DB, any policy → `CREATE` (`prev = null`, `next = incoming entity`)
  - Entity exists + `FAIL_IF_EXISTS` → `FAIL` (`prev = existing`, `next = incoming`)
  - Entity exists + `SKIP_IF_EXISTS` → `SKIP` (`prev = existing`, `next = null`)
  - Entity exists + `OVERWRITE` → `UPDATE` (`prev = existing`, `next = incoming`)
- **FR-005**: The preview endpoint MUST NOT write, update, or delete any data; it MUST be strictly read-only.
- **FR-006**: The system MUST apply the same ZIP parsing and validation logic as the real `/import` endpoint — same config file name lookup, same duplicate-entry detection, same error responses for malformed input.
- **FR-007**: The system MUST process all eight entity-type maps from the parsed config (MCP/adapter/interceptor image definitions; MCP/adapter/interceptor/NIM/inference deployments).
- **FR-008**: The system MUST treat the `globalImageBuildDomainWhitelist` as a single unit: one `ImportComponentDto<List<String>>` where `prev` is the current full whitelist and `next` is the merged whitelist (existing entries preserved, incoming entries appended; deduplicated). The `OVERWRITE` policy merges rather than replaces — existing domains are never removed by import. No component is emitted when the incoming list is empty or absent.
- **FR-009**: The `ImportConfigPreviewDto` collections MUST be typed with existing web-layer DTOs (e.g., `List<ImportComponentDto<McpImageDefinitionDto>>`) — the `prev` and `next` fields in each `ImportComponentDto` are mapped domain model instances, not raw domain objects. All mapping MUST go through existing DTO mappers.
- **FR-010**: The endpoint MUST return HTTP 200 with all lists empty when the ZIP contains no entities, or with accurate `ImportComponentDto` entries for each entity present in the ZIP.

### Key Entities

- **ImportAction**: New enum representing the predicted outcome for a single entity: `CREATE`, `UPDATE`, `SKIP`, `FAIL`.
- **ImportComponent\<T\>**: New generic wrapper pairing an `ImportAction` with `prev` (existing T or null) and `next` (incoming T or null).
- **ImportConfigPreview**: New aggregate model containing per-type lists of `ImportComponent<>` for all eight entity types plus a single `ImportComponent<List<String>>` for the global domain whitelist.
- **ImportActionDto**: New DTO enum mirroring `ImportAction`, used in the API response.
- **ImportComponentDto\<T\>**: New generic DTO record with `importAction`, `prev`, and `next`.
- **ImportConfigPreviewDto**: New response DTO record containing typed `ImportComponentDto<T>` collections for all entity types (where `T` is the corresponding web-layer DTO, e.g., `McpImageDefinitionDto`, `McpDeploymentDto`) and a single `ImportComponentDto<List<String>>` for the whitelist.
- **ConfigImportPreviewer**: New read-only service that orchestrates per-type previewers to produce an `ImportConfigPreview` without writing to the database.
- **ImportConfigDtoMapper**: New mapper converting `ImportConfigPreview` → `ImportConfigPreviewDto`, mapping domain model `prev`/`next` values to their corresponding web-layer DTOs via existing DTO mappers.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Administrators can determine the exact outcome of an import (per entity: create, update, skip, or fail) without triggering any data change, completing the preview in under 3 seconds for ZIPs containing up to 200 entities.
- **SC-002**: Every entity present in the ZIP is represented in the preview response — no entity is silently omitted unless the ZIP itself is malformed.
- **SC-003**: Calling the preview endpoint any number of times with the same input produces identical responses and leaves the database state unchanged (fully idempotent, read-only).
- **SC-004**: The predicted action from the preview matches the actual outcome of a subsequent real import with the same file and `resolutionPolicy` in 100% of cases (preview accuracy), assuming no other process modifies data between the two calls.

## Clarifications

### Session 2026-03-16

- Q: Should the `globalImageBuildDomainWhitelist` preview be a single `ImportComponentDto<List<String>>` for the entire list, or per-entry `ImportComponentDto<String>` for each domain string? → A: Single `ImportComponentDto<List<String>>` for the whole list (prev = current list, next = incoming list).

## Known Limitations

### `next` value is an approximation of the actual import result

The `next` field in each `ImportComponentDto` represents the raw deserialized entity from the export ZIP, not the exact entity that would be persisted after a real import. Several fields are excluded by Jackson export mixins (`ImageDefinitionExportMixIn`, `DeploymentExportMixIn`, `InternalImageSourceExportMixIn`, `SensitiveEnvVarExportMixIn`) and will appear as `null` in `next`, whereas the real import pipeline populates them with server-derived values.

**Image definitions** — fields `null` in preview but set during real import:
- `id`, `createdAt`, `updatedAt` — assigned by DB.
- `buildStatus` — set to `NOT_BUILT` on create.
- `author` — set to current user on create; preserved from existing entity on update (via `mergeForOverwrite`).
- `imageName`, `buildLogs`, `builtAt` — remain `null` (same as preview) on create; retain existing DB values on update.

**Deployments** — fields `null` in preview but set during real import:
- `status` — set to `NOT_DEPLOYED` on create; derived from existing status on update (e.g. `STOPPED` → `NOT_DEPLOYED`).
- `author` — set to current user on create.
- `url`, `serviceName` — preserved from existing entity on update.
- `source.imageDefinitionId` (for `InternalImageSource`) — resolved from DB by (type, name, version).
- `k8sSecretName`, `k8sSecretKey` on sensitive env vars — newly provisioned K8s secrets during real import.
- Real import may also trigger side effects (K8s rolling update, Cilium policy changes) that the preview does not predict.

**Deployment env var values in preview:**
- `prev`: K8s secrets are resolved (`resolveSecrets=true`), so `metadata.envs[*].value` includes both plain and sensitive env var values for comparison.
- `next`: env var values are sourced from `metadata.envs[*].value` in the deserialized export (via `DeploymentDtoMapper` fallback). Sensitive values will be `null` if the export was created with `addSecrets=false`.

This is accepted behaviour: the preview is intended to show the *incoming data and conflict resolution action*, not to fully simulate the import pipeline.

## Assumptions

- The preview endpoint reuses the same ZIP-parsing and config deserialisation logic as the real `/import` endpoint; no duplication.
- Error handling (invalid ZIP, missing config file, unknown policy) matches `/import` behaviour exactly — same HTTP status codes and error messages.
- The `prev` value reflects the entity state at the moment the preview is called; if the entity changes between preview and import, the real import outcome may differ (TOCTOU is accepted behaviour).
- Authorization rules for the preview endpoint match those of the existing `/import` endpoint.
- The endpoint does not require pagination; the full preview for all entities in the ZIP is returned in a single response.
- No database migrations are required; this feature is read-only.
