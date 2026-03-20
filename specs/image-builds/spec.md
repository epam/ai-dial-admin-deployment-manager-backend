# Image Builds

## Purpose
This spec describes the image build pipeline — triggering builds for image definitions, tracking their lifecycle via `imageDefinitionId`, and streaming status and logs in real time via SSE.

Status: **Implemented**

## Key Terms
- **Image build**: An asynchronous pipeline execution that builds a container image from an image definition's source and pushes it to the registry.
- **Build identifier**: Builds are keyed by `imageDefinitionId` (UUID) — there is no separate build UUID. Only one build record exists per image definition at a time.
- **Build status (`ImageStatusDto`)**: The current state of a build: `NOT_BUILT`, `BUILDING`, `BUILD_FAILED`, `BUILD_SUCCESSFUL`. Terminal states (`BUILD_SUCCESSFUL`, `BUILD_FAILED`) have `isFinal = true` and trigger automatic SSE stream closure.
- **Image builder (`ImageBuilderDto`)**: The build engine override: `BUILDKIT` or `BUILDKIT_ROOTLESS`. Controls whether the build runs in rootless mode.
- **SSE (Server-Sent Events)**: The streaming protocol used for real-time build status and log delivery.
- **ImageBuildRunner**: Selects and dispatches the correct build pipeline based on image definition type, source type, and MCP transport type.
- **ImageBuildLogsService**: Drives SSE streams for build status and log output by polling the image definition entity at a configurable interval.

## Requirements

### Requirement: Trigger image build
The system SHALL accept a build request for an image definition and initiate an asynchronous build pipeline. The response is HTTP 201 with no body.

Status: **Implemented**

#### Scenario: Successful trigger
- **WHEN** `POST /api/v1/images/builds` is called with a valid `imageDefinitionId` in the request body
- **THEN** a build pipeline is initiated for that image definition; the response is HTTP 201 with no body

#### Scenario: Non-existent image definition
- **WHEN** `POST /api/v1/images/builds` is called with an `imageDefinitionId` that does not exist
- **THEN** the system responds with 404

#### Scenario: Build rejected if already in progress or complete
- **WHEN** `POST /api/v1/images/builds` is called and the image definition is already in `BUILD_SUCCESSFUL` or `BUILDING` status
- **THEN** HTTP 400 is returned with an appropriate error message

### Requirement: Get build details
The system SHALL return full metadata for a build, keyed by `imageDefinitionId`.

Status: **Implemented**

#### Scenario: Retrieve details
- **WHEN** `GET /api/v1/images/builds/{imageDefinitionId}/details` is called
- **THEN** build metadata is returned: `imageDefinitionId`, `status` (ImageStatusDto), `imageName`, `builtAt`, `logs` (List of captured log lines)

#### Scenario: No build exists for image definition
- **WHEN** `GET /api/v1/images/builds/{imageDefinitionId}/details` is called for an image definition that has never been built
- **THEN** the system responds with 404

### Requirement: Stream build status via SSE
The system SHALL provide an SSE stream that pushes build status events as the build progresses.

Status: **Implemented**

#### Scenario: Status stream during build
- **WHEN** `GET /api/v1/images/builds/{imageDefinitionId}/status` is called while a build is running
- **THEN** an SSE connection is opened and `status` events are pushed in real time as the status changes

#### Scenario: Stream closes on terminal state
- **WHEN** the build reaches a terminal state (`BUILD_SUCCESSFUL` or `BUILD_FAILED`)
- **THEN** the status SSE stream closes automatically

### Requirement: Stream build logs via SSE
The system SHALL provide an SSE stream for real-time and historical build log output.

Status: **Implemented**

#### Scenario: Log stream during active build
- **WHEN** `GET /api/v1/images/builds/{imageDefinitionId}/logs` is called while a build is running
- **THEN** an SSE stream is opened; log lines are pushed as `logs` events and status changes are pushed as `status` events as they are produced

#### Scenario: Log stream for completed build
- **WHEN** `GET /api/v1/images/builds/{imageDefinitionId}/logs` is called for a completed build
- **THEN** stored log lines are replayed and the connection closes after all logs and the final `status` event are delivered

