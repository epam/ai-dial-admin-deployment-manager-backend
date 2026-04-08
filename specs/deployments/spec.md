# Deployments

## Purpose
This spec describes the deployment lifecycle — creating, configuring, activating, and monitoring Kubernetes deployments for AI components. All deployment types share a common CRUD and lifecycle contract via `DeploymentController`; subtype specs document type-specific fields and behaviors.

Status: **Implemented**

## Key Terms
- **Deployment**: A persistent configuration record representing a deployed (or deployable) AI component in Kubernetes, identified by a UUID.
- **`name`**: The stable, immutable string identifier for a deployment (2–36 chars, `^[a-z0-9-]+`). Used as the Kubernetes resource name and in API paths that reference a deployment by name. Separate from `displayName`.
- **Source**: A polymorphic typed object (`$type` discriminator) representing where a deployment's container image or model comes from. All deployment types carry a `source` field. Four variants exist: `InternalImageSource` (`$type: "internal_image"` — references a managed image definition), `ImageReferenceSource` (`$type: "image_reference"` — direct Docker image URI), `HuggingFaceSource` (`$type: "huggingface"` — HuggingFace model), `NgcRegistrySource` (`$type: "ngc_registry"` — NVIDIA NGC image).
- **Image-based deployment (Knative)**: A deployment type (MCP, Interceptor, Adapter, Application) that runs on KNative. Accepts two source types: `internal_image` (referencing an image definition by ID or type+name+version triple) or `image_reference` (direct Docker image name, no image definition required).
- **Model-source deployment**: A deployment type (Inference, NIM) that references a model source directly. Inference uses `HuggingFaceSource`; NIM uses `NgcRegistrySource`.
- **Deploy / Undeploy**: Activating or deactivating the Kubernetes resources for a deployment, while preserving the configuration record.
- **Reconciliation**: Process run at startup to align actual Kubernetes state with the desired state stored in the database.
- **`DeploymentMetadataDto`**: A container object holding the deployment's environment variable definitions (`envs`).

## Deployment Status Lifecycle

```
NOT_DEPLOYED ──(deploy)──▶ PENDING ──(reconcile: healthy)──▶ RUNNING
                                   └──(reconcile: unhealthy)──▶ CRASHED
RUNNING ──(undeploy)──▶ STOPPING ──(reconcile: removed)──▶ STOPPED
RUNNING ──(rolling update / deploy with config changes)──▶ PENDING
RUNNING ──(reconcile: health check fails)──▶ CRASHED
STOPPED ──(deploy)──▶ PENDING
CRASHED ──(undeploy)──▶ STOPPING
```

Note: calling `deploy` on an already-active deployment (PENDING, RUNNING, CRASHED) performs a **rolling update** — Kubernetes resources are updated in-place and status transitions to PENDING. The `isActive()` predicate covers PENDING, RUNNING, CRASHED, and STOPPING states.

| Status | Meaning |
|---|---|
| `NOT_DEPLOYED` | Created but never deployed; no Kubernetes resources exist |
| `PENDING` | Deploy requested; Kubernetes resources are being created or updated |
| `RUNNING` | Deployment is live and healthy |
| `CRASHED` | Deployment failed to start or became unhealthy (detected during reconciliation) |
| `STOPPING` | Undeploy requested; Kubernetes resources are being removed |
| `STOPPED` | Undeployed; configuration retained but no Kubernetes resources |

## Requirements

### Requirement: List deployments
The system SHALL return all deployments, optionally filtered by `imageDefinitionId` OR deployment `type`. The two filters are mutually exclusive.

Status: **Implemented**

#### Scenario: List all deployments
- **WHEN** `GET /api/v1/deployments` is called without filters
- **THEN** all deployments of all types are returned

#### Scenario: Filter by image definition
- **WHEN** `GET /api/v1/deployments?imageDefinitionId={uuid}` is called
- **THEN** only deployments linked to that image definition are returned

#### Scenario: Filter by type
- **WHEN** `GET /api/v1/deployments?type=MCP` (or multiple `type` values) is called
- **THEN** only deployments of the specified type(s) are returned

### Requirement: Get deployment by ID
The system SHALL return a single deployment by its unique identifier including all subtype-specific fields.

Status: **Implemented**

#### Scenario: Existing deployment
- **WHEN** `GET /api/v1/deployments/{id}` is called with a valid ID
- **THEN** the deployment is returned with all base and subtype-specific fields

