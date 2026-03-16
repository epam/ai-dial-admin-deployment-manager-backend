# Kubernetes Cleanup

## Purpose
This spec describes the lifecycle management of transient Kubernetes resources — tracking ephemeral resources created during builds and deployments, and ensuring they are cleaned up automatically or on demand.

Status: **Implemented**

## Key Terms
- **Disposable resource**: A Kubernetes or container registry resource created for a transient purpose (e.g., a BuildKit build pod, an intermediate container image) and eligible for deletion once its job is done. Tracked by a `DisposableResourceEntity` record.
- **Resource lifecycle state (`ResourceLifecycleState`)**: The state of a disposable resource tracking record:
  - `STABLE` — the resource is still in use; not yet eligible for cleanup
  - `TEMPORARY` — the resource is transitional; will be cleaned up after immediate use (e.g., intermediate build pod)
  - `TO_CLEANUP` — the resource has reached a terminal state and is scheduled for deletion
- **ResourceReference**: Sealed type hierarchy for resource identity. Subtypes:
  - `K8sResourceReference` — identifies a Kubernetes resource by `kind` (`K8sResourceKind`), `namespace`, and `name`
  - `ContainerRegistryResourceReference` — identifies a container image by name in the managed registry
- **K8sResourceKind**: Enum of tracked Kubernetes resource types: `JOB`, `CONFIGMAP`, `SECRET`, `KNATIVE_SERVICE`, `NIM_SERVICE`, `INFERENCE_SERVICE`, `CILIUM_NETWORK_POLICY`
- **Component removal**: A tracked removal operation persisted across restarts to ensure idempotent cleanup.
- **ShedLock**: Distributed locking mechanism used to prevent concurrent cleanup runs in multi-instance deployments.

## Requirements

### Requirement: Track disposable Kubernetes resources
The system SHALL persist a record for each transient Kubernetes or container registry resource created, enabling later cleanup.

Status: **Implemented**

#### Scenario: Resource registration
- **WHEN** a transient resource is created (e.g., a BuildKit build pod, an intermediate container image)
- **THEN** a `DisposableResourceEntity` record is persisted with: `id`, `groupId`, `reference` (ResourceReference as JSON), `lifecycleState`, and `createdAt`

#### Scenario: Resource reaches terminal state
- **WHEN** a disposable resource completes its purpose (e.g., build finishes or deployment is deleted)
- **THEN** its `lifecycleState` is updated to `TO_CLEANUP`, making it eligible for the cleanup job

### Requirement: Scheduled cleanup of eligible disposable resources
The system SHALL run a scheduled job to delete all disposable resources whose `lifecycleState` is `TO_CLEANUP` or `TEMPORARY`.

Status: **Implemented**

#### Scenario: Scheduler triggers cleanup
- **WHEN** the cleanup job triggers (cron expression from `app.resource-cleaner-cron`)
- **THEN** all resources with `lifecycleState: TO_CLEANUP` are deleted from Kubernetes (or the container registry) and their tracking records are removed

#### Scenario: Already-deleted resource
- **WHEN** cleanup attempts to delete a resource that no longer exists in Kubernetes
- **THEN** cleanup proceeds without error (idempotent)

#### Scenario: Distributed lock prevents concurrent cleanup
- **WHEN** multiple application instances are running
- **THEN** ShedLock ensures only one instance executes the cleanup job at a time (`@SchedulerLock(name = "cleanDisposableResources")`)

#### Scenario: Cleanup processed in batches
- **WHEN** the scheduled cleanup runs
- **THEN** resources are fetched in batches of `app.resource-cleaner-take-size` until all eligible resources are processed

### Requirement: Manual cleanup trigger via API
The system SHALL expose an endpoint to immediately trigger cleanup of all eligible disposable resources.

Status: **Implemented**

#### Scenario: Manual trigger
- **WHEN** `POST /api/v1/disposable/clean` is called
- **THEN** all resources with `lifecycleState: TO_CLEANUP` or `TEMPORARY` are cleaned synchronously and an empty 200 response is returned

### Requirement: Temporary resource cleanup after pipeline completion
The system SHALL clean up resources marked `TEMPORARY` immediately after the pipeline or operation that created them completes, regardless of success or failure.

Status: **Implemented**

#### Scenario: Temporary build resources cleaned after pipeline
- **WHEN** a build pipeline (`ImageBuildFromGitPipeline`, `ImageWrapperBuildPipeline`, `ImageCopyPipeline`) completes (success or exception)
- **THEN** `DisposableResourceCleaner.cleanTemporaryByGroupId(imageDefinitionId)` is called in the `finally` block, deleting all `TEMPORARY` resources for that group

### Requirement: Component removal tracking survives restarts
The system SHALL persist in-progress removal operations so that cleanup can resume after an application restart.

Status: **Implemented**

#### Scenario: Restart during cleanup
- **WHEN** the application restarts mid-cleanup
- **THEN** `ComponentRemovalEntity` records allow the cleanup to be identified and completed on the next startup or scheduler run

### Requirement: Cleanup order respects resource dependencies
The system SHALL delete Kubernetes resources in a dependency-safe order to avoid orphaned resources.

Status: **Implemented**

#### Scenario: Services deleted before jobs
- **WHEN** cleanup runs for a group containing both service resources and job/configmap/secret resources
- **THEN** KNative Services, NIM Services, and Inference Services are deleted before Jobs, ConfigMaps, Secrets, and CiliumNetworkPolicies

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.web.controller.DisposableResourceController`
  - `POST /api/v1/disposable/clean`
- Cleanup components (in `com.epam.aidial.deployment.manager.cleanup`):
  - `DisposableResourceManager`           — JPA-backed resource state management (save, mark, delete, query by lifecycle state and group). Accepts the stored `serviceName` as a parameter for resource tracking (no longer generates service names internally).
  - `DisposableResourceCleaner`           — dispatches physical deletion per resource type; deletion order: KNATIVE_SERVICE → NIM_SERVICE → INFERENCE_SERVICE → JOB → CONFIGMAP → SECRET → CILIUM_NETWORK_POLICY → IMAGE
  - `DisposableResourceScheduledCleaner`  — `@Scheduled` + `@SchedulerLock` wrapper
  - `ContextRefreshedEventBasedDisposableResourceCleaner` — triggers cleanup on application startup
- Resource reference model (in `com.epam.aidial.deployment.manager.cleanup.resource.model`):
  - `K8sResourceReference`               — kind, namespace, name
  - `ContainerRegistryResourceReference` — image name
  - `ResourceLifecycleState`             — STABLE, TEMPORARY, TO_CLEANUP
  - `K8sResourceKind`                    — JOB, CONFIGMAP, SECRET, KNATIVE_SERVICE, NIM_SERVICE, INFERENCE_SERVICE, CILIUM_NETWORK_POLICY
- JPA repositories:
  - `com.epam.aidial.deployment.manager.dao.jpa.DisposableResourceJpaRepository`
  - `com.epam.aidial.deployment.manager.dao.jpa.ComponentRemovalJpaRepository`
- Component removal entity: `com.epam.aidial.deployment.manager.dao.entity.ComponentRemovalEntity`
- Scheduler config: `app.resource-cleaner-cron` (env var `RESOURCE_CLEANER_CRON`)
- Batch size: `app.resource-cleaner-take-size`
- ShedLock lock duration: `app.resource-cleaner-scheduler-lock-at-most-for` (default `10m`)
- ShedLock version: 6.3.0 (`shedlock-spring` + `shedlock-provider-jdbc-template`)
- Related specs: `image-builds`, `buildkit`, `container-registry`