### Requirement: Pipeline selection based on image definition type and source
`ImageBuildRunner` SHALL select the correct pipeline automatically based on the image definition type (MCP, Adapter, Interceptor, Application), its source type (GitDockerfile or DockerImage), and for MCP definitions, the transport type (REMOTE or LOCAL).

Status: **Implemented**

#### Scenario: MCP image with Git Dockerfile source
- **WHEN** build is triggered for an MCP image definition with `GitDockerfileImageSource`
- **THEN** `ImageBuildFromGitPipeline` is dispatched (builds base image via BuildKit; for LOCAL transport, also runs image analysis and wrapper build)

#### Scenario: MCP LOCAL image with Docker source
- **WHEN** build is triggered for an MCP image definition with `DockerImageSource` and transport type `LOCAL`
- **THEN** `ImageWrapperBuildPipeline` is dispatched (analyses existing base image, then builds a wrapper image)

#### Scenario: MCP REMOTE image with Docker source
- **WHEN** build is triggered for an MCP image definition with `DockerImageSource` and transport type `REMOTE`
- **THEN** `ImageCopyPipeline` is dispatched (copies the existing image into the managed registry via Skopeo)

#### Scenario: Adapter, Interceptor, or Application image with Docker source
- **WHEN** build is triggered for an Adapter, Interceptor, or Application image definition with `DockerImageSource`
- **THEN** `ImageCopyPipeline` is dispatched

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.web.controller.ImageBuildController`
  - `POST   /api/v1/images/builds`              — start build (HTTP 201, no body)
  - `GET    /api/v1/images/builds/{id}/status`  — SSE status stream (`text/event-stream`)
  - `GET    /api/v1/images/builds/{id}/details` — build details JSON
  - `GET    /api/v1/images/builds/{id}/logs`    — SSE log stream (`text/event-stream`)
- Build request DTO: `com.epam.aidial.deployment.manager.web.dto.CreateBuildImageRequestDto` (record, field: `imageDefinitionId`)
- Build details DTO: `com.epam.aidial.deployment.manager.web.dto.ImageBuildDetailsDto`
- Build status enum: `com.epam.aidial.deployment.manager.web.dto.ImageStatusDto` (NOT_BUILT, BUILDING, BUILD_FAILED, BUILD_SUCCESSFUL)
- Image builder enum: `com.epam.aidial.deployment.manager.web.dto.ImageBuilderDto` (BUILDKIT, BUILDKIT_ROOTLESS)
- Dispatcher: `com.epam.aidial.deployment.manager.service.ImageBuildRunner`
- Log/status SSE service: `com.epam.aidial.deployment.manager.service.ImageBuildLogsService`
- Pipelines (in `com.epam.aidial.deployment.manager.service.pipeline`):
  - `ImageBuildFromGitPipeline`    — Git Dockerfile → BuildKit build → optional wrapper build (for LOCAL transport)
  - `ImageWrapperBuildPipeline`    — existing Docker image → image analysis step → wrapper image build step
  - `ImageCopyPipeline`            — existing Docker image → Skopeo copy into managed registry
- Image sources (in `com.epam.aidial.deployment.manager.model`):
  - `DockerImageSource`            — existing Docker image URI + optional entrypoint
  - `GitDockerfileImageSource`     — Git repo URL, branch, Dockerfile path, entrypoint
- Domain status enum: `com.epam.aidial.deployment.manager.model.ImageStatus` (NOT_BUILT, BUILDING, BUILD_FAILED, BUILD_SUCCESSFUL; `isFinal` flag drives stream closure)
- SSE polling intervals configurable via `app.sse.poll-interval-ms` (default 1000 ms) and `app.sse.min-streaming-interval-ms` (default 2000 ms)
- Build pipeline executor: `pipeline-runner` `ExecutorService` (Spring qualifier)
- SSE executor: `sse-streamer` `ExecutorService` (Spring qualifier)
- Related specs: `buildkit` (build engine), `image-definitions` (definition of what to build), `container-registry` (registry auth and image copy), `kubernetes-cleanup` (build pod lifecycle)
