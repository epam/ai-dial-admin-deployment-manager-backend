# Research: Hubble Relay Domain Streaming

## Decision 1: Cross-cluster Hubble Relay Connectivity

**Decision**: Kubernetes API port-forward via fabric8 `LocalPortForward`.

**Rationale**: The Deployment Manager already has an authenticated K8s API connection to the untrusted cluster (`deployKubeClient` bean, fabric8 7.5.2). Port-forward tunnels the gRPC channel through this existing WebSocket link — no firewall rules, no new service types, no infrastructure coordination. Fabric8 7.5.2 provides `client.pods().inNamespace(...).withName(...).portForward(port)` via `LocalPortForward`. Connection stability for long-lived gRPC streams is managed by the reconnect/retry mechanism (FR-011).

**Alternatives considered**:
- NodePort + firewall rule: direct gRPC, simpler code, but requires infrastructure change and cloud-team coordination. Deferred as upgrade path if port-forward proves insufficient.
- LoadBalancer service: production-grade stable IP, but cloud LB cost and provisioning lead time. Same upgrade path as NodePort.
- K8s API service proxy (`/api/v1/namespaces/.../services/.../proxy/`): HTTP/1.1 only — does not support gRPC HTTP/2 streaming. Eliminated.

**TLS note**: For the port-forward approach, gRPC-level TLS (`FR-013`) is not applicable — the channel is `localhost:N` (plaintext). Security comes from the K8s API TLS + RBAC layer, which already validates the service account token. If connectivity is later upgraded to NodePort/LoadBalancer, gRPC TLS can be enabled by switching `ManagedChannelBuilder` from `usePlaintext()` to `useTransportSecurity()` with a configured trust store. This change is isolated to `HubbleRelayGrpcChannelFactory`.

**Shared-channel model**: A single `ManagedChannel` (one port-forward WebSocket) is shared across all concurrent observations. N parallel builds open N gRPC *streams* over the shared HTTP/2 connection — not N separate port-forwards. Channel health is checked on each `getSharedChannel()` call; a `TRANSIENT_FAILURE` or `SHUTDOWN` state triggers recreation of the port-forward and channel. Reconnection timing is governed by `observeWithRetry` in `HubbleDomainFlowService`.

---

## Decision 2: Hubble Relay gRPC Stubs

**Decision**: Copy Cilium proto files into `src/main/proto/` and generate stubs via the `com.google.protobuf` Gradle plugin.

**Rationale**: Cilium does not publish a standalone Java artifact for the Hubble observer API on Maven Central. The proto files are available in the `cilium/hubble` repository under `vendor/github.com/cilium/cilium/api/v1/observer/`. Bundling them in the project gives full control over the generated code and eliminates a runtime dependency on an external registry.

Required proto files (minimal set):
- `observer/observer.proto` — `Observer` service with `GetFlows` and `GetNodes` RPCs
- `flow/flow.proto` — `Flow`, `DNS`, `Layer4`, `Verdict` messages
- `flow/types.proto` — `FlowType` enum
- `google/protobuf/timestamp.proto` — standard Google proto (available via `protobuf-java`)

New Gradle dependencies:
```
implementation("io.grpc:grpc-netty-shaded")          // version already constrained to 1.75.0
implementation("io.grpc:grpc-stub:1.75.0")
implementation("io.grpc:grpc-protobuf:1.75.0")
implementation("com.google.protobuf:protobuf-java:3.25.3")
```

New Gradle plugin: `id 'com.google.protobuf' version '0.9.4'`

**Alternatives considered**:
- Use existing `io.kubernetes:client-java` gRPC transport: client-java provides gRPC infra but not Hubble-specific stubs. Still requires proto generation. No advantage over direct approach.
- Hand-write gRPC stubs: fragile and maintenance-heavy. Rejected.

---

## Decision 3: Domain Entry Storage — Deduplication Strategy

**Decision**: Enforce deduplication at the database level via a `UNIQUE` constraint on `(scope_id, domain, verdict)`. Application code uses `INSERT ... ON CONFLICT DO NOTHING` (Postgres) / `INSERT IGNORE` (H2 workaround) / `TRY { insert } CATCH ConstraintViolation` (SQL Server). A surrogate auto-increment `id` column provides insertion order for chronological replay.

