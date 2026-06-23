# Image Builds

## Purpose
This spec describes the image build pipeline — triggering builds for image definitions, tracking their lifecycle via `imageDefinitionId`, and streaming status and logs in real time via SSE.

Status: **Implemented**

## Key Terms
- **Image build**: An asynchronous pipeline execution that builds a container image from an image definition's source and pushes it to the registry.
- **Build identifier**: Builds are keyed by `imageDefinitionId` (UUID) — there is no separate build UUID. Only one build record exists per image definition at a time.
- **Build status (`ImageStatusDto`)**: The current state of a build: `NOT_BUILT`, `BUILDING`, `BUILD_FAILED`, `BUILD_SUCCESSFUL`.
- **Image builder (`ImageBuilderDto`)**: The build engine override: `BUILDKIT` or `BUILDKIT_ROOTLESS`. Controls whether the build runs in rootless mode.
- **SSE (Server-Sent Events)**: The streaming protocol used for real-time build status and log delivery.

## ADDED Requirements

### Requirement: Trigger image build
The system SHALL accept a build request for an image definition and initiate an asynchronous build pipeline. The response is HTTP 201 with no body.

Status: **Implemented**

#### Scenario: Successful trigger
- **WHEN** `POST /api/v1/images/builds` is called with a valid `imageDefinitionId` in the request body
- **THEN** a build pipeline is initiated for that image definition; the response is HTTP 201 with no body

#### Scenario: Non-existent image definition
- **WHEN** `POST /api/v1/images/builds` is called with an `imageDefinitionId` that does not exist
- **THEN** the system responds with 404

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
- **THEN** an SSE connection is opened and status change events are pushed in real time

#### Scenario: Stream closes on terminal state
- **WHEN** the build reaches a terminal state (`BUILD_SUCCESSFUL` or `BUILD_FAILED`)
- **THEN** the status SSE stream closes

### Requirement: Stream build logs via SSE
The system SHALL provide an SSE stream for real-time and historical build log output.

Status: **Implemented**

#### Scenario: Log stream during active build
- **WHEN** `GET /api/v1/images/builds/{imageDefinitionId}/logs` is called while a build is running
- **THEN** an SSE stream is opened and log lines are pushed as they are produced by BuildKit

#### Scenario: Log stream for completed build
- **WHEN** `GET /api/v1/images/builds/{imageDefinitionId}/logs` is called for a completed build
- **THEN** stored log lines are replayed and the connection closes after all logs are delivered

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.web.controller.ImageBuildController`
- Build pipeline orchestration: `com.epam.aidial.deployment.manager.service.pipeline.*`
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.CreateBuildImageRequestDto` (field: `imageDefinitionId`)
- Details DTO: `com.epam.aidial.deployment.manager.web.dto.ImageBuildDetailsDto`
  - Fields: `imageDefinitionId` (UUID), `status` (ImageStatusDto), `imageName` (String), `builtAt` (Instant), `logs` (List<String>)
- Build status enum: `com.epam.aidial.deployment.manager.web.dto.ImageStatusDto` (NOT_BUILT, BUILDING, BUILD_FAILED, BUILD_SUCCESSFUL)
- Image builder enum: `com.epam.aidial.deployment.manager.web.dto.ImageBuilderDto` (BUILDKIT, BUILDKIT_ROOTLESS)
- Related specs: `buildkit` (build engine), `image-definitions` (definition of what to build), `kubernetes-cleanup` (build pod lifecycle)
