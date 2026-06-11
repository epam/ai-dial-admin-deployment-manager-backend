# Feature Specification: Unified Deployment Metrics API (PoC — Live Snapshot)

**Feature Branch**: `feat/model-metrics-poc`
**Created**: 2026-06-05
**Status**: Implemented — code, tests, docs, and the capability spec (`specs/deployment-metrics/spec.md`) shipped. Resource metrics (replica counts and per-pod CPU/memory) are reported for every deployment type; serving-quality metrics are scraped for INFERENCE deployments. Dev-cluster PoC acceptance performed live against a KServe vLLM (V1) deployment, with real `/metrics` fixtures captured and engine-drift corrections folded back in (vLLM V1 ITL rename, `error` finished_reason)
**Capability**: N/A — creates new capability deployment-metrics
**Input**: User description: "Implement PoC of unified model metrics API feature" — design investigation for [issue #162](https://github.com/epam/ai-dial-admin-deployment-manager-backend/issues/162).

> Design source of record: the capability spec `specs/deployment-metrics/spec.md` — its
> engine-availability matrix is the authoritative field-level reference, and the OpenAPI contract
> `specs/023-deployment-metrics-api/contracts/deployment-metrics-api.yaml` is the authoritative
> snapshot contract. This spec defines the user-facing behaviour the PoC must deliver.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Live serving-quality snapshot for an inference model deployment (Priority: P1)

An operator has deployed a model (e.g. from HuggingFace) and wants to answer "is my new model fast
enough?" without leaving the admin surface or standing up external monitoring. They request a live
metrics snapshot for the deployment and immediately see serving-quality signals — time-to-first-token,
inter-token latency, prompt/generation token throughput, queue depth, running requests, and KV-cache
utilization — expressed in engine-neutral names and explicit units.

**Why this priority**: This is the core value of the feature and the primary spike demo scenario.
Without it nothing else matters; with only this, the PoC is already a viable MVP for vLLM-class
inference deployments.

**Independent Test**: Deploy one vLLM-class inference model on the dev cluster, generate a few chat
completions, request the snapshot, and verify live serving-quality values appear with correct units
and identification of the engine and pod that was read.

**Acceptance Scenarios**:

1. **Given** a running inference deployment with at least one Ready pod, **When** the operator requests
   the metrics snapshot, **Then** the response contains serving-quality metrics in the unified schema,
   the detected engine family, the name of the pod the values were read from, and a collection timestamp.
2. **Given** the deployment has served requests, **When** the snapshot is requested, **Then** latency
   distributions (time-to-first-token, inter-token latency, end-to-end latency) include mean, p50, p95,
   p99 and an observation count.
3. **Given** values derived from cumulative counters (e.g. tokens/second, error ratio), **When** they are
   returned, **Then** they are explicitly labelled as lifetime aggregates (window: "lifetime") and the raw
   cumulative counters are included so consumers can derive their own windowed rates.
4. **Given** the engine the deployment runs is one of the recognized families (vLLM, TGI, SGLang),
   **When** the snapshot is requested, **Then** the engine family is detected automatically — the operator
   never has to declare it.

---

### User Story 2 - Resource context for any deployment type (Priority: P2)

An operator requests the snapshot for a non-inference deployment (e.g. an MCP server, application,
adapter, or interceptor) and still gets replica counts and per-pod CPU/memory in the same response
shape — so a client (UI panel, script) written against the snapshot works for every deployment kind,
with the serving-quality blocks honestly marked unavailable when they don't apply.

**Why this priority**: Resource headroom and replica context are useful for every workload, not only
model deployments. Reporting them uniformly across types — rather than rejecting non-model
deployments — is what makes the endpoint a general deployment-metrics surface.

**Independent Test**: Request the snapshot for a non-inference deployment and verify the payload
validates against the same contract as User Story 1, with replica counts (and per-pod usage when the
resource-metrics service is present) populated and the serving block marked unavailable.

**Acceptance Scenarios**:

1. **Given** a running deployment of any type with at least one pod, **When** the operator requests the
   metrics snapshot, **Then** the response uses the same schema and reports total/ready replica counts
   and per-pod CPU/memory usage.
2. **Given** a non-inference deployment, **When** the snapshot is requested, **Then** the serving and
   operational blocks are marked unavailable with a reason while the resource block is still populated —
   never an error.

---

### User Story 3 - Honest partial answers when telemetry is missing (Priority: P2)

An operator requests metrics for a deployment that is degraded, idle, or running in a cluster lacking
optional telemetry plumbing. Instead of a failure, they get a successful partial response in which each
missing block is explicitly marked unavailable with a human-readable reason — so they can tell "the
model has no metrics because it has no running pods" apart from "the platform broke".

**Why this priority**: The spike makes graceful degradation a hard contract rule ("never 500 for
missing telemetry"). Operators most need metrics exactly when things are unhealthy, so the degraded
paths are as user-visible as the happy path.

**Independent Test**: Exercise each degradation condition (no Ready pods, unreachable metrics source,
unrecognized engine, absent cluster resource-metrics service) and verify every one yields a successful
partial response carrying a reason, with the remaining blocks still populated.

**Acceptance Scenarios**:

1. **Given** a supported deployment with no Ready pods, **When** the snapshot is requested, **Then** the
   response succeeds with the affected blocks marked unavailable and the reason stating no pods were
   available to read.
2. **Given** a Ready pod whose metrics endpoint is unreachable or returns an error, **When** the snapshot
   is requested, **Then** the response succeeds with the serving block unavailable and the reason recorded.
3. **Given** a deployment whose engine cannot be recognized from its exposed metrics, **When** the
   snapshot is requested, **Then** engine is reported as unknown, serving metrics are marked unavailable
   with that reason, and any independently obtainable blocks (e.g. replicas, pod resource usage) are still
   returned.
4. **Given** a deployment of a type that does not expose serving-quality metrics (anything other than
   inference), **When** the snapshot is requested, **Then** the serving and operational blocks are
   marked unavailable with a reason while the resource block is still returned — the request succeeds.
5. **Given** a deployment id that does not exist, **When** the snapshot is requested, **Then** the
   response is the platform's standard not-found error.

---

### User Story 4 - Resource usage context alongside serving metrics (Priority: P3)

An operator checking "do I need to right-size before paying for more GPUs?" sees, in the same snapshot,
how many replicas exist and are ready, and each pod's CPU and memory consumption — giving resource
headroom context next to the serving-quality signals.

**Why this priority**: Valuable context but secondary to the serving-quality core; it also depends on a
cluster capability (resource-metrics service) that may be absent, in which case the rest of the snapshot
still delivers full value.

**Independent Test**: Request the snapshot on a cluster with the resource-metrics service present and
verify per-pod CPU/memory and replica counts; disable/absent service and verify the block degrades with
a reason while serving metrics remain.

**Acceptance Scenarios**:

1. **Given** a running supported deployment, **When** the snapshot is requested, **Then** the response
   includes total and ready replica counts and, for each pod, CPU and memory usage with explicit units.
2. **Given** the cluster lacks the resource-metrics capability, **When** the snapshot is requested,
   **Then** the resource-usage block is marked unavailable with a reason and all other blocks are
   unaffected.
3. **Given** GPU utilization/memory fields in the schema, **When** the cluster prerequisite for GPU
   telemetry is not installed (the PoC default), **Then** those fields are present in the contract but
   reported unavailable — KV-cache utilization serves as the engine-level GPU-pressure proxy.

---

### Edge Cases

- **Engine restart resets lifetime counters**: counter-derived values silently restart from zero; the
  explicit `window: "lifetime"` label and collection timestamp let consumers detect and tolerate this.
- **Multiple Ready pods**: the PoC reads exactly one Ready pod for serving metrics and names it in the
  payload; cross-pod aggregation is explicitly out of scope (see `specs/deployment-metrics/spec.md`). When the Ready pods
  span KServe components (a chained `TEXT_CLASSIFICATION` deployment has both a `predictor` and a
  `transformer` pod under one InferenceService), the `predictor` pod is selected — the engine runs there,
  while the transformer exposes no engine metrics. Absent component labels (KNative/raw inference,
  single-pod), the first Ready pod is used.
- **Deployment exists but is undeployed/stopped**: behaves as the "no Ready pods" degradation, not an
  error.
- **Rapid repeated polling** (e.g. an auto-refreshing UI): responses may be served from a short-lived
  cache; staleness is bounded to a few seconds so the control plane is not hammered.
- **Feature disabled by platform configuration**: the capability can be switched off entirely; requests
  then receive a predictable, documented response rather than partial behaviour.
- **Unknown or mixed metric vocabulary** (engine upgrade renames metrics): unrecognized names are
  ignored; renamed metrics that no longer match are reported unavailable rather than wrong.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide an on-demand live metrics snapshot for a single deployment of
  any type. Resource metrics (replica counts and per-pod CPU/memory) MUST be reported for every
  deployment type; serving-quality and operational metrics MUST be collected for inference deployments
  and marked unavailable with a reason for all other types.
- **FR-002**: The system MUST respond to unknown deployment ids with the platform's standard not-found
  error, using the platform's standard error shape (per `specs/api-conventions/spec.md`).
- **FR-003**: The snapshot MUST express all metrics in a unified, engine-neutral schema with explicit
  units, covering: serving quality (time-to-first-token, inter-token latency, prompt and generation
  tokens/second, queue depth, running requests, KV-cache utilization), resources (replica totals and
  readiness, per-pod CPU and memory, GPU fields), and operational signals (request error ratio,
  end-to-end latency distribution) — per the engine-availability matrix in `specs/deployment-metrics/spec.md`.
- **FR-004**: Every metric block MUST carry an availability marker; when a block is degraded or absent
  the marker MUST include a human-readable reason.
- **FR-005**: Missing telemetry MUST NOT produce a server error: no Ready pods, unreachable metrics
  source, unrecognized engine, or absent cluster resource-metrics capability each null out only the
  affected block(s) while the request still succeeds with a partial payload.
- **FR-006**: The system MUST detect the engine family automatically (vLLM, TGI, SGLang via their
  exposed metric vocabularies) and report it in the response; an unrecognized engine is reported as
  unknown with serving metrics unavailable.
- **FR-007**: Counter-derived values MUST be labelled with their aggregation window (always "lifetime"
  in the PoC), and the raw cumulative counters MUST be included under unified names so consumers can
  derive windowed rates themselves.
- **FR-008**: Latency metrics MUST be reported as distribution summaries (mean, p50, p95, p99,
  observation count), approximated from a single point-in-time reading.
- **FR-009**: The response MUST identify the pod the engine metrics were read from and the collection
  timestamp; the PoC reads exactly one Ready pod, preferring the `predictor` component when the Ready
  pods carry KServe component labels (so a chained deployment's metrics-less transformer pod is skipped).
- **FR-010**: Metric collection MUST happen only in response to an operator request (request-triggered),
  never via background polling, and repeated requests within a short interval MAY be served from a
  cache with bounded staleness to limit load on the cluster control plane.
- **FR-011**: Operators MUST be able to disable the capability, tune the per-collection time budget and
  cache lifetime, and independently toggle the pod resource-usage block via platform configuration,
  documented in `docs/configuration.md`.
- **FR-012**: GPU utilization and GPU memory fields MUST exist in the contract but be reported
  unavailable when the cluster-level GPU telemetry prerequisite is absent (the PoC default).
- **FR-013**: The snapshot contract MUST be forward-compatible with the designed (not implemented)
  time-range variant: unified metric names and availability semantics are identical, so clients written
  against the snapshot keep working when history arrives.
- **FR-014**: Any additional cluster permissions the feature needs MUST be limited to read-only access
  and documented for the deployment runbook/Helm chart.

### Key Entities

- **Deployment Metrics Snapshot**: the top-level response — collection timestamp, detected engine
  family, scraped pod name, aggregation window label, availability map, and the three metric blocks
  plus raw counters.
- **Metric Block**: a cohesive group of related metrics (serving quality / resources / operational)
  that is populated or declared unavailable as a unit.
- **Availability Marker**: per-block flag plus optional human-readable reason explaining degradation.
- **Distribution Summary**: mean, p50, p95, p99, and observation count for a latency-style metric.
- **Engine Family**: the recognized serving engine vocabulary (vLLM, TGI, SGLang, unknown) that
  determines how raw engine metrics map to the unified schema.
- **Raw Counter Set**: cumulative engine counters under unified names, enabling client-side rate
  derivation and future time-series backends.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can see live serving-quality metrics for a supported deployment within
  5 seconds of asking, using only the platform — no external monitoring tools, dashboards, or new
  cluster infrastructure.
- **SC-002**: 100% of the enumerated missing-telemetry conditions (no Ready pods, unreachable metrics
  source, unrecognized engine, absent resource-metrics service) produce a successful partial response
  with a stated reason — zero internal-error responses across the degradation matrix.
- **SC-003**: A client written once against the snapshot contract consumes every deployment kind
  unchanged — an inference deployment showing live TTFT, tokens/second, and KV-cache utilization, and a
  non-inference deployment showing replica counts and per-pod resource usage with the serving block
  marked unavailable.
- **SC-004**: Every numeric value in the response is interpretable without out-of-band knowledge: it
  carries an explicit unit and, where counter-derived, an explicit window label.
- **SC-005**: Real captured engine metric outputs from the dev-cluster verification (vLLM) are checked
  in as test fixtures, and the engine-name mappings in the engine-availability matrix (`specs/deployment-metrics/spec.md`) are confirmed or corrected
  against them.

## Assumptions

- **PoC scope is snapshot-only**: the time-range API is designed but explicitly not implemented (see
  the OpenAPI contract `contracts/deployment-metrics-api.yaml`); long-term retention, alerting, UI
  implementation, GPU telemetry rollout, multi-pod aggregation, and metrics-driven autoscaling are
  follow-ups (see the sized tickets in `specs/deployment-metrics/spec.md`).
- **Engines already expose metrics**: target serving engines publish standard-format metrics on their
  existing serving port; no deployment-manifest changes are needed to enable collection.
- **Direct read, no new infrastructure**: per the telemetry-foundation ADR (`specs/deployment-metrics/spec.md`), the PoC reads engine metrics live through
  the cluster's existing management API path with existing credentials; no metrics-storage stack is
  installed.
- **Lifetime windows are acceptable for the PoC**: without a time-series backend, counter-derived rates
  are since-engine-start aggregates and are honestly labelled as such.
- **Cluster resource-metrics service is normally present** in managed clusters; its absence is a
  supported degradation, not an error.
- **Access control**: the endpoint inherits the existing deployment-API authorization model; no new
  roles or permissions are introduced for callers.
- **Engine detection by exposed vocabulary is sufficient for the PoC**; persisting the serving runtime
  at deploy time is a recorded follow-up ((c) in `specs/deployment-metrics/spec.md`).
- **Verification against live engines is part of the PoC**: exact upstream metric names drift between
  engine versions, so dev-cluster captures are required before the mappings are trusted (see the
  engine-availability matrix note in `specs/deployment-metrics/spec.md`).