#### Scenario: Non-existent deployment
- **WHEN** `GET /api/v1/deployments/{id}` is called with an unknown ID
- **THEN** the system responds with 404

### Requirement: Create deployment
The system SHALL create a new deployment of the specified subtype. New deployments start in `NOT_DEPLOYED` status and the response is HTTP 201.

Status: **Implemented**

#### Scenario: Create image-based deployment with internal_image source (by ID)
- **WHEN** `POST /api/v1/deployments` is called with `type: MCP|INTERCEPTOR|ADAPTER|APPLICATION` and `source: { "$type": "internal_image", "imageDefinitionId": "<uuid>" }`
- **THEN** a new deployment is persisted in `NOT_DEPLOYED` status with the `internal_image` source; HTTP 201 is returned

#### Scenario: Create image-based deployment with internal_image source (by type + name + version)
- **WHEN** `POST /api/v1/deployments` is called with `source: { "$type": "internal_image", "imageDefinitionType": "...", "imageDefinitionName": "...", "imageDefinitionVersion": "..." }`
- **THEN** the image definition is resolved by type + name + version and the deployment is persisted; HTTP 201 is returned

#### Scenario: Create image-based deployment with image_reference source
- **WHEN** `POST /api/v1/deployments` is called with `type: MCP|INTERCEPTOR|ADAPTER|APPLICATION` and `source: { "$type": "image_reference", "imageReference": "<docker-image>" }`
- **THEN** a new deployment is persisted in `NOT_DEPLOYED` status using the direct image reference (no image definition required); HTTP 201 is returned

#### Scenario: Incomplete internal_image source rejected
- **WHEN** `POST /api/v1/deployments` is called with an `internal_image` source missing both `imageDefinitionId` AND a complete `(imageDefinitionType, imageDefinitionName, imageDefinitionVersion)` triple
- **THEN** the system responds with 400 (`@AssertTrue` validation on `isValidImageReference()`)

#### Scenario: Incompatible source type rejected
- **WHEN** `POST /api/v1/deployments` is called with a source type that does not match the deployment type (e.g., `ngc_registry` for an MCP deployment, or `image_reference` for an Inference deployment)
- **THEN** the system responds with 400 with a source type validation error

#### Scenario: Create model-source deployment
- **WHEN** `POST /api/v1/deployments` is called with `type: INFERENCE|NIM` and a model source reference
- **THEN** a new deployment is persisted in `NOT_DEPLOYED` status with the model source; HTTP 201 is returned

#### Scenario: Missing required fields
- **WHEN** `POST /api/v1/deployments` is called with missing required fields (e.g., `name`, `displayName`)
- **THEN** the system responds with 400

### Requirement: Update deployment configuration
The system SHALL update an existing deployment's configuration without affecting its Kubernetes resources.

Status: **Implemented**

#### Scenario: Successful update
- **WHEN** `PUT /api/v1/deployments/{id}` is called with a valid body
- **THEN** the deployment configuration is updated; the Kubernetes state is not immediately changed

#### Scenario: Non-existent deployment
- **WHEN** `PUT /api/v1/deployments/{id}` is called with an unknown ID
- **THEN** the system responds with 404

### Requirement: Delete deployment
The system SHALL delete a deployment record and remove its associated Kubernetes resources. The response is HTTP 204.

Status: **Implemented**

#### Scenario: Successful deletion
- **WHEN** `DELETE /api/v1/deployments/{id}` is called
- **THEN** the deployment record is removed and Kubernetes resources are cleaned up; HTTP 204 is returned

### Requirement: Duplicate deployment
The system SHALL create an identical copy of an existing deployment with a new name, in `NOT_DEPLOYED` status.

Status: **Implemented**

#### Scenario: Duplicate
- **WHEN** `POST /api/v1/deployments/duplicate` is called with `sourceDeploymentName`, `newDeploymentName` (max 36 chars, `^[a-z0-9-]+`), and `newDeploymentDisplayName`
- **THEN** a new deployment is created with the same configuration, the specified new name and display name, and `NOT_DEPLOYED` status

### Requirement: Change image definition for deployments
The system SHALL allow reassigning one or more image-based deployments to reference a different image definition version.

Status: **Implemented**

