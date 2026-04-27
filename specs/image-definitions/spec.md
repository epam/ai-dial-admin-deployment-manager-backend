# Image Definitions

## Purpose
This spec describes the ImageDefinition entity — the configuration record for a container image that can be built and deployed. ImageDefinitions have four concrete subtypes (MCP, Interceptor, Adapter, Application), each with its own spec.

Status: **Implemented**

## Key Terms
- **ImageDefinition**: A persistent record that defines a container image — its source, version, metadata, and build configuration. Identified by a UUID.
- **Image source**: Either a pre-built Docker image reference (`DockerImageSourceDto`) or a Git repository with a Dockerfile (`GitDockerfileImageSourceDto`).
- **`$type` discriminator**: The Jackson polymorphism field (`"mcp"` | `"adapter"` | `"interceptor"` | `"application"`) that determines which subtype is created or returned.
- **Build status**: The latest build outcome for a given image definition version (`buildStatus` field).
- **Semantic version**: The `version` field must follow semantic versioning (e.g., `1.0.0`).
- **Grouped view**: An `ImageDefinitionViewDto` that groups all versions of the same image definition name under a single entry, with a `selectedId` and an `availableVersions` list.
- **ImageType**: Domain enum with values MCP, ADAPTER, INTERCEPTOR, APPLICATION — there are no NIM or INFERENCE image definition subtypes.

## Base Fields

All image definition subtypes carry these fields:

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID | Yes (response only) | Generated on creation |
| `name` | String | Yes | 2–255 chars, alphanumeric/space/underscore/hyphen only |
| `version` | String | Yes | Must be a valid semantic version (`@ValidSemanticVersion`) |
| `description` | String | No | |
| `source` | ImageSourceDto | Yes | DockerImageSource or GitDockerfileImageSource |
| `license` | String | No | Max 255 chars |
| `createdAt` | Instant | Yes (response) | Set on creation |
| `updatedAt` | Instant | Yes (response) | Updated on modification |
| `topics` | List\<String\> | No (request) / Yes (response) | Free-form topic labels. Nullable on input; always present (possibly empty) in responses. Each topic: non-blank, ≤255 chars, no leading/trailing whitespace. Values are not from a fixed enum — the topics endpoint returns the distinct union across all image definitions. |
| `buildStatus` | ImageStatusDto | No | Latest build status, null if never built. Values: NOT_BUILT, BUILDING, BUILD_FAILED, BUILD_SUCCESSFUL |
| `author` | String | No | |
| `allowedDomains` | List\<String\> | No (request) / Yes (response) | Per-definition domains allowed during image build (build-time network access). Nullable on input; always present (possibly empty) in responses. See `domain-whitelist` spec for the full domain access control layering. |
| `imageBuilder` | ImageBuilderDto | No | Override the build engine: BUILDKIT or BUILDKIT_ROOTLESS (default: BUILDKIT_ROOTLESS) |

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
The system SHALL update an existing image definition by ID. The set of fields that may be mutated depends on the current `buildStatus`:

- **NOT_BUILT, BUILD_FAILED, BUILD_STOPPED** — full replacement is allowed. Any update resets `buildStatus` to `NOT_BUILT` because the prior build artifact (if any) is no longer valid against the new configuration.
- **BUILD_SUCCESSFUL** — only **meta fields** may change: `description`, `author`, `topics`, `license`. The update is applied in place; `buildStatus`, `imageName`, `builtAt`, and build logs are preserved, and the built image remains valid. Any change to a build-affecting field (`name`, `version`, `source`, `allowedDomains`, `imageBuilder`, or a subtype-specific field such as MCP `transportType`) is rejected with HTTP 400.
- **BUILDING** — the image definition is read-only. Any update is rejected with HTTP 400.

`allowedDomains` is compared order-insensitively (multiset semantics) during build-affecting detection — a cosmetic reorder of the same domains is treated as no change, mirroring `DeploymentService.isApplicableForCiliumNetworkPolicyUpdate`.

The configuration import path (`POST /api/v1/configs/import`, see `export-import` spec) bypasses the `BUILD_SUCCESSFUL` restriction and uses full-replacement semantics regardless of the prior build status.

Status: **Implemented**

#### Scenario: Full update when not BUILD_SUCCESSFUL or BUILDING
- **WHEN** `PUT /api/v1/images/definitions/{id}` is called with a valid body and current `buildStatus` is `NOT_BUILT`, `BUILD_FAILED`, or `BUILD_STOPPED`
- **THEN** the image definition is fully replaced, `buildStatus` is reset to `NOT_BUILT`, and a new `updatedAt` timestamp is set

