# Image Definitions

## Purpose
This spec describes the ImageDefinition entity — the configuration record for a container image that can be built and deployed. ImageDefinitions have three concrete subtypes (MCP, Interceptor, Adapter), each with its own spec.

Status: **Implemented**

## Key Terms
- **ImageDefinition**: A persistent record that defines a container image — its source, version, metadata, and build configuration. Identified by a UUID.
- **Image source**: Either a pre-built Docker image reference (`DockerImageSourceDto`) or a Git repository with a Dockerfile (`GitDockerfileImageSourceDto`).
- **`$type` discriminator**: The Jackson polymorphism field (`"mcp"` | `"adapter"` | `"interceptor"`) that determines which subtype is created or returned.
- **Build status**: The latest build outcome for a given image definition version (`buildStatus` field).
- **Semantic version**: The `version` field must follow semantic versioning (e.g., `1.0.0`).
- **Grouped view**: An `ImageDefinitionViewDto` that groups all versions of the same image definition name under a single entry, with a `selectedId` and an `availableVersions` list.

## Base Fields

All image definition subtypes carry these fields:

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID | Yes (response only) | Generated on creation |
| `name` | String | Yes | Human-readable identifier |
| `version` | String | Yes | Must be a valid semantic version (`@ValidSemanticVersion`) |
| `description` | String | No | |
| `source` | ImageSourceDto | Yes | DockerImageSource or GitDockerfileImageSource |
| `license` | String | No | |
| `createdAt` | Instant | Yes (response) | Set on creation |
| `updatedAt` | Instant | Yes (response) | Updated on modification |
| `topics` | List\<String\> | No (request) / Yes (response) | Free-form topic labels. Nullable on input; always present (possibly empty) in responses. Each topic: non-blank, ≤255 chars, no leading/trailing whitespace. Values are not from a fixed enum — the topics endpoint returns the distinct union across all image definitions. |
| `buildStatus` | ImageStatusDto | No | Latest build status, null if never built. Values: NOT_BUILT, BUILDING, BUILD_FAILED, BUILD_SUCCESSFUL |
| `author` | String | No | |
| `allowedDomains` | List\<String\> | No (request) / Yes (response) | Per-definition domains allowed during image build (build-time network access). Nullable on input; always present (possibly empty) in responses. See `domain-whitelist` spec for the full domain access control layering. |
| `imageBuilder` | ImageBuilderDto | No | Override the build engine: BUILDKIT or BUILDKIT_ROOTLESS |

## Requirements

### Requirement: List image definitions
The system SHALL return all image definitions, optionally filtered by subtype.

Status: **Implemented**

#### Scenario: List all
- **WHEN** `GET /api/v1/images/definitions` is called without filters
- **THEN** all image definitions of all types are returned

#### Scenario: Filter by type
- **WHEN** `GET /api/v1/images/definitions?type=MCP` is called
- **THEN** only image definitions with `$type: mcp` are returned

### Requirement: Get image definition by ID
The system SHALL return a single image definition including all subtype-specific fields.

Status: **Implemented**

#### Scenario: Existing definition
- **WHEN** `GET /api/v1/images/definitions/{id}` is called
- **THEN** the full image definition is returned including all base and subtype fields

#### Scenario: Non-existent ID
- **WHEN** `GET /api/v1/images/definitions/{id}` is called with an unknown ID
- **THEN** the system responds with 404

### Requirement: Create image definition
The system SHALL create a new image definition of the subtype indicated by `$type`.

Status: **Implemented**

#### Scenario: Successful creation
- **WHEN** `POST /api/v1/images/definitions` is called with a valid body including `$type`, `name`, `version`, and `source`
- **THEN** a new image definition is persisted and returned with a generated `id`, `createdAt`, and `updatedAt`

#### Scenario: Missing required fields
- **WHEN** `POST /api/v1/images/definitions` is called without `name`, `version`, or `source`
- **THEN** the system responds with 400

#### Scenario: Invalid semantic version
- **WHEN** `POST /api/v1/images/definitions` is called with a `version` that is not a valid semantic version
- **THEN** the system responds with 400