#### Scenario: Image version change
- **WHEN** `POST /api/v1/deployments/change-image` is called with `imageDefinitionId` (UUID) and a non-empty list of deployment names (`deployments`)
- **THEN** the specified deployments are updated to reference the new image definition version

### Requirement: Activate a deployment (deploy)
The system SHALL create or update Kubernetes resources to make a deployment live. Status transitions to `PENDING` immediately, then `RUNNING` once healthy.

Status: **Implemented**

#### Scenario: Successful deploy
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called
- **THEN** if no `serviceName` is stored, one is generated via `K8sNamingUtils.generateName()` and persisted; Kubernetes resources are created using the stored service name and the status transitions from `NOT_DEPLOYED` or `STOPPED` to `PENDING`

#### Scenario: Deploy already-running deployment (rolling update)
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called on a `RUNNING` deployment
- **THEN** Kubernetes resources are updated to reflect any configuration changes and status transitions to `PENDING`

#### Scenario: Deploy a crashed deployment
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called on a `CRASHED` deployment
- **THEN** Kubernetes resources are updated and status transitions to `PENDING`

### Requirement: Deactivate a deployment (undeploy)
The system SHALL remove Kubernetes resources for a deployment while preserving its configuration record. Status transitions to `STOPPING`, then `STOPPED`.

Status: **Implemented**

#### Scenario: Successful undeploy
- **WHEN** `POST /api/v1/deployments/{id}/undeploy` is called
- **THEN** Kubernetes resources are removed and status transitions to `STOPPING`, eventually reaching `STOPPED`

### Requirement: Deployment carries common base configuration
All deployment types SHALL carry these fields:

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | String | Yes | 2–36 chars, `^[a-z0-9-]+`. Immutable after creation. Used as K8s resource name. |
| `displayName` | String | Yes | 2–255 chars. Human-readable label. |
| `description` | String | No | Free-text description. |
| `metadata` | DeploymentMetadataDto | No | Container for `envs` list (see env var requirement below). |
| `resources` | ResourcesDto | No | CPU/memory resource configuration (see below). |
| `probeProperties` | ProbePropertiesDto | No | Startup probe configuration (see below). |
| `allowedDomains` | List\<String\> | No | Runtime network policy — see `domain-whitelist` spec. |
| `containerPort` | Integer | No | 1–65535. Container port override. |
| `scaling` | ScalingDto | No | Scaling configuration — replicas, strategy, scale-to-zero. See structure below. |
| `command` | String | No | Container entrypoint override. Parsed into a list of tokens using shell-like parsing (handling quoted strings, spaces, special characters). Applied to container spec in generated manifests. |
| `args` | String | No | Container arguments override. Same parsing as `command`. Applied to container spec in generated manifests. |
| `status` | DeploymentStatusDto | Yes (response) | Current lifecycle status (see status lifecycle above). |
| `url` | String | No (response only) | Auto-generated service URL. Set when deployment becomes RUNNING; cleared on undeploy/stop. Not user-supplied. |
| `serviceName` | String | No (internal) | Kubernetes service name. Generated at first deploy via `K8sNamingUtils.generateName()`, persisted in DB, and used for all subsequent K8s operations. Immutable once assigned. Not exposed via API; excluded from config export. NULL for NOT_DEPLOYED deployments that have never been deployed. |
| `author` | String | No | |
| `createdAt` | Instant | Yes (response) | Set on creation. |
| `updatedAt` | Instant | Yes (response) | Updated on modification. |

**`ResourcesDto` structure:**

| Field | Type | Notes |
|---|---|---|
| `limits` | Map\<String, String\> | Resource limits (e.g., `{"cpu": "500m", "memory": "512Mi"}`). Nullable. |
| `requests` | Map\<String, String\> | Resource requests (e.g., `{"cpu": "100m", "memory": "256Mi"}`). Nullable. |

**`ProbePropertiesDto` structure** (startup probe configuration):

| Field | Type | Required | Notes |
|---|---|---|---|
| `enabled` | boolean | Yes | When true, the startup probe is applied to the container. |
| `initialDelaySeconds` | Integer | No | Seconds before first probe. Min: 1, max: 6000. |
| `periodSeconds` | Integer | No | Probe interval in seconds. Min: 1, max: 600. |
| `timeoutSeconds` | Integer | No | Probe timeout in seconds. Min: 1, max: 12000. |
| `failureThreshold` | Integer | No | Consecutive failures before marking failed. Min: 1, max: 100. |
| `probe` | ProbeHandlerDto | Yes | Probe handler — one of `HttpGetProbeDto` (path + port) or `TcpSocketProbeDto` (port). |

