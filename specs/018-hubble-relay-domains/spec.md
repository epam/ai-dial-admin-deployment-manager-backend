# Feature Specification: Hubble Relay Domain Streaming

**Feature Branch**: `018-hubble-relay-domains`
**Created**: 2026-04-23
**Status**: Draft
**Input**: User description: "If Hubble Relay is enabled, the system should support real-time streaming of a list of all domains accessed during the image build and image deploy processes, together with logs and status updates. Each domain entry should indicate whether access was allowed or blocked by Cilium, enabling administrators to review domain usage and identify any blocked domains."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Stream domains in real time during an active image build (Priority: P1)

An administrator triggers an image build and opens the live log stream. During the build, a Kubernetes pod uses BuildKit to build a Docker image and push it to a registry; Cilium enforces network policies on this pod. Alongside existing log lines and status updates, the administrator sees a real-time feed of every external domain the image build pod contacted, each entry showing whether access was allowed or blocked by Cilium. This lets the administrator spot blocked domains immediately — without waiting for the build to finish or digging through raw network observability tooling.

**Why this priority**: This is the core value. Without it, operators have no inline visibility into network access during builds. Early detection of blocked domains shortens the feedback loop for domain whitelist tuning.

**Independent Test**: Trigger a build with Hubble Relay enabled and open the build log stream — `domain` events must appear interleaved with `logs` and `status` events, reflecting actual Cilium verdicts.

**Acceptance Scenarios**:

1. **Given** Hubble Relay is enabled and a build is running, **When** the image build pod contacts an external domain, **Then** a `domain` event is pushed to connected build log stream subscribers within the normal SSE polling window, containing the domain name and verdict (`ALLOWED` or `BLOCKED`)
2. **Given** Hubble Relay is enabled, **When** the image build pod contacts a domain not in the allowed list, **Then** that domain appears in the stream with verdict `BLOCKED`
3. **Given** Hubble Relay is disabled, **When** a client opens the build log stream, **Then** no `domain` events are present and existing `logs`/`status` event behaviour is unchanged

---

### User Story 2 - Review the full domain access list after a build completes (Priority: P2)

An administrator reviews a completed build — particularly one that failed. They retrieve build details and see the complete list of domains the build accessed, with allowed/blocked verdicts, allowing them to identify which blocked domains caused the failure and update the whitelist accordingly.

**Why this priority**: Builds frequently run unattended. Post-mortem review of domain access is essential for diagnosing build failures caused by Cilium blocks.

**Independent Test**: Run a build with Hubble Relay enabled, wait for completion, then call the build details endpoint for that image definition and verify a `domains` list is present in the response with correct verdicts.

**Acceptance Scenarios**:

1. **Given** Hubble Relay is enabled and a build has completed, **When** the administrator calls the build details endpoint, **Then** the response includes a `domains` list, each entry containing the domain name and its Cilium verdict
2. **Given** Hubble Relay is disabled and a build has completed, **When** the build details endpoint is called, **Then** the `domains` list is empty or absent
3. **Given** a build has never been started for an image definition, **When** the build details endpoint is called, **Then** the response is 404

---

### User Story 3 - Replay domain events when reconnecting to a completed build stream (Priority: P3)

A client that missed the live stream (disconnected, reconnected late) opens the log stream for a completed build. All captured domain entries are replayed alongside stored log lines and the terminal status event, so no domain access information is lost.

**Why this priority**: Consistent with existing log-replay behaviour for completed builds. Extends replay to domain entries so the stream endpoint is the single complete source of build information.

**Independent Test**: Complete a build with Hubble Relay enabled, then open the log stream endpoint for the completed build and verify domain events are replayed before the stream closes.

**Acceptance Scenarios**:

1. **Given** Hubble Relay is enabled and a build has completed, **When** a client opens the log stream, **Then** all captured domain events are replayed alongside stored log lines, followed by the terminal status event and automatic stream closure
2. **Given** the completed build had both allowed and blocked domain accesses, **When** the domain events are replayed, **Then** each event accurately reflects the original verdict

---

### User Story 4 - Stream domains in real time for a running deployment (Priority: P4)

An administrator monitors a running deployment. Each deployment runs as a Kubernetes pod using the Docker image produced by the image build process; Cilium enforces network policies on this deployment pod too. The administrator opens the pod log stream and, alongside log lines, sees real-time `domain` events for every external domain the deployment pod is contacting, each with its Cilium verdict. This provides runtime network visibility in the same stream already used for log monitoring.

