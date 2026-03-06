# Kubernetes Events

## Purpose
This spec describes the Kubernetes cluster state tracking infrastructure — informers that maintain live in-memory caches and the event streaming service that powers the SSE endpoint exposed by `DeploymentController`. For the API-level SSE endpoint contract, see the `deployments` spec.

Status: **Implemented**

## Key Terms
- **Kubernetes event**: A cluster-level notification about a resource state change (e.g., pod scheduled, container started, pod failed).
- **Kubernetes informer**: A long-lived watch loop that maintains an in-memory cache of cluster resources, used to receive events without polling.
- **InformerManager**: Spring `@Component` that manages the lifecycle of all `InformerRegistration` instances — starts them via a cached daemon thread pool on `@PostConstruct` and stops them on `@PreDestroy`.
- **InformerRegistration**: Interface implemented per resource type to encapsulate informer start/stop logic.
- **EventStreamingService**: Spring `@Service` that bridges Kubernetes event sources to SSE connections via `EventReaderFactory` and `SseEmitterFactory`, running each stream on the `sse-streamer` executor.
- **SSE (Server-Sent Events)**: The HTTP streaming protocol used to push events from server to client.

## Requirements

### Requirement: Kubernetes informers track cluster state
The system SHALL use Kubernetes informers to maintain a live in-memory cache of cluster resource state, enabling efficient event delivery without polling the Kubernetes API.

Status: **Implemented**

#### Scenario: Informer reacts to state changes
- **WHEN** a Kubernetes resource changes state (pod created, pod failed, etc.)
- **THEN** the informer notifies subscribers and the change is reflected in deployment status updates

#### Scenario: Informer survives transient API server errors
- **WHEN** the Kubernetes API server is temporarily unreachable
- **THEN** the informer reconnects and re-syncs its cache once the API server recovers

#### Scenario: Informer lifecycle managed by InformerManager
- **WHEN** the Spring application context is initialized
- **THEN** `InformerManager` starts all registered `InformerRegistration` instances in daemon threads; on context shutdown it stops them and awaits termination for up to 10 seconds

### Requirement: Event streaming service bridges informers to SSE
The system SHALL provide a service layer that subscribes to informer events and multiplexes them to SSE connections opened by `DeploymentController` at `GET /api/v1/deployments/{id}/events/stream`.

Status: **Implemented**

#### Scenario: Multiple SSE clients for the same deployment
- **WHEN** multiple clients open SSE connections for the same deployment's events
- **THEN** each client receives its own copy of events independently

#### Scenario: Client disconnect cleanup
- **WHEN** an SSE client disconnects
- **THEN** the server-side event subscription is released without error

#### Scenario: Unknown object kind skipped
- **WHEN** a Kubernetes event references an object kind not recognised by `EventInfoMapper`
- **THEN** an `UnknownObjectKindException` is caught, the event is skipped with a debug log, and the stream remains open

#### Scenario: Events filtered by sinceTime, eventType, involvedObjectKind
- **WHEN** a client opens the SSE stream with optional query parameters (`sinceTime`, `eventType`, `involvedObjectKind`)
- **THEN** the `EventStreamerConfiguration` is built from those parameters and passed to `EventReaderFactory` to filter the event stream accordingly

## Implementation Notes
- Informer lifecycle manager: `com.epam.aidial.deployment.manager.kubernetes.informer.InformerManager`
- Informer registration interface: `com.epam.aidial.deployment.manager.kubernetes.informer.registration.InformerRegistration`
- Informer utilities: `com.epam.aidial.deployment.manager.kubernetes.informer.*`
- Event streaming service: `com.epam.aidial.deployment.manager.service.deployment.EventStreamingService`
- Event reader factory: `com.epam.aidial.deployment.manager.kubernetes.event.EventReaderFactory`
- Event streamer configuration: `com.epam.aidial.deployment.manager.kubernetes.event.EventStreamerConfiguration`
- Event watch reader: `com.epam.aidial.deployment.manager.kubernetes.event.WatchEventReader`
- SSE emitter factory: `com.epam.aidial.deployment.manager.service.SseEmitterFactory`
- SSE executor: `sse-streamer` `ExecutorService` (Spring qualifier)
- Fabric8 Kubernetes client version: see `fabric8_kubernetes_client_version` in `gradle.properties`
- API endpoints owned by `DeploymentController` — see `deployments` spec for endpoint contracts
  - `GET /api/v1/deployments/{id}/events/stream` — accepts `sinceTime`, `eventType`, `involvedObjectKind` query params
- Related specs: `deployments`