**`ScalingDto` structure** (scaling configuration, applies to all deployment types):

| Field | Type | Required | Notes |
|---|---|---|---|
| `minReplicas` | int | Yes | Minimum replica count. `@Min(0)`. Must be 0 when `scaleToZeroDelaySeconds` is set; must be > 0 when `scaleToZeroDelaySeconds` is not set. |
| `maxReplicas` | int | Yes | Maximum replica count. `@Min(1)`. |
| `scaleToZeroDelaySeconds` | Integer | No | Idle time (seconds) before scaling to zero. `@Min(1)`. When set, `minReplicas` must be 0; when not set, `minReplicas` must be > 0. |
| `strategy` | ScalingStrategyDto | Conditional | Scaling strategy. Nullable. Required when `minReplicas != maxReplicas` (unless `minReplicas=0, maxReplicas=1`). Must be null when `minReplicas == maxReplicas` or when `minReplicas=0, maxReplicas=1`. |

**`ScalingStrategyDto` structure:**

| Field | Type | Required | Notes |
|---|---|---|---|
| `$type` | ScalingStrategyTypeDto | Yes | Strategy type: `PENDING_REQUESTS`, `ACTIVE_REQUESTS`, or `HARDWARE_USAGE`. Note: KNative deployments only support `ACTIVE_REQUESTS`; KServe supports all types. |
| `threshold` | int | Yes | Scale-out threshold. `@Min(1)`. |

Status: **Implemented**

#### Scenario: Common fields returned
- **WHEN** any deployment is retrieved
- **THEN** the response includes all common base fields alongside type-specific fields

#### Scenario: URL populated on RUNNING
- **WHEN** a deployment's health check passes and status transitions to RUNNING
- **THEN** `url` is set to the resolved Kubernetes service URL

#### Scenario: URL cleared on undeploy
- **WHEN** a deployment is undeployed or stops
- **THEN** `url` is set to null

### Requirement: Deployment supports environment variable configuration
A deployment SHALL support an optional `metadata.envs` list. Each `EnvVarDefinitionDto` entry defines a name, a value, a mount type, and an optional description.

Status: **Implemented**

#### Scenario: Environment variables stored and returned
- **WHEN** a deployment is created or updated with `metadata.envs` entries
- **THEN** the env vars are persisted and returned in all deployment responses

#### Scenario: Env var name validation
- **WHEN** an env var name is outside `^[-._a-zA-Z0-9]+` or exceeds 253 chars
- **THEN** the system responds with 400

#### Scenario: Mount type variants
- **WHEN** an env var is defined with `mountType: CONTENT`, `SECURE_CONTENT`, or `SECURE_FILE`
- **THEN** the mount type is persisted and used during Kubernetes manifest generation

#### Scenario: Simple value type
- **WHEN** an env var value has `$type: "simple"` with a nullable `value` string
- **THEN** the value is stored as a plain env var

#### Scenario: File value type
- **WHEN** an env var value has `$type: "file"` with `fileName` (1–253 chars, `^[-._a-zA-Z0-9]+`) and optional `fileContent`
- **THEN** the file content is mounted as a volume file rather than an env var

### Requirement: Stream deployment events via SSE
The system SHALL stream real-time Kubernetes events for a deployment via SSE. Optional filters narrow the event stream.

Status: **Implemented**

#### Scenario: Event stream
- **WHEN** `GET /api/v1/deployments/{id}/events/stream` is called
- **THEN** an SSE connection is opened and Kubernetes events for the deployment are pushed in real time

#### Scenario: Event stream with filters
- **WHEN** the event stream endpoint is called with `sinceTime`, `eventType`, or `involvedObjectKind` query parameters
- **THEN** only events matching the filter criteria are streamed

#### Scenario: Client disconnects
- **WHEN** the SSE client disconnects
- **THEN** the server-side event subscription is cleaned up

### Requirement: List pods for a deployment
The system SHALL return all pods and active pods associated with a deployment.

Status: **Implemented**