**Why this priority**: Extends the same observability from build time to runtime. Lower priority than builds because runtime domain policies are configured before deployment (via `allowedDomains`), but visibility into actual runtime access is still valuable.

**Independent Test**: Deploy a service with Hubble Relay enabled, open `GET /api/v1/deployments/{id}/pods/{podId}/logs` for an active pod, and verify `domain` events appear interleaved with `logs` events for external connections the pod makes.

**Acceptance Scenarios**:

1. **Given** Hubble Relay is enabled and a deployment pod is running, **When** the pod contacts an external domain, **Then** a `domain` event is pushed to the pod log stream (`GET /api/v1/deployments/{id}/pods/{podId}/logs`) interleaved with `logs` events, containing the domain name and Cilium verdict
2. **Given** Hubble Relay is enabled and a client reconnects to the pod log stream for a still-running pod, **Then** all domain events captured since the current deployment activation are replayed before live events resume
3. **Given** Hubble Relay is disabled, **When** a client opens the pod log stream, **Then** no `domain` events are present and existing log streaming behaviour is unchanged

---

### Edge Cases

- What if Hubble Relay is enabled but the same domain is contacted multiple times during a build? — Domain entries are deduplicated per build activation by (domain, verdict) pair; if the same domain appears with both `ALLOWED` and `BLOCKED` verdicts, both entries are retained.
- What if Hubble Relay is enabled but observes no flows for an image build pod? — The `domains` list in build details is empty; no `domain` SSE events are emitted.
- What about the many DROPPED DNS flows generated by search-domain resolution (e.g., `auth.docker.io.svc.cluster.local.`)? — These are DNS client noise and must be filtered out before recording. Only flows for bare external FQDNs (query name contains no `.cluster.local` or `.internal.*` suffix) are recorded. For an allowed domain, the search-domain queries appear as DROPPED but do not represent a Cilium policy block; only the bare FQDN query (`auth.docker.io.`) carries the actual verdict.
- What if the cross-cluster connection to Hubble Relay is unavailable at the time of a build or deployment? — The system retries the connection a fixed number of times with a short interval at stream startup; if still unavailable after the retry limit, domain streaming degrades gracefully: the build or deployment proceeds normally, no `domain` events are emitted, and a warning is logged. The build is never failed due to a Hubble Relay connectivity issue.
- What if a client disconnects mid-stream during an active build? — Standard SSE disconnection handling applies; on reconnect, domain events captured so far are replayed.
- What if a build produces a very large number of unique domain accesses? — Deduplication limits the stored count; no artificial cap on unique (domain, verdict) pairs, but deduplication keeps the list manageable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When Hubble Relay is enabled, the system MUST capture, in real time, each external domain accessed by an image build pod (the Kubernetes pod that uses BuildKit to build and push a Docker image), together with the Cilium verdict (`ALLOWED` or `BLOCKED`) for that access
- **FR-002**: When Hubble Relay is enabled, the system MUST capture, in real time, each external domain accessed by a deployment pod (the Kubernetes pod that runs the Docker image produced by the build process), together with its Cilium verdict
- **FR-003**: Captured domain access entries MUST be persisted per build activation, keyed by `imageDefinitionId`; all prior entries for that `imageDefinitionId` MUST be cleared when a new build is started, so entries always reflect the most recent build attempt only, retained for the lifetime of that build record
- **FR-004**: The build log SSE stream (`GET /api/v1/images/builds/{id}/logs`, where `{id}` is the build run identifier for a specific version) MUST include `domain` events when Hubble Relay is enabled, interleaved with existing `logs` and `status` events
- **FR-005**: Each `domain` SSE event MUST contain exactly the domain name and the Cilium verdict (`ALLOWED` or `BLOCKED`); no timestamp is included in the event payload — chronological ordering during replay is determined server-side based on stored observation order
- **FR-006**: The build details endpoint (`GET /api/v1/images/builds/{id}/details`, where `{id}` is the build run identifier for a specific version) MUST include a `domains` list in its response when Hubble Relay is enabled; each entry carries the domain name and verdict
- **FR-007**: When a client opens the build log stream for a completed build (replay mode), all stored log lines MUST be emitted first (in array order), followed by all captured domain events (in observation order), followed by the terminal status event and automatic stream closure. Note: individual log lines do not carry timestamps; domain events are emitted in insertion order after log lines, providing approximate chronological ordering without requiring log-line timestamps.
- **FR-008**: Real-time `domain` events for a running deployment pod MUST be delivered via the existing pod log stream endpoint (`GET /api/v1/deployments/{id}/pods/{podId}/logs`), interleaved with `logs` events, when Hubble Relay is enabled; when a client reconnects to an active pod stream, all domain events captured since the current deployment activation MUST be replayed before live events resume
- **FR-009**: Before recording, DNS flows with cluster-internal or cloud-internal search domain suffixes (e.g., `.svc.cluster.local`, `.cluster.local`, `.internal.*`) MUST be discarded; only bare external FQDNs are processed. After filtering, domain entries MUST be deduplicated by (domain name, verdict) pair — for builds, per build activation; for deployments, per deployment lifetime. The same (domain, verdict) combination is stored and streamed only once within its respective scope
- **FR-010**: When Hubble Relay is disabled, the system MUST NOT emit `domain` SSE events, MUST return an empty or absent `domains` list in build details, and MUST NOT degrade build or deployment behaviour in any way
- **FR-011**: Cross-cluster connectivity failures to Hubble Relay MUST NOT cause image builds or deployments to fail; at stream startup the system MUST retry the connection a fixed number of times with a short interval before degrading gracefully — once the retry limit is exhausted, the build or deployment proceeds normally with no `domain` events emitted and a warning logged
- **FR-012**: The Hubble Relay integration MUST be controlled by a dedicated configuration flag; changing the flag MUST NOT require application redeployment
- **FR-013**: The cross-cluster gRPC connection to Hubble Relay MUST support TLS with server-side certificate validation; the CA certificate or trust store used to validate the Hubble Relay server certificate MUST be configurable
- **FR-014**: All stored domain entries for a deployment MUST be cleared each time that deployment is activated (`POST /api/v1/deployments/{id}/deploy`); entries always reflect the current running instance only

