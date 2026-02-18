# Kubernetes Events

## Purpose
This spec describes the Kubernetes cluster state tracking infrastructure — informers that maintain live in-memory caches and the event/log streaming services that power the SSE endpoints exposed by `DeploymentController`. For the API-level SSE endpoints and their contracts, see the `deployments` spec.

Status: **Implemented**

## Key Terms
- **Kubernetes event**: A cluster-level notification about a resource state change (e.g., pod scheduled, container started, pod failed).
- **Kubernetes informer**: A long-lived watch loop that maintains an in-memory cache of cluster resources, used to receive events without polling.
- **SSE (Server-Sent Events)**: The HTTP streaming protocol used to push events from server to client.

## ADDED Requirements

### Requirement: Kubernetes informers track cluster state
The system SHALL use Kubernetes informers to maintain a live in-memory cache of cluster resource state, enabling efficient event delivery without polling the Kubernetes API.

Status: **Implemented**

#### Scenario: Informer reacts to state changes
- **WHEN** a Kubernetes resource changes state (pod created, pod failed, etc.)
- **THEN** the informer notifies subscribers and the change is reflected in deployment status updates

#### Scenario: Informer survives transient API server errors
- **WHEN** the Kubernetes API server is temporarily unreachable
- **THEN** the informer reconnects and re-syncs its cache once the API server recovers

### Requirement: Event streaming service bridges informers to SSE
The system SHALL provide a service layer that subscribes to informer events and multiplexes them to SSE connections opened by `DeploymentController` endpoints (see `deployments` spec: "Stream deployment events via SSE" and "Stream pod logs via SSE").

Status: **Implemented**

#### Scenario: Multiple SSE clients for the same deployment
- **WHEN** multiple clients open SSE connections for the same deployment's events
- **THEN** each client receives its own copy of events independently

#### Scenario: Client disconnect cleanup
- **WHEN** an SSE client disconnects
- **THEN** the server-side event subscription is released without error

## Implementation Notes
- Event streaming service: `com.epam.aidial.deployment.manager.kubernetes.event.*`
- Kubernetes informers: `com.epam.aidial.deployment.manager.kubernetes.informer.*`
- Fabric8 Kubernetes client: 7.5.2
- API endpoints are owned by `DeploymentController` — see `deployments` spec for endpoint contracts
- Related specs: `deployments`