#### Scenario: Meta-only update when BUILD_SUCCESSFUL
- **WHEN** `PUT /api/v1/images/definitions/{id}` is called while current `buildStatus` is `BUILD_SUCCESSFUL` and only meta fields (`description`, `author`, `topics`, `license`) differ from the stored record
- **THEN** the meta fields are updated; `buildStatus`, `imageName`, `builtAt`, and build logs are preserved

#### Scenario: allowedDomains reorder is not a build-affecting change
- **WHEN** `PUT /api/v1/images/definitions/{id}` is called while current `buildStatus` is `BUILD_SUCCESSFUL` and `allowedDomains` contains the same entries as the stored record in a different order (meta fields may also change)
- **THEN** the update is treated as meta-only and applied in place; `buildStatus` is preserved

#### Scenario: Build-affecting update when BUILD_SUCCESSFUL is rejected
- **WHEN** `PUT /api/v1/images/definitions/{id}` is called while current `buildStatus` is `BUILD_SUCCESSFUL` and any build-affecting field (`name`, `version`, `source`, `allowedDomains` membership, `imageBuilder`, or a subtype-specific field) differs
- **THEN** the system responds with 400 and the stored record is unchanged

#### Scenario: Update rejected while BUILDING
- **WHEN** `PUT /api/v1/images/definitions/{id}` is called while current `buildStatus` is `BUILDING`
- **THEN** the system responds with 400

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
- **THEN** all versions for the given name are returned; each entry includes `id`, `name`, `version`, `status`

#### Scenario: Versions filtered by type
- **WHEN** `GET /api/v1/images/definitions/{name}/versions?type=MCP` is called
- **THEN** only versions matching that subtype are returned

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.web.controller.ImageDefinitionController`
- Endpoints:
  - `GET    /api/v1/images/definitions`
  - `GET    /api/v1/images/definitions/grouped`
  - `GET    /api/v1/images/definitions/{id}`
  - `GET    /api/v1/images/definitions/{name}/versions`
  - `POST   /api/v1/images/definitions`
  - `PUT    /api/v1/images/definitions/{id}`
  - `DELETE /api/v1/images/definitions/{id}`
- Service: `com.epam.aidial.deployment.manager.service.ImageDefinitionService`
- JPA repository: `com.epam.aidial.deployment.manager.dao.jpa.ImageDefinitionJpaRepository`
- Domain repository: `com.epam.aidial.deployment.manager.dao.repository.ImageDefinitionRepository`
- Domain model (abstract): `com.epam.aidial.deployment.manager.model.ImageDefinition`
- Domain type enum: `com.epam.aidial.deployment.manager.model.ImageType` (MCP, ADAPTER, INTERCEPTOR, APPLICATION)
- Base response DTO (abstract): `com.epam.aidial.deployment.manager.web.dto.ImageDefinitionDto`
- Base request DTO (abstract): `com.epam.aidial.deployment.manager.web.dto.ImageDefinitionRequestDto`
- Subtype response DTOs: `com.epam.aidial.deployment.manager.web.dto.McpImageDefinitionDto`, `AdapterImageDefinitionDto`, `InterceptorImageDefinitionDto`, `ApplicationImageDefinitionDto`
- Subtype request DTOs: `com.epam.aidial.deployment.manager.web.dto.McpImageDefinitionRequestDto`, `AdapterImageDefinitionRequestDto`, `InterceptorImageDefinitionRequestDto`, `ApplicationImageDefinitionRequestDto`
- Image source DTOs: `DockerImageSourceDto`, `GitDockerfileImageSourceDto`
- Grouped view DTO: `com.epam.aidial.deployment.manager.web.dto.ImageDefinitionViewDto` (name, selectedId, availableVersions)
- Grouped view element DTO: `com.epam.aidial.deployment.manager.web.dto.ImageDefinitionViewElementDto` (id, version, status, description, topics)
- Versions endpoint response DTO: `com.epam.aidial.deployment.manager.web.dto.BaseImageDetailsDto` (id, name, version, status)
- Version validation: `@ValidSemanticVersion` constraint on `version` field
- Topics validation: `@ValidTopics` — non-blank, ≤255 chars, no leading/trailing whitespace per topic
- Builder enum: `ImageBuilderDto` (BUILDKIT, BUILDKIT_ROOTLESS); default is BUILDKIT_ROOTLESS
- Deletion cascade: `com.epam.aidial.deployment.manager.cleanup.component.ImageDefinitionCleanupStrategy` (finds all deployments by `imageDefinitionId`, queues each for async deletion via `ComponentCleanupService`)
