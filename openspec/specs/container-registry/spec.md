# Container Registry

## Purpose
This spec describes Docker registry integration — querying image metadata and copying images between registries using Skopeo.

Status: **Implemented**

## Key Terms
- **Docker registry**: An OCI-compliant container image registry (e.g., Docker Hub, a private registry configured via `DOCKER_REGISTRY`).
- **Skopeo**: A command-line tool used to inspect and copy container images between registries without requiring a local Docker daemon.
- **Image reference**: A fully qualified container image identifier (registry/repository:tag or @digest).

## Requirements

### Requirement: Inspect container images in a registry
The system SHALL query a Docker registry to retrieve image metadata (digest, manifest, tags) for a given image reference.

Status: **Implemented**

#### Scenario: Image inspection succeeds
- **WHEN** the system needs to verify or retrieve metadata for a container image
- **THEN** the configured registry is queried and the image metadata is returned

#### Scenario: Image not found
- **WHEN** the system queries for a container image that does not exist in the registry
- **THEN** an appropriate error is surfaced to the calling operation (not silently ignored)

### Requirement: Copy images between registries using Skopeo
The system SHALL use Skopeo to copy container images from a source registry to the deployment target registry, preserving manifests and layers.

Status: **Implemented**

#### Scenario: Image copy
- **WHEN** an image needs to be transferred from a source registry to the internal registry
- **THEN** Skopeo copies the image with all layers and manifests intact

### Requirement: Registry authentication
The system SHALL authenticate with the configured Docker registry using the provided credentials before performing any registry operation.

Status: **Implemented**

#### Scenario: Authenticated registry access
- **WHEN** any registry operation is performed
- **THEN** the system authenticates with the registry using credentials from the `DOCKER_REGISTRY` configuration

#### Scenario: Authentication failure
- **WHEN** registry credentials are invalid or expired
- **THEN** the operation fails with an appropriate error

## Implementation Notes
- Docker registry client: `com.epam.aidial.deployment.manager.docker.*`
- Config property: `DOCKER_REGISTRY`
- Image copy tool: Skopeo (external process)
- Related specs: `image-builds`, `buildkit`
