# Kubernetes Cleanup

## Purpose
This spec describes the lifecycle management of transient Kubernetes resources — tracking ephemeral resources created during builds and deployments, and ensuring they are cleaned up automatically or on demand.

Status: **Implemented**

## Key Terms
- **Disposable resource**: A Kubernetes resource created for a transient purpose (e.g., a BuildKit build pod) and eligible for deletion once its job is done. Tracked by a `DisposableResourceEntity` record.
- **Resource lifecycle state (`ResourceLifecycleState`)**: The state of a disposable resource tracking record:
  - `STABLE` — the resource is still in use; not yet eligible for cleanup
  - `TEMPORARY` — the resource is transitional; will be cleaned up after use
  - `TO_CLEANUP` — the resource has reached a terminal state and is scheduled for deletion
- **Component removal**: A tracked removal operation persisted across restarts to ensure idempotent cleanup.
- **ShedLock**: Distributed locking mechanism used to prevent concurrent cleanup runs in multi-instance deployments.

## Requirements

### Requirement: Track disposable Kubernetes resources
The system SHALL persist a record for each transient Kubernetes resource created, enabling later cleanup.

Status: **Implemented**

#### Scenario: Resource registration
- **WHEN** a transient Kubernetes resource is created (e.g., a BuildKit build pod)
- **THEN** a `DisposableResourceEntity` record is persisted with: `id`, `groupId`, `reference` (ResourceReference JSON), `lifecycleState`, and `createdAt`

#### Scenario: Resource reaches terminal state
- **WHEN** a disposable resource completes its purpose (e.g., build finishes)
- **THEN** its `lifecycleState` is updated to `TO_CLEANUP`, making it eligible for the cleanup job

### Requirement: Scheduled cleanup of eligible disposable resources
The system SHALL run a scheduled job to delete all disposable Kubernetes resources whose `lifecycleState` is `TO_CLEANUP`.

Status: **Implemented**

#### Scenario: Scheduler triggers cleanup
- **WHEN** the cleanup job triggers (cron expression from `RESOURCE_CLEANER_CRON`)
- **THEN** all resources with `lifecycleState: TO_CLEANUP` are deleted from Kubernetes and their tracking records are removed

#### Scenario: Already-deleted resource
- **WHEN** cleanup attempts to delete a resource that no longer exists in Kubernetes
- **THEN** cleanup proceeds without error (idempotent)

#### Scenario: Distributed lock prevents concurrent cleanup
- **WHEN** multiple application instances are running
- **THEN** ShedLock ensures only one instance executes the cleanup job at a time

### Requirement: Manual cleanup trigger via API
The system SHALL expose an endpoint to immediately trigger cleanup of all eligible disposable resources.

Status: **Implemented**

#### Scenario: Manual trigger
- **WHEN** `POST /api/v1/disposable/clean` is called
- **THEN** all resources with `lifecycleState: TO_CLEANUP` are cleaned synchronously and a success response is returned

### Requirement: Component removal tracking survives restarts
The system SHALL persist in-progress removal operations so that cleanup can resume after an application restart.

Status: **Implemented**

#### Scenario: Restart during cleanup
- **WHEN** the application restarts mid-cleanup
- **THEN** `ComponentRemovalEntity` records allow the cleanup to be identified and completed on the next startup or scheduler run

## Implementation Notes
- Controller: `com.epam.aidial.deployment.manager.web.controller.DisposableResourceController`
- Cleanup components: `com.epam.aidial.deployment.manager.cleanup.*`
- Disposable resource entity: `com.epam.aidial.deployment.manager.dao.entity.DisposableResourceEntity`
  - Fields: `id`, `groupId`, `reference` (ResourceReference as JSON), `lifecycleState` (ResourceLifecycleState), `createdAt`
- Component removal entity: `com.epam.aidial.deployment.manager.dao.entity.ComponentRemovalEntity`
- Resource lifecycle state: `com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState` (STABLE, TEMPORARY, TO_CLEANUP)
- Scheduler config: `RESOURCE_CLEANER_CRON` environment variable
- Distributed lock: ShedLock 6.3.0
- Related specs: `image-builds`, `buildkit`
