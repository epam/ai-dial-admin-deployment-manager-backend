# Phase 0 Research: GPU Metrics via DCGM

All open questions were resolved with the stakeholder before planning. This records the decisions, rationale, and alternatives — the format `/speckit.plan` expects.

## Decision 1 — Telemetry transport

**Decision**: Scrape the NVIDIA `dcgm-exporter` DaemonSet pods' Prometheus `/metrics` through the existing Kubernetes **API-server pod-proxy** (`K8sClient.scrapePodMetrics`), and parse with the existing hand-rolled `PrometheusTextParser`.

**Rationale**:
- Zero new infrastructure beyond the exporter (a cluster prerequisite already implied by the metrics ADR follow-up (b)). Reuses existing kube auth/TLS and works whether the deployment manager runs in- or out-of-cluster (pod IPs aren't routable out-of-cluster; the API server is).
- Request-triggered only, satisfying constitution Principle III (no background polling).
- DCGM exposition is standard Prometheus text — the in-repo `PrometheusTextParser` already parses this format, so no new dependency.
- Consistent with the serving-metrics path already shipped in 023 (Option A of the telemetry ADR).

**Alternatives considered**:
- **Query an existing cluster Prometheus (PromQL, ADR Option B)** — gives true windowed rates but adds a hard runtime dependency on Prometheus per cluster and a new query/HTTP layer. Rejected for the snapshot: not guaranteed present, heavier.
- **Service-proxy to the dcgm-exporter Service** — one target, but the returned series still span every node and must be filtered by pod label; loses the co-location optimization and still needs the same parse/join. Rejected in favour of pod-proxy for parity with the existing transport and to enable co-located scoping.
- **`nvidia-smi` via pod exec** — brittle, high-privilege, per-pod round-trips. Rejected (also rejected in the issue).

## Decision 2 — Scrape scope (which exporter pods per request)

**Decision**: Scrape only the exporter pods **co-located** on the nodes actually running this deployment's GPU pods.

**Rationale**: A DaemonSet runs one exporter pod per node. Scraping every exporter pod on a large GPU cluster would be many pod-proxy round-trips per snapshot; scraping only the nodes hosting this deployment's pods keeps cost proportional to the deployment. GPU deployments typically have 1 node in play.

**Consequence / cost**: requires knowing each deployment pod's node. `PodInfo` does not carry `nodeName` today, so we add it (populated from `pod.getSpec().getNodeName()` in `AbstractDeploymentManager.toPodInfo`). Exporter-pod→node comes from the exporter pod's own `spec.nodeName` (or the `Hostname` label DCGM emits — `nodeName` is the reliable one).

**Alternatives considered**:
- **Scrape all exporter DaemonSet pods and filter series by pod label** — simpler (no node lookup) but cost scales with GPU-node count. Rejected for efficiency; noted as a fallback if node resolution proves unavailable.

## Decision 3 — Exposed fields & units

**Decision**: Populate three per-pod fields:
- `gpuUtilization` — ratio 0–1, from `DCGM_FI_DEV_GPU_UTIL` (percent 0–100) ÷ 100, **averaged** across a pod's GPUs.
- `gpuMemoryBytes` — used bytes, from `DCGM_FI_DEV_FB_USED` (MiB) × 1024² , **summed** across a pod's GPUs.
- `gpuMemoryTotalBytes` — **NEW** field, from `(DCGM_FI_DEV_FB_USED + DCGM_FI_DEV_FB_FREE)` (MiB) × 1024², summed across a pod's GPUs.

**Rationale**: A memory gauge needs a denominator; used-only can't render a percentage. Total is trivially available from the same exporter series. Additive field is backward-compatible. Sum-for-memory / average-for-utilization is the intuitive per-pod aggregation across multiple GPUs.

**Unit notes**: DCGM `FB_USED`/`FB_FREE` are reported in **MiB**. Utilization is an integer percent. The public contract is bytes + ratio, matching the existing `gpuMemoryBytes` semantics and the CPU/memory block's byte convention.

**Alternatives considered**: Only the two existing fields (matches the issue text literally) — rejected because the gauge is the stated user goal (SC-002) and needs a max.

## Decision 4 — Enablement & degradation

**Decision**: New nested config `app.metrics.scrape.gpu.*`, `enabled` default **true**, mirroring `resource-usage.enabled`. GPU collection is attempted only for GPU-requesting deployments. `resources.gpu` availability carries a distinct reason per case:

| Situation | `resources.gpu` | Reason (human-readable) |
|---|---|---|
| GPU data present for ≥1 pod | available | — |
| Deployment requests no GPU | unavailable | "deployment does not request GPU" |
| Feature disabled by config | unavailable | "GPU metrics collection is disabled by configuration" |
| No running pods | unavailable | (no-pods reason) |
| Exporter absent / unreachable / no data | unavailable | "GPU telemetry unavailable (DCGM exporter not present or unreachable?)" |

**Rationale**: Matches the graceful-degradation contract already established for `resources.usage` and the serving blocks; a missing exporter must never 500 the snapshot.

## Decision 5 — GPU gating from deployment resources

**Decision**: Determine "requests GPU" by reading `nvidia.com/gpu` from the deployment's `Resources` (`limits`/`requests`, values are strings) and testing `> 0`. Do this before any cluster round-trip.

**Rationale**: Avoids wasted scrapes for the common non-GPU case (SC-004). `Resources` is already on the `Deployment` domain object (`Deployment.getResources()`), and `nvidia.com/gpu` is the exact key already validated by `ResourcesValidator`.

**Edge**: fractional/any positive value qualifies; absent key or "0" does not.

## Decision 6 — DCGM label mapping / attribution

**Decision**: Join DCGM series to pods on the `(namespace, pod)` labels the exporter attaches when run with Kubernetes pod mapping (`DCGM_EXPORTER_KUBERNETES=true` / kube pod-resources). Filter to this deployment's pod names in the deployment namespace; aggregate per pod.

**Assumptions to validate in the live cluster** (captured as fixture + config, low code-risk):
- Exact label names (`pod`, `namespace`) — some setups relabel to `exported_pod`/`exported_namespace` only when scraped *through Prometheus*; scraping the exporter **directly** (as we do) yields the raw `pod`/`namespace`. The parser reads whichever the config names; defaults to `pod`/`namespace`.
- Default exporter port `9400`, metrics path `/metrics`, namespace and pod label selector — all configurable (Decision 4 config block) so a cluster that differs needs no code change.

**Alternatives considered**: joining via the kubelet PodResources API for GPU→pod mapping — redundant, since the exporter already does this mapping and exposes it as labels.

## Open items → resolved

All `NEEDS CLARIFICATION` resolved; no blocking unknowns remain. Live-cluster label/port specifics are absorbed by configuration + a representative test fixture, so they do not block implementation.