### Requirement: Update image definition
The system SHALL update an existing image definition by ID.

Status: **Implemented**

#### Scenario: Successful update
- **WHEN** `PUT /api/v1/images/definitions/{id}` is called with a valid body
- **THEN** the image definition is updated and returned with a new `updatedAt` timestamp

#### Scenario: Non-existent ID
- **WHEN** `PUT /api/v1/images/definitions/{id}` is called with an unknown ID
- **THEN** the system responds with 404

### Requirement: Delete image definition
The system SHALL delete an image definition asynchronously via the component cleanup service. The response is HTTP 204 (accepted for deletion). Deletion cascades to all dependent resources.

**Cascade chain:** Image definition deletion triggers async cleanup of all deployments referencing this image definition. Each cascaded deployment is undeployed from Kubernetes and then deleted from the database. Build-related Kubernetes resources are also cleaned up.

Status: **Implemented**

#### Scenario: Successful deletion
- **WHEN** `DELETE /api/v1/images/definitions/{id}` is called
- **THEN** the deletion is initiated asynchronously and the system returns HTTP 204

#### Scenario: Cascade to dependent deployments
- **WHEN** an image definition is deleted that has deployments referencing it
- **THEN** all deployments with that `imageDefinitionId` are queued for async deletion (undeploy + DB removal)

#### Scenario: Cascade to Kubernetes resources
- **WHEN** a cascaded deployment deletion runs
- **THEN** the deployment is undeployed from Kubernetes, K8s resources are cleaned up, and the deployment record is deleted from the database

### Requirement: Get grouped image definition views
The system SHALL return image definitions organized by name, grouping all versions of the same name into a single `ImageDefinitionViewDto` entry. An optional `type` filter narrows results.

Status: **Implemented**

#### Scenario: Grouped view returned
- **WHEN** `GET /api/v1/images/definitions/grouped` is called
- **THEN** image definitions are returned grouped by name; each group includes `name`, `selectedId`, and `availableVersions` (list of `ImageDefinitionViewElementDto`)

#### Scenario: Grouped view filtered by type
- **WHEN** `GET /api/v1/images/definitions/grouped?type=MCP` is called
- **THEN** only groups for MCP image definitions are returned

### Requirement: Get image versions with statuses by name
The system SHALL return all versions of an image definition matching a given name, including their build statuses. An optional `type` filter is supported.

Status: **Implemented**

#### Scenario: List versions
- **WHEN** `GET /api/v1/images/definitions/{name}/versions` is called
- **THEN** all versions for the given name are returned; each `ImageDefinitionViewElementDto` includes `id`, `version`, `status`, `description`, `topics`

#### Scenario: Versions filtered by type
- **WHEN** `GET /api/v1/images/definitions/{name}/versions?type=MCP` is called
- **THEN** only versions matching that subtype are returned

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.web.controller.ImageDefinitionController`
- Service: `com.epam.aidial.deployment.manager.service.ImageDefinitionService`
- Base DTO: `com.epam.aidial.deployment.manager.web.dto.ImageDefinitionDto`
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.ImageDefinitionRequestDto`
- Subtype DTOs: `McpImageDefinitionDto`, `InterceptorImageDefinitionDto`, `AdapterImageDefinitionDto`
- Image source DTOs: `DockerImageSourceDto`, `GitDockerfileImageSourceDto`
- Grouped view DTOs: `ImageDefinitionViewDto` (name, selectedId, availableVersions), `ImageDefinitionViewElementDto` (id, version, status, description, topics)
- Version validation: `@ValidSemanticVersion` constraint on `version` field
- Topics validation: `@ValidTopics` — non-blank, ≤255 chars, no leading/trailing whitespace per topic
- Builder enum: `ImageBuilderDto` (BUILDKIT, BUILDKIT_ROOTLESS)
- Deletion cascade: `com.epam.aidial.deployment.manager.cleanup.component.ImageDefinitionCleanupStrategy` (finds all deployments by `imageDefinitionId`, queues each for async deletion via `ComponentCleanupService`)
