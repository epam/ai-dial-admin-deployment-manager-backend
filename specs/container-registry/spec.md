# Container Registry

## Purpose
This spec describes Docker registry integration — querying image metadata and copying images between registries using Skopeo.

Status: **Implemented**

## Key Terms
- **Docker registry**: An OCI-compliant container image registry (e.g., Docker Hub, a private registry configured via `DOCKER_REGISTRY`).
- **Skopeo**: A command-line tool used to inspect and copy container images between registries without requiring a local Docker daemon.
- **Image reference**: A fully qualified container image identifier (registry/repository:tag or @digest).
- **DockerAuthScheme**: Authentication scheme for registry access — `NONE` (anonymous / bearer) or `BASIC` (username/password).
- **Trusted private registries**: Additional registries beyond the primary `DOCKER_REGISTRY` that the system is allowed to pull from, each optionally configured with BASIC credentials (`app.registry.trusted-private-registries`).

## Requirements

### Requirement: Inspect container images in a registry
The system SHALL query a Docker registry to retrieve image metadata (digest, manifest, entrypoint) for a given image reference.

Status: **Implemented**

#### Scenario: Image inspection succeeds
- **WHEN** the system needs to verify or retrieve metadata for a container image
- **THEN** the configured registry is queried and the image metadata (including entrypoint from the container configuration blob) is returned

#### Scenario: Image not found
- **WHEN** the system queries for a container image that does not exist in the registry
- **THEN** an appropriate error is surfaced to the calling operation (not silently ignored)

### Requirement: Copy images between registries using Skopeo
The system SHALL use Skopeo to copy container images from a source registry to the deployment target registry, preserving manifests and layers.

Status: **Implemented**

#### Scenario: Image copy
- **WHEN** an image needs to be transferred from a source registry to the internal registry
- **THEN** Skopeo copies the image with all layers and manifests intact

### Requirement: Delete images from the registry
The system SHALL support deleting container images from the managed registry by tag/digest, used during resource cleanup.

Status: **Implemented**

#### Scenario: Image deletion
- **WHEN** a disposable container registry resource reaches `TO_CLEANUP` state
- **THEN** the image is deleted from the registry by resolving its manifest digest and issuing a DELETE request

#### Scenario: Image already absent
- **WHEN** the image to be deleted is not found in the registry (HTTP 404 from manifest API)
- **THEN** cleanup proceeds without error

### Requirement: Registry authentication
The system SHALL authenticate with the configured Docker registry using the provided credentials before performing any registry operation. Multiple authentication modes are supported.

Status: **Implemented**

#### Scenario: BASIC auth with primary registry
- **WHEN** `app.registry.auth` is `BASIC` and the image registry matches `app.registry.url`
- **THEN** the operation authenticates using `app.registry.user` / `app.registry.password` credentials

#### Scenario: BASIC auth with a trusted private registry
- **WHEN** the image registry matches one of the entries in `app.registry.trusted-private-registries` with `authScheme: BASIC`
- **THEN** the operation authenticates using that registry's configured user/password

#### Scenario: Bearer/anonymous auth
- **WHEN** `app.registry.auth` is `NONE` and no trusted-registry entry matches
- **THEN** the operation uses bearer token negotiation (pull bearer auth)

#### Scenario: Authentication failure
- **WHEN** registry credentials are invalid or expired
- **THEN** the operation fails with an appropriate error

## Implementation Notes
- Docker registry client: `com.epam.aidial.deployment.manager.docker.DockerRegistryClient`
  - Image inspection: uses `jib-core` (`RegistryClient`) to pull manifest and container configuration blob
  - Image deletion: uses raw HTTP (`HttpURLConnection`) against the Docker Registry v2 manifest API
- Auth scheme enum: `com.epam.aidial.deployment.manager.configuration.DockerAuthScheme` (NONE, BASIC)
- Registry configuration bean: `com.epam.aidial.deployment.manager.configuration.RegistryConfiguration` → produces `RegistryProperties`
- Registry properties: `com.epam.aidial.deployment.manager.configuration.RegistryProperties`
  - `app.registry.url`                       — primary registry hostname
  - `app.registry.protocol`                  — registry protocol (default: `https`)
  - `app.registry.auth`                      — auth scheme (`NONE` or `BASIC`)
  - `app.registry.user` / `app.registry.password` — primary registry credentials
  - `app.registry.trusted-private-registries` — JSON array of additional registries with optional BASIC credentials
- Image copy tool: Skopeo (external process, invoked from build pipeline steps)
- Pull-secret `config.json` keys (`RegistryService.dockerConfig()`): `https://<host>/v2` for every registry, **except** Docker Hub aliases (`docker.io`, `index.docker.io`, `registry-1.docker.io`) which use the legacy key `https://index.docker.io/v1/`. Skopeo/containers-image normalizes both forms transparently, but `docker/cli` (and therefore BuildKit's authprovider, used by MCP LOCAL transport) hardcodes the legacy key for the Official Docker Hub index — a `/v2`-shaped key for `docker.io` is silently ignored there.
- `DockerRegistryClient.getRegistryClient()` (jib-core path, used for in-manager-process image inspection like inheriting a source image's entrypoint during wrapper builds):
  - Treats `docker.io`, `index.docker.io`, and `registry-1.docker.io` as the same registry when matching against `app.registry.url` and `app.registry.trusted-private-registries` entries. jib-core canonicalizes any Docker Hub reference to `registry-1.docker.io`, so a user-configured `docker.io` trusted entry must alias-match — otherwise jib falls through to anonymous bearer auth and fails 401 on private repos.
  - Once credentials are matched, uses jib's `doPullBearerAuth()` (not `configureBasicAuth()`) for the actual auth handshake. Modern registries (Docker Hub, ACR, GHCR, GAR, ECR, …) return a `WWW-Authenticate: Bearer …` challenge and reject a hardcoded `Authorization: Basic …` header on the registry API; `doPullBearerAuth()` performs the token-endpoint round-trip with the credentials and falls back to Basic when a registry advertises `WWW-Authenticate: Basic`.
- `jib-core` version: 0.27.3
- Related specs: `image-builds`, `buildkit`, `kubernetes-cleanup`