#### Scenario: All pods
- **WHEN** `GET /api/v1/deployments/{id}/pods` is called
- **THEN** all pod instances are returned; each `PodInfoDto` entry includes `name`, `createdAt`, `restartCount`, `lastTerminationReason`, `lastExitCode`, `lastSignal`, `lastFinishedAt`

#### Scenario: Active pods only
- **WHEN** `GET /api/v1/deployments/{id}/active-pods` is called
- **THEN** only pods in a running/ready state are returned

### Requirement: Stream pod logs via SSE
The system SHALL stream real-time log output from a specific pod via SSE, with optional control parameters.

Status: **Implemented**

#### Scenario: Pod log stream
- **WHEN** `GET /api/v1/deployments/{id}/pods/{podId}/logs` is called
- **THEN** an SSE connection is opened and live log lines from the pod are streamed

#### Scenario: Pod log stream with parameters
- **WHEN** the log endpoint is called with `sinceTime`, `sinceSeconds`, `tail`, or `previous` parameters
- **THEN** the log stream is filtered or bounded according to the specified parameters

### Requirement: Internal deployment lookup
The system SHALL expose an unauthenticated internal API for service-to-service retrieval of a deployment by ID.

Status: **Implemented**

#### Scenario: Internal deployment retrieval
- **WHEN** `GET /api/internal/v1/deployments/{id}` is called
- **THEN** the full deployment record is returned (same shape as the public API); the endpoint requires no authentication

### Requirement: Startup reconciliation
The system SHALL reconcile all deployments against actual Kubernetes state on application startup, correcting any drift caused by restarts. Reconciliation uses the stored `serviceName` to locate Kubernetes resources, so it remains correct even if `resourceNamePrefix` has changed since the deployment was created.

Status: **Implemented**

#### Scenario: Reconcile on startup
- **WHEN** the application starts
- **THEN** deployments with `RUNNING` or `PENDING` status are reconciled against actual Kubernetes state using stored service names; status is corrected if needed

## Entity vs DTO Hierarchy

The entity and DTO layers use different inheritance structures:

**DTO layer** (two-tier for image-based types):
```
DeploymentDto (abstract)
├── ImageBasedDeploymentDto (abstract) — adds source: DeploymentSourceDto (internal_image | image_reference)
│   ├── McpDeploymentDto
│   ├── InterceptorDeploymentDto
│   ├── AdapterDeploymentDto
│   └── ApplicationDeploymentDto
├── InferenceDeploymentDto — source: InferenceDeploymentSourceDto (huggingface)
└── NimDeploymentDto — source: NimDeploymentSourceDto (ngc_registry)
```

**Domain model** — unified `Source` sealed interface:
```
Source (sealed interface, $type discriminator)
├── InternalImageSource (internal_image) — imageDefinitionId, imageDefinitionType, imageDefinitionName, imageDefinitionVersion
├── ImageReferenceSource (image_reference) — imageReference
├── HuggingFaceSource (huggingface) — modelName
└── NgcRegistrySource (ngc_registry) — imageRef
```

**Entity layer** (flat — all extend base directly):
```
DeploymentEntity (@Inheritance JOINED) — source: PersistenceSource (JSON), imageDefinitionId: UUID
├── McpDeploymentEntity
├── InterceptorDeploymentEntity
├── AdapterDeploymentEntity
├── ApplicationDeploymentEntity
├── InferenceDeploymentEntity
└── NimDeploymentEntity
```