**Rationale**: DB-level dedup is the strongest guarantee — concurrent observer threads cannot produce duplicates even if the application logic emits the same (domain, verdict) pair simultaneously. The `UNIQUE` constraint replaces the need for distributed locking.

**Alternatives considered**:
- Application-level `existsBy` check before insert: race condition between check and insert. Rejected.
- Store timestamp and update on duplicate: timestamps not needed per spec (server-side ordering only). Simpler to use `DO NOTHING`.

---

## Decision 4: Observer Lifecycle Management

**Decision**: `HubbleDomainFlowService` (service layer) manages one `ScheduledFuture` per active observation scope (imageDefinitionId for builds, deploymentId for deployments), stored in thread-safe `ConcurrentHashMap` fields. Observations are started/stopped by callers (`ImageBuildRunner`, `AbstractDeploymentManager`).

**Rationale**: A per-scope handle map allows precise start/stop without a global cleanup sweep. Virtual threads (Java 21) are appropriate for the blocking gRPC stream read loop — they park cheaply. Channel lifecycle (port-forward + `ManagedChannel`) is managed exclusively by `HubbleRelayGrpcChannelFactory.getSharedChannel()` / `close()`, not by individual observers.

**Alternatives considered**:
- Single global thread pool with task tracking: harder to cancel per-scope. Rejected.
- Reactor/Project Reactor: adds a new reactive dependency not present in the codebase. Rejected.

---

## Decision 5: DNS Search Domain Noise Filtering

**Decision**: Discard any DNS query flow whose query name (after stripping the trailing `.`) ends with `.local`, or contains `.cluster.local`, `.svc.cluster.local`, or `.internal.` (covers Azure `.bx.internal.cloudapp.net` and similar cloud-internal suffixes).

**Rationale**: Kubernetes pods use `ndots:5` DNS config, generating 4–8 DROPPED flows per external domain lookup against cluster search domains before the bare FQDN query. For *allowed* domains these DROPPED search-domain queries are DNS client noise, not Cilium policy decisions. Only the bare FQDN query carries the meaningful verdict. See log samples in `specs/018-hubble-relay-domains/spec.md` Assumptions section.

Filter rule implemented in `HubbleDomainFilter.isExternalDomain(String queryName)`:
- Strip trailing `.`
- Return `false` if the result ends with `.local` OR contains `.internal.` OR contains `.cluster.`
- Otherwise return `true`

---

## Decision 6: SSE Domain Event Emission in Existing Streams

**Decision**:
- **Build stream** (`ImageBuildLogsService`): add domain entry polling alongside existing log polling. On each poll interval, read new domain entries from `ImageBuildDomainEntryRepository.findByImageDefinitionIdOrderById(id)` from the last emitted index, emit each as `event: domain`.
- **Deployment pod stream** (`DeploymentLogsService`): run a parallel polling task (same SSE executor) alongside the K8s log stream. The domain task polls `DeploymentDomainEntryRepository` by index and emits `event: domain`. Synchronized emission via `synchronized(emitter)` guard.

**Rationale**: Both approaches keep domain events in the same SSE connection as logs. The build stream already polls DB — extending it is minimal. The deployment pod stream reads K8s logs live — a parallel polling task is the least-invasive addition that avoids restructuring `PodLogReader`.

**Alternatives considered**:
- Separate SSE endpoint for domain events: contradicts spec (FR-004, FR-008 require interleaving in existing streams).
- Push-based in-memory queue: adds concurrency complexity. Polling from DB is simpler and supports replay naturally.

---

## Decision 7: RBAC Requirement for Port-Forward

**Decision**: Add `pods/portforward` to the service account permissions on the `cilium` namespace in the untrusted cluster.

**Rationale**: The fabric8 `LocalPortForward` API calls the K8s API endpoint `POST /api/v1/namespaces/{ns}/pods/{name}/portforward` via WebSocket. This requires the `pods/portforward` sub-resource verb. In TOKEN mode (`withTrustCerts(true)`), the token must have this permission on the `cilium` namespace.

This permission is additive to the existing RBAC documented in `specs/cilium/spec.md`.
