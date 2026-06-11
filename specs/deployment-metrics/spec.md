# Model Metrics

## Purpose
This spec describes the unified deployment metrics API — an on-demand live metrics snapshot for any deployment. Resource metrics (replica counts and per-pod CPU/memory) are reported for every deployment type. For INFERENCE deployments, engine-specific Prometheus metrics (generative engines vLLM/TGI/SGLang, and the KServe Python ModelServer behind non-generative classification/embedding/custom predictors) are additionally scraped from the Ready predictor pod through the Kubernetes API-server pod proxy and normalized to a single engine-neutral schema so clients never depend on a particular engine's vocabulary. For a chained KServe InferenceService the transformer pod is also scraped so its pre/post-processing latency combines with the predictor's inference latency.

Status: **Implemented** *(Implemented via 023-deployment-metrics-api — PoC scope: live snapshot only)*

Origin: [issue #162](https://github.com/epam/ai-dial-admin-deployment-manager-backend/issues/162). This spec is the design source of record — the telemetry-foundation ADR, the engine-availability matrix, and the sized follow-up tickets are captured below. OpenAPI contract: `specs/023-deployment-metrics-api/contracts/deployment-metrics-api.yaml`.

## Key Terms
- **Unified schema**: engine-neutral metric names with explicit units, grouped into `serving`, `resources`, and `operational` blocks plus a `rawCounters` echo of directly-exposed cumulative counters.
- **Engine family**: the recognized serving-engine vocabulary — `VLLM`, `TGI`, `SGLANG`, `KSERVE_MODELSERVER`, or `UNKNOWN`. The generative engines are identified by metric-name prefix sniffing (`vllm:` / `tgi_` / `sglang:`); `KSERVE_MODELSERVER` exposes unprefixed `request_*_seconds` histograms and is identified by the `request_predict_seconds` metric name declared in a `# TYPE` comment (so it is recognized even before the model serves its first request, when the label-gated histograms emit no samples). Non-inference deployments are not scraped and report engine `UNKNOWN`.
- **Availability marker**: per-block `{available, reason}` entry; every response carries one entry per block key (`serving`, `operational`, `resources`, `resources.usage`, `resources.gpu`).
- **Lifetime window**: counter-derived values are aggregates since engine process start, labelled `window: "lifetime"`. There is no time-series backend in the PoC.
- **Pod proxy scrape**: `GET /api/v1/namespaces/{ns}/pods/http:{pod}:{port}/proxy/metrics` through the kube API server — no new infrastructure, works when the deployment manager runs outside the cluster. Used only for INFERENCE deployments (vLLM-class engines expose the standard `/metrics` exposition path).

## Requirements

### Requirement: Live metrics snapshot endpoint
The system SHALL expose `GET /api/v1/deployments/{id}/metrics` returning a live unified metrics snapshot for a deployment of any type. Resource metrics (replica counts and per-pod CPU/memory) are reported for every type; serving-quality metrics are scraped for INFERENCE deployments and marked unavailable for all others. An unknown id yields HTTP 404 (`ErrorView`, per `api-conventions`). For INFERENCE deployments the Ready predictor pod is scraped per request and named in `scrapedPod`; for a chained KServe Python ModelServer deployment the Ready transformer pod is additionally scraped to combine pre/post-processing latency with predictor inference latency.

Status: **Implemented** *(Implemented via 023-deployment-metrics-api)*

#### Scenario: vLLM inference snapshot
- **WHEN** the endpoint is called for a running vLLM-class inference deployment with at least one Ready pod
- **THEN** the response contains serving-quality metrics (TTFT, inter-token latency, tokens/second, queue depth, running requests, KV-cache usage), operational metrics (request error ratio, e2e latency), the detected engine (`VLLM`), the scraped pod name, and a collection timestamp

#### Scenario: KServe Python ModelServer inference snapshot
- **WHEN** the endpoint is called for a running KServe Python ModelServer inference deployment (e.g. a text-classification predictor such as deberta) with at least one Ready predictor pod
- **THEN** the detected engine is `KSERVE_MODELSERVER`, the `serving` block reports the engine-neutral signals (`requestLatency` distribution and `requestsPerSecond` throughput) while the generative-only fields stay null, and the `operational` block reports an end-to-end latency; for a chained deployment the end-to-end latency combines the transformer's pre/post-processing with the predictor's inference (its mean is the sum of the per-stage means; cross-pod percentiles are null), and the predictor pod is named in `scrapedPod`
- **AND** when the model is idle (no requests served yet) the `serving`/`operational` blocks remain available with null fields rather than reporting engine `UNKNOWN`

#### Scenario: Resource metrics for any deployment type
- **WHEN** the endpoint is called for a non-inference deployment (MCP/adapter/application/interceptor/NIM) with at least one pod
- **THEN** the response uses the same schema, reports total/ready replica counts and per-pod CPU/memory usage, reports engine `UNKNOWN`, and marks the `serving`/`operational` blocks unavailable with a reason — the request succeeds (no scrape is attempted)

#### Scenario: Unknown deployment
- **WHEN** the endpoint is called with a nonexistent id
- **THEN** the response is HTTP 404 with an `ErrorView`

### Requirement: Graceful degradation — never 500 for missing telemetry
The system SHALL respond HTTP 200 with a partial payload when telemetry is missing: no Ready pods, an unreachable/erroring metrics endpoint, an unrecognized engine vocabulary, or an absent metrics-server each null out only the affected block(s) and record a human-readable reason in `availability`. Every 200 response carries an availability entry for every block key.

Status: **Implemented** *(Implemented via 023-deployment-metrics-api)*

#### Scenario: No Ready pods
- **WHEN** the deployment has no Ready pods (e.g. undeployed or stopped)
- **THEN** the response is 200 with `serving`/`operational` null, the reason recorded, and replica counts still reported (0/0 when no pods exist)

#### Scenario: Scrape failure
- **WHEN** the pod's `/metrics` endpoint is unreachable, times out, or returns an error
- **THEN** the response is 200 with the serving block unavailable and the reason recorded; the resource block is unaffected

#### Scenario: Unknown engine
- **WHEN** the scraped vocabulary matches no recognized engine signal (no `vllm:`/`tgi_`/`sglang:` prefix and no `request_predict_seconds` marker)
- **THEN** the response is 200 with engine `UNKNOWN`, serving metrics unavailable with that reason, and independently obtainable blocks still populated

#### Scenario: Metrics-server absent
- **WHEN** the cluster lacks the `metrics.k8s.io` resource-metrics capability
- **THEN** the response is 200 with the per-pod usage unavailable (`resources.usage` reason recorded) while replica counts and serving metrics are unaffected

### Requirement: Lifetime window labelling and raw counters
Counter-derived values (tokens/second, request error ratio) SHALL be lifetime aggregates labelled via `window: "lifetime"`, and directly-exposed cumulative counters SHALL be echoed under unified names in `rawCounters` so clients (or a future TSDB) can derive their own windowed rates. Latency metrics are distribution summaries (mean, p50, p95, p99, count) approximated from cumulative Prometheus histogram buckets in a single snapshot. Token rates require the engine to expose `process_start_time_seconds`; otherwise the rate is null while the raw counter remains.

Status: **Implemented** *(Implemented via 023-deployment-metrics-api)*

#### Scenario: Raw counters echoed
- **WHEN** a vLLM deployment is scraped
- **THEN** `rawCounters` contains `prompt_tokens_total`, `generation_tokens_total`, and `request_success_total` as raw cumulative values

#### Scenario: Percentile approximation
- **WHEN** an engine exposes a native latency histogram
- **THEN** p50/p95/p99 are interpolated from the cumulative `le` buckets, clamped at the last finite bucket boundary when the target rank falls into the `+Inf` bucket

### Requirement: Request-triggered collection with bounded staleness
Metric collection SHALL happen only in response to an API request — never via background polling (constitution Principle III). Repeated requests within a short interval are served from a per-deployment response cache with bounded staleness (default TTL 5 s) to limit kube API-server load. Degraded/partial snapshots are cached on the same terms as complete ones — there is no separate negative-caching path — so a transient failure (e.g. a momentary scrape timeout) may be served for up to the TTL before re-collection. Concurrent first requests for the same id are coalesced (single-flight) so only one collection runs per key per TTL.

Status: **Implemented** *(Implemented via 023-deployment-metrics-api)*

#### Scenario: Rapid repeated polling
- **WHEN** a client polls the endpoint faster than the cache TTL
- **THEN** cached responses are returned and the API server is not re-queried until the TTL expires

### Requirement: Operator configuration
Operators SHALL be able to disable the capability, tune the per-scrape time budget and cache TTL, and independently toggle the per-pod resource-usage block via `app.metrics.scrape.*` properties (see `docs/configuration.md` § Model Metrics Scrape Configuration). When disabled, requests are rejected with HTTP 400 and a clear message.

Status: **Implemented** *(Implemented via 023-deployment-metrics-api)*

#### Scenario: Feature disabled
- **WHEN** `app.metrics.scrape.enabled=false` and the endpoint is called
- **THEN** the response is HTTP 400 with an `ErrorView` stating metrics collection is disabled by configuration

### Requirement: GPU fields are contract placeholders in the PoC
The schema SHALL carry per-pod `gpuUtilization`/`gpuMemoryUsedBytes` fields, reported unavailable (`resources.gpu` reason) until the DCGM exporter cluster prerequisite ships (follow-up (b) below). KV-cache usage is the engine-level GPU-pressure proxy available today.

Status: **Implemented** *(placeholder behaviour; GPU telemetry itself is a follow-up)*

## Design Rationale (Telemetry Foundation ADR)
All target engines natively expose the **Prometheus exposition format** over HTTP (`/metrics`); none speaks OTLP. Three transports were weighed:

- **Option A — DM scrapes engine `/metrics` directly (chosen for the PoC).** DM reaches each pod's `/metrics` through the Kubernetes API-server pod-proxy subresource and normalizes in-process. Zero new infrastructure, reuses existing kube auth/TLS, works when DM runs outside the cluster (pod IPs are not routable there, the API server is), always-fresh values. Trade-off: no history — counter-derived values are lifetime aggregates, not windowed rates.
- **Option B — cluster Prometheus + PromQL.** True windowed rates and history, but new stateful infrastructure per cluster and a second observability stack alongside the existing OTLP pipeline.
- **Option C — OTel Collector (Prometheus receiver) → OTLP backend (chosen direction for the time-range follow-up).** Aligns with the existing OTel investment so engine metrics, DM logs, and traces share one backend (preserving the trace↔metric pivot); the collector is stateless. Trade-off: still new cluster infrastructure, and the time-range query surface is backend-specific.

**Decision**: ship the snapshot path on Option A (no infra, no manifest changes); implement the time-range follow-up on Option C, with Option B an acceptable per-cluster fallback where a managed Prometheus already exists. The unified schema is the stable contract that survives whichever transport wins — the PoC normalizers are reused as the collector's relabel/mapping rules later.

## Engine-Availability Matrix
Per-engine source metric for each unified field (`✓` exposed directly · `D` derived by DM · `✗` not exposed). Names verified against live dev-cluster captures where marked; upstream names drift between engine versions (e.g. vLLM V1 renamed `vllm:gpu_cache_usage_perc` → `vllm:kv_cache_usage_perc` and `vllm:time_per_output_token_seconds` → `vllm:inter_token_latency_seconds`), so normalizers accept both vocabularies.

### Serving-quality
| Unified field | Unit | vLLM | TGI | SGLang | KServe ModelServer |
|---|---|---|---|---|---|
| `ttft` | s | ✓ `vllm:time_to_first_token_seconds` | ✗ | ✓ `sglang:time_to_first_token_seconds` | ✗ |
| `interTokenLatency` | s | ✓ `vllm:time_per_output_token_seconds` (V1: `vllm:inter_token_latency_seconds`) | ✓ `tgi_request_mean_time_per_token_duration` | ✓ `sglang:inter_token_latency_seconds` | ✗ |
| `tokensPerSecond.prompt` | tok/s | D `vllm:prompt_tokens_total` | D `tgi_request_input_length` | D `sglang:prompt_tokens_total` | ✗ |
| `tokensPerSecond.generation` | tok/s | D `vllm:generation_tokens_total` | D `tgi_request_generated_tokens` | D `sglang:generation_tokens_total` | ✗ |
| `queueDepth` | requests | ✓ `vllm:num_requests_waiting` | ✓ `tgi_queue_size` | ✓ `sglang:num_queue_reqs` | ✗ |
| `runningRequests` | requests | ✓ `vllm:num_requests_running` | ✓ `tgi_batch_current_size` | ✓ `sglang:num_running_reqs` | ✗ |
| `kvCacheUsage` | ratio 0–1 | ✓ `vllm:gpu_cache_usage_perc` (V1: `vllm:kv_cache_usage_perc`) | ✗ | ✓ `sglang:token_usage` | ✗ |
| `requestLatency` | s | ✗ (e2e only) | ✗ | ✗ | ✓ `request_predict_seconds` |
| `requestsPerSecond` | req/s | ✗ | ✗ | ✗ | D `request_predict_seconds` count |

### Operational
| Unified field | Unit | vLLM | TGI | SGLang | KServe ModelServer |
|---|---|---|---|---|---|
| `requestErrorRatio` | ratio 0–1 | D `vllm:request_success_total` by `finished_reason` (`abort` V0 + `error` V1) | D `tgi_request_count` vs `tgi_request_success` | D `sglang:num_aborted_requests_total` | ✗ (no success counter) |
| `e2eLatency` (p50/p95/p99) | s | ✓ `vllm:e2e_request_latency_seconds` | ✓ `tgi_request_duration` | ✓ `sglang:e2e_request_latency_seconds` | ✓ `request_predict_seconds` (+ transformer pre/post means for chained deployments) |

### Resource (engine-independent)
| Unified field | Unit | Source | Notes |
|---|---|---|---|
| `cpuMillicores` / `memoryBytes` | millicores / bytes | `metrics.k8s.io` (Fabric8 `top().pods()`) | per pod; requires metrics-server (present in managed clusters) |
| `replicas.total` / `replicas.ready` | pods | existing pod listing | reported for every deployment type |
| `gpuUtilization` / `gpuMemoryUsedBytes` | ratio 0–1 / bytes | DCGM exporter (`DCGM_FI_DEV_*`) | contract placeholder — cluster prerequisite, follow-up (b); `kvCacheUsage` is today's engine-level proxy |

Scale events are not duplicated here — they are served by the existing k8s events stream (see `specs/kubernetes-events/spec.md`).

## Out of Scope (designed follow-ups)
- **Time-range API** (`?start&end&step&metrics`) — see `specs/023-deployment-metrics-api/contracts/deployment-metrics-api.yaml` (documented as not-implemented); requires the OTel Collector pipeline (ADR Option C above). The snapshot contract is forward-compatible: unified names and availability semantics are identical.
- Multi-pod aggregation, long-term retention, alerting, access-control specifics.

Sized follow-up tickets:

| # | Ticket | Size | Depends on |
|---|---|---|---|
| (a) | **Time-range metrics API** — OTel Collector (Prometheus receiver) pipeline per ADR Option C, backend query adapter, `?start&end&step` implementation | L | PoC schema; per-cluster backend decision |
| (b) | **GPU metrics via DCGM** — DCGM exporter as cluster prerequisite, join `DCGM_FI_DEV_*` series to pods, fill `gpuUtilization`/`gpuMemory*` in the resource block | M | (a) for history; snapshot-only join possible standalone |
| (c) | **Persist serving runtime on `InferenceDeployment`** — record the engine at deploy time (DB column + DTOs + manifest plumbing), replacing prefix sniffing | S/M | none |
| (d) | **Metrics-driven autoscaling** — implement `HARDWARE_USAGE` / `PENDING_REQUESTS` strategies (currently throw in `InferenceManifestGenerator`) on top of the unified feed | L | (a) or (b) for the metric source |
| (e) | **UI metrics panel** — deployment-page panel consuming the snapshot, later the time-range variant | M (FE) | PoC endpoint |

## Implementation Notes
- Endpoint: `DeploymentController.getMetrics` → `service/deployment/metrics/DeploymentMetricsService` (`@Cacheable`, cache `DeploymentMetricsCache` registered by `configuration/MetricsCachingConfig`)
- Scrape transport (INFERENCE only): `kubernetes/K8sClient.scrapePodMetrics(ns, pod, port, metricsPath, timeoutMs)` via Fabric8 `client.raw(...)` on the pod-proxy subresource; failures map to empty, never propagate. All target engines expose the standard `/metrics` path. The predictor pod is selected by the `component=predictor` label; for `KSERVE_MODELSERVER` the `component=transformer` pod is additionally scraped — concurrently with the predictor on the `metrics-scrape` pool so a chained request waits ~one timeout rather than two — and degrades gracefully to predictor-only when absent/unreachable
- Parsing: hand-rolled `PrometheusTextParser` (exposition format 0.0.4; no new dependency — nothing on the classpath parses this format); returns `ParsedExposition` (value-bearing samples + the metric names declared in `# TYPE` comments) so detection can recognize label-gated histograms that emit no samples while idle
- Normalization: `EngineDetector` (prefix sniffing for the generative engines via `EngineFamily.metricNamePrefix()`, plus a `request_predict_seconds` declared-name marker for `KSERVE_MODELSERVER`) + `EngineMetricsNormalizer` implementations (`Vllm`/`Tgi`/`Sglang`/`KserveModelServer`, selected via `supports(EngineFamily)` and fed an `EngineScrapeContext` carrying the predictor index and an optional transformer index); percentiles via `HistogramSummaries`. The vLLM normalizer accepts both V0 and V1 names where they drifted — KV cache `vllm:gpu_cache_usage_perc` → `vllm:kv_cache_usage_perc`, inter-token latency `vllm:time_per_output_token_seconds` → `vllm:inter_token_latency_seconds` — and counts both `abort` (V0) and `error` (V1) `finished_reason` values in the error ratio (verified against a live dev-cluster vLLM V1 capture). The KServe ModelServer normalizer maps the predictor's `request_predict_seconds` histogram to `serving.requestLatency` + `requestsPerSecond` (generative fields and `requestErrorRatio` stay null, as the framework exposes no token/KV-cache/queue or success counters) and combines transformer pre/post means with predictor predict mean into `operational.e2eLatency`
- Pod CPU/memory (all types): `kubernetes/metrics/PodResourceUsageReader` via Fabric8 `top().pods()`; replica counts and the Ready pod set come from `DeploymentManager.getInstancesWithReadiness` (all pods plus the Ready subset from a single pod-list call)
- Domain types: `model/metrics/*` (`UnifiedDeploymentMetrics`, `NormalizedEngineMetrics`, …); web DTOs in `web/dto/metrics/*` mapped by `DeploymentMetricsDtoMapper`
- Ports: `deployment.containerPort` with fallback to the manager default exposed via `DeploymentManager.getDefaultContainerPort()` (8080 for KServe inference)
- RBAC (runbook/Helm, read-only): `pods`, `pods/proxy` get/list; `metrics.k8s.io` pods get/list when the resource-usage block is enabled
- Test fixtures: `src/test/resources/metrics-fixtures/*` — `vllm.txt` (real dev-cluster vLLM V1 capture, KServe pod) is a live capture; `vllm-v0.txt` preserves V0-vocabulary coverage with hand-computed percentile expectations; `tgi.txt`/`sglang.txt` and the `kserve-modelserver-predictor.txt`/`kserve-modelserver-transformer.txt` pair (KServe Python ModelServer vocabulary, modelled on the live deberta exposition) remain synthetic until captured live
- Related specs: `deployments` (pod introspection), `inference-deployments`, `nim-deployments`, `api-conventions` (ErrorView), `observability-and-logging` (trace pivot scenario), `kubernetes-events` (scale events are cross-referenced, not duplicated)