The `source` JSON column lives on the base `DeploymentEntity`, storing a `PersistenceSource` (sealed interface mirroring the domain `Source` hierarchy). The `imageDefinitionId` is retained as a separate indexed column for efficient query filtering. The `imageDefinitionType`, `imageDefinitionName`, `imageDefinitionVersion` columns have been removed from the entity (moved into `PersistenceInternalImageSource` within the JSON). NIM and Inference subtype entities no longer carry their own `source` columns.

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.web.controller.DeploymentController`
- Internal controller: `com.epam.aidial.deployment.manager.web.controller.internal.DeploymentInternalController` (path: `/api/internal/v1/deployments`, no auth required)
- Service: `com.epam.aidial.deployment.manager.service.deployment.*`
- Base entity: `com.epam.aidial.deployment.manager.dao.entity.deployment.DeploymentEntity` (flat hierarchy, JOINED inheritance)
- Status enum: `com.epam.aidial.deployment.manager.dao.entity.PersistenceDeploymentStatus`
- Base response DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.DeploymentDto`
- Image-based abstract DTO: `com.epam.aidial.deployment.manager.web.dto.deployment.ImageBasedDeploymentDto` (no entity-layer counterpart)
- Metadata DTO: `com.epam.aidial.deployment.manager.web.dto.DeploymentMetadataDto`
- Env var DTOs: `EnvVarDefinitionDto`, `SimpleEnvVarValueDto` (`$type: "simple"`), `FileEnvVarValueDto` (`$type: "file"`)
- Env var mount types: `EnvVarMountTypeDto` (CONTENT, SECURE_CONTENT, SECURE_FILE)
- Pod DTO: `com.epam.aidial.deployment.manager.web.dto.PodInfoDto`
- Duplicate request: `com.epam.aidial.deployment.manager.web.dto.DuplicateDeploymentRequestDto`
- Image change request: `com.epam.aidial.deployment.manager.web.dto.DeploymentImageChangeRequestDto`
- Resources DTO: `com.epam.aidial.deployment.manager.web.dto.ResourcesDto` (record with `limits`/`requests` maps)
- Scaling DTO: `com.epam.aidial.deployment.manager.web.dto.ScalingDto` (minReplicas, maxReplicas, scaleToZeroDelaySeconds, strategy)
- Scaling strategy DTO: `com.epam.aidial.deployment.manager.web.dto.ScalingStrategyDto` ($type as `ScalingStrategyTypeDto`, threshold)
- Scaling strategy type enum: `com.epam.aidial.deployment.manager.web.dto.ScalingStrategyTypeDto` (PENDING_REQUESTS, ACTIVE_REQUESTS, HARDWARE_USAGE)
- Scaling validator: `com.epam.aidial.deployment.manager.web.validation.ScalingValidator` (validates strategy presence rules based on replica counts; validates minReplicas/scaleToZeroDelaySeconds cross-field constraints)
- Scaling DTO mapper: `com.epam.aidial.deployment.manager.web.mapper.ScalingDtoMapper`
- Domain scaling model: `com.epam.aidial.deployment.manager.model.Scaling`, `com.epam.aidial.deployment.manager.model.ScalingStrategy`, `com.epam.aidial.deployment.manager.model.ScalingStrategyType`
- Probe properties DTO: `com.epam.aidial.deployment.manager.web.dto.probe.ProbePropertiesDto`
- Probe handlers: `HttpGetProbeDto` (path + port), `TcpSocketProbeDto` (port)
- Source response DTO (Knative): `com.epam.aidial.deployment.manager.web.dto.deployment.DeploymentSourceDto` (sealed interface, `$type` discriminator)
  - `InternalImageDeploymentSourceDto` (`$type: "internal_image"`) — imageDefinitionId, imageDefinitionName, imageDefinitionVersion
  - `ImageReferenceDeploymentSourceDto` (`$type: "image_reference"`) — imageReference
- Source request DTO (Knative): `com.epam.aidial.deployment.manager.web.dto.deployment.CreateDeploymentSourceRequestDto` (sealed interface)
  - `CreateInternalImageDeploymentSourceRequestDto` — imageDefinitionId or (imageDefinitionType + imageDefinitionName + imageDefinitionVersion)
  - `CreateImageReferenceDeploymentSourceRequestDto` — imageReference (`@ValidDockerImageName`)
- Domain source model: `com.epam.aidial.deployment.manager.model.deployment.Source` (sealed interface)
  - `InternalImageSource`, `ImageReferenceSource`, `HuggingFaceSource`, `NgcRegistrySource`
- Persistence source model: `com.epam.aidial.deployment.manager.dao.entity.deployment.PersistenceSource` (sealed interface, stored as JSON)
  - `PersistenceInternalImageSource`, `PersistenceImageReferenceSource`, `PersistenceHuggingFaceSource`, `PersistenceNgcRegistrySource`
- Source validation: `DeploymentService.validateSourceForDeploymentType()` — enforces source-to-deployment-type compatibility
- Image resolution: `KnativeDeploymentManager.resolveImageName()` — pattern-matches on Source variant
- Export mix-in: `com.epam.aidial.deployment.manager.configuration.export.InternalImageSourceExportMixIn` — excludes `imageDefinitionId` from config export
- URL resolution: `AbstractDeploymentManager.resolveServiceUrl()` — populated on RUNNING, cleared on undeploy