### Key Entities *(include if feature involves data)*

- **Build Domain Entry**: A record of a single observed domain access during an image build run. Key attributes: `imageDefinitionId`, domain name, Cilium verdict (`ALLOWED` or `BLOCKED`). Entries always reflect the most recent build activation for that `imageDefinitionId`; all prior entries are cleared when a new build starts. Entries are deleted when the associated build record is deleted.
- **Deployment Domain Entry**: A record of a single observed domain access by a running deployment. Key attributes: deployment identifier, domain name, Cilium verdict. Deduplicated per deployment run by (domain name, verdict) pair. Persisted for the duration of the active deployment run to support reconnection replay. Entries are cleared on each new deploy activation and deleted when the deployment record is deleted.
- **Cilium Verdict**: An enumeration of Cilium network policy outcomes relevant to external domain access: `ALLOWED` (the flow was forwarded) and `BLOCKED` (the flow was dropped by policy).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An administrator can identify all blocked domains for any completed build without accessing Cilium tooling directly — 100% of blocked domain flows observed by Hubble Relay during the build appear in build details
- **SC-002**: The first `domain` event appears in the build log stream within the same latency window as `logs` and `status` events — domain observability introduces no perceptible additional delay
- **SC-003**: When Hubble Relay is disabled, build and deployment operations complete with identical behaviour to the pre-feature baseline — zero regressions in functionality or performance
- **SC-004**: A client that reconnects to a completed build stream receives all domain events captured during that build — no domain access information is lost due to reconnection
- **SC-005**: Hubble Relay connectivity failures result in zero build failures — 100% of builds succeed regardless of Hubble Relay availability

## Out of Scope

- **Deployment domain details REST endpoint**: This feature does **not** implement a `GET /api/v1/deployments/{id}/details` domain list endpoint. Domain entries for deployments are queryable only via the SSE pod log stream (`GET /api/v1/deployments/{id}/pods/{podId}/logs`) while the stream is active or the deployment is running.

## Assumptions

