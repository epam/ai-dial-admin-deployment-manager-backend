# Export / Import

## Purpose
This spec describes configuration portability — exporting deployments, image definitions, and domain whitelists as a ZIP archive, and importing them into another environment. It enables environment promotion (e.g., dev → staging → production) and backup/restore workflows.

Status: **Implemented**

## Key Terms
- **ExportConfig**: The root export structure. Contains maps of all exportable components keyed by name.
- **ExportConfigComponentType**: The enum of exportable entity types: `MCP_IMAGE_DEFINITION`, `ADAPTER_IMAGE_DEFINITION`, `INTERCEPTOR_IMAGE_DEFINITION`, `MCP_DEPLOYMENT`, `ADAPTER_DEPLOYMENT`, `INTERCEPTOR_DEPLOYMENT`, `NIM_DEPLOYMENT`, `INFERENCE_DEPLOYMENT`.
- **ConflictResolutionPolicy**: Controls what happens when an imported entity already exists: `OVERWRITE` (replace) or `KEEP_EXISTING` (skip).
- **ExportSanitizer**: Service that removes or masks sensitive environment variable values before export, ensuring exported ZIPs are safe to share or commit.
- **Import order**: Image definitions are always imported before deployments to satisfy foreign-key constraints; global domain whitelist follows.
- **Dependency auto-inclusion**: When a deployment is selected for export, its referenced image definition is automatically included in the export bundle even if not explicitly selected.

## Requirements

### Requirement: Export selected components as a ZIP archive
The system SHALL accept an export request specifying which components to include, build the export bundle, and return it as a downloadable ZIP file.

Status: **Implemented**

#### Scenario: Export specific items
- **WHEN** `POST /api/v1/configs/export` is called with a `SelectedItemsExportRequestDto` listing specific component names and types
- **THEN** those components are serialized into the export bundle and returned as a ZIP file download

#### Scenario: Export with auto-included image definition
- **WHEN** an export request includes an image-based deployment (MCP, Adapter, Interceptor)
- **THEN** the referenced image definition is automatically added to the bundle, even if not explicitly listed

#### Scenario: Empty selection returns empty ZIP
- **WHEN** `POST /api/v1/configs/export` is called with an empty selection
- **THEN** an empty (or near-empty) ZIP is returned without error

### Requirement: Sensitive environment variables are sanitized before export
The system SHALL strip or mask sensitive environment variable values from all exported deployments so that the exported ZIP does not contain secrets.

Status: **Implemented**

#### Scenario: Sensitive env vars removed
- **WHEN** a deployment with `SECURE_CONTENT` or `SECURE_FILE` env vars is exported
- **THEN** the exported bundle contains the env var definition (name, mount type) but the sensitive value is removed or masked

#### Scenario: Plain env vars preserved
- **WHEN** a deployment with plain (`CONTENT`) env vars is exported
- **THEN** those env var values are preserved in the exported bundle

### Requirement: Import from ZIP with conflict resolution policy
The system SHALL accept a ZIP archive upload and import its contents into the environment, applying the specified conflict resolution policy when entities already exist.

Status: **Implemented**

#### Scenario: Import with OVERWRITE policy
- **WHEN** `POST /api/v1/configs/import` is called with a valid ZIP and `conflictResolutionPolicy=OVERWRITE`
- **THEN** any existing entity with the same name is replaced with the imported version

#### Scenario: Import with KEEP_EXISTING policy
- **WHEN** `POST /api/v1/configs/import` is called with a valid ZIP and `conflictResolutionPolicy=KEEP_EXISTING`
- **THEN** any existing entity with the same name is left unchanged; only new entities are created

#### Scenario: Import ordering: image definitions before deployments
- **WHEN** a ZIP contains both image definitions and deployments referencing them
- **THEN** image definitions are imported first, ensuring foreign-key constraints are satisfied when deployments are imported

### Requirement: All major component types are exportable and importable
The system SHALL support export and import of all five deployment types, all three image definition types, and the global domain whitelist.

Status: **Implemented**

#### Scenario: Image-based deployment types
- **WHEN** MCP, Adapter, or Interceptor deployments are included in an export/import
- **THEN** they are serialized/deserialized including their image definition reference and all configuration fields

#### Scenario: Model-source deployment types
- **WHEN** NIM or Inference deployments are included in an export/import
- **THEN** they are serialized/deserialized including their model source, scaling (Inference), and all configuration fields

#### Scenario: Global domain whitelist exported and imported
- **WHEN** the global domain whitelist is included in the export
- **THEN** it is serialized into the bundle and re-applied on import after deployments

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.web.controller.ConfigController` (path: `/api/v1/configs`)
- Export endpoint: `POST /api/v1/configs/export` — body: `ExportRequestDto`, response: `application/zip`
- Import endpoint: `POST /api/v1/configs/import` — multipart: ZIP file + `ConflictResolutionPolicy` parameter
- Transfer service: `com.epam.aidial.deployment.manager.service.config.ConfigTransferService`
- Exporter: `com.epam.aidial.deployment.manager.service.config.ConfigExporter`
- Importer: `com.epam.aidial.deployment.manager.service.config.ConfigImporter`
- Sanitizer: `com.epam.aidial.deployment.manager.service.config.ExportSanitizer`
- Component importers: `com.epam.aidial.deployment.manager.service.config.imports.*` (`DeploymentImporter`, `ImageDefinitionImporter`, `GlobalDomainWhitelistImporter`)
- Export model root: `com.epam.aidial.deployment.manager.model.config.ExportConfig`
- Component type enum: `com.epam.aidial.deployment.manager.model.config.ExportConfigComponentType`
- Conflict policy enum: `ConflictResolutionPolicy` (OVERWRITE, KEEP_EXISTING)
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.config.ExportRequestDto`
- Selected items DTO: `com.epam.aidial.deployment.manager.web.dto.config.SelectedItemsExportRequestDto`
- Component DTO: `com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentDto`