- Hubble Relay is installed in the **untrusted cluster** — the separate Kubernetes cluster where image build pods (BuildKit) and deployment pods run — not in the trusted cluster where the Deployment Manager itself runs. The Deployment Manager connects to Hubble Relay over the cross-cluster network link (the same link used to manage Kubernetes resources on the untrusted cluster).
- Hubble Relay is an optional, independent feature flag (`HUBBLE_RELAY_ENABLED`, default `false`); it is independent of `CILIUM_NETWORK_POLICIES_ENABLED` but only produces meaningful results when Cilium is enforcing network policies
- Domain names are captured from **L7 DNS proxy flows** (`dns-request proxy` type): Cilium intercepts DNS queries from pods to CoreDNS and records the queried domain name and the policy verdict (`FORWARDED` or `DROPPED`)
- Kubernetes pods use `ndots:5` DNS configuration, causing the resolver to try multiple search-domain-qualified queries (e.g., `auth.docker.io.svc.cluster.local.`) before the bare FQDN (`auth.docker.io.`). These search-domain queries must be filtered out — only the bare external FQDN query carries the meaningful Cilium policy verdict. The filter rule: discard any DNS query whose name (after stripping the trailing `.`) contains cluster-internal or cloud-internal DNS suffixes (`.cluster.local`, `.svc.cluster.local`, `.internal.*`)
- Both A (IPv4) and AAAA (IPv6) queries are emitted for the same bare FQDN; the `(domain, verdict)` deduplication rule collapses these into one entry per domain per verdict
- `imageDefinitionId` is a stable identifier that persists across retried builds (status ∈ `{NOT_BUILT, BUILD_FAILED, BUILD_STOPPED}` allows a new build attempt on the same record); domain entries are keyed solely by `imageDefinitionId` and cleared at the start of each new build activation
- The existing `image_build_logs` table pattern (separate table keyed by `imageDefinitionId`, cascade-deleted) is the appropriate storage model for build domain entries; no version column is needed since entries always reflect the most recent build activation
- Deployment domain entries reflect the current running instance only; they are cleared on each new deploy activation and deleted when the deployment record is deleted
- Deduplication is enforced at storage time; the live SSE stream may transiently emit a duplicate before deduplication is confirmed, but the persisted record and replay are always deduplicated
- The cross-cluster gRPC connection to Hubble Relay uses TLS with server-side certificate validation (not mutual TLS); the Deployment Manager validates the Hubble Relay server certificate but does not present a client certificate — deferred to the NodePort/LoadBalancer upgrade path when the port-forward approach is in use (port-forward tunnels gRPC as localhost plaintext; transport security is provided by the Kubernetes API TLS + RBAC layer)
- The `docs/configuration.md` file MUST be updated to document `HUBBLE_RELAY_ENABLED` and any related Hubble Relay connection properties, including TLS configuration

## Clarifications

### Session 2026-04-23

- Q: What security mode should the cross-cluster gRPC connection to Hubble Relay use? → A: TLS with server-side certificate validation only (no mutual TLS)
- Q: Should deployment domain entries be deduplicated, and by what rule? → A: Deduplicate per deployment lifetime by (domain, verdict) pair — same rule as builds
- Q: What happens to deployment domain entries when a deployment is stopped and re-deployed? → A: Entries are cleared on each new deploy activation; entries always reflect the current running instance only
- Q: Which endpoint should be used for real-time deployment domain streaming? → A: `GET /api/v1/deployments/{id}/pods/{podId}/logs` — domain events interleaved with pod log events (no separate endpoint)
- Q: Should domain events be replayed when a client reconnects to the pod log stream for a still-running deployment? → A: Yes — all domain events captured since the current deploy activation are replayed on reconnect

### Session 2026-04-24

- Q: How are build domain entries scoped when a build is retried for the same `imageDefinitionId`? → A: `imageDefinitionId` is stable across retries; domain entries are keyed solely by `imageDefinitionId` and cleared at the start of each new build activation, so entries always reflect the most recent attempt only
- Q: Does `{id}` in `GET /api/v1/images/builds/{id}/logs` and `/details` refer to the `imageDefinitionId`? → A: Yes — `{id}` is the `imageDefinitionId`, which unambiguously addresses a single image definition's logs and domain entries
- Q: In what order should domain events be replayed with log lines for a completed build stream? → A: Chronological order — domain events interleaved with log lines by observation timestamp
- Q: Should the `domain` SSE event payload include an observation timestamp? → A: No — payload contains only domain name and verdict; chronological replay order is managed server-side
- Q: Should the system retry the Hubble Relay connection on failure, and how? → A: Yes — retry a fixed number of times with a short interval at stream startup, then degrade gracefully (no domain events, warning logged) if still unavailable
