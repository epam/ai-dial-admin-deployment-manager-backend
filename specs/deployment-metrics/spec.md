# Model Metrics

## Purpose
This spec describes the unified deployment metrics API — an on-demand live metrics snapshot for any deployment. Resource metrics (replica counts and per-pod CPU/memory) are reported for every deployment type. For INFERENCE deployments, engine-specific Prometheus metrics (generative engines vLLM/TGI/SGLang, and the KServe Python ModelServer behind non-generative classification/embedding/custom predictors) are additionally scraped from the Ready predictor pod through the Kubernetes API-server pod proxy and normalized to a single engine-neutral schema so clients never depend on a particular engine's vocabulary. For a chained KServe InferenceService the transformer pod is also scraped so its pre/post-processing latency combines with the predictor's inference latency.

Status: **Implemented** *(Implemented via 023-deployment-metrics-api — PoC scope: live snapshot only)*

Design source: `docs/deployment-metrics-api-spike.md` (issue #162 spike — ADR, engine-availability matrix, follow-up tickets). OpenAPI contract: `specs/023-deployment-metrics-api/contracts/deployment-metrics-api.yaml`.

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
The schema SHALL carry per-pod `gpuUtilization`/`gpuMemoryUsedBytes` fields, reported unavailable (`resources.gpu` reason) until the DCGM exporter cluster prerequisite ships (spike follow-up (b)). KV-cache usage is the engine-level GPU-pressure proxy available today.

Status: **Implemented** *(placeholder behaviour; GPU telemetry itself is a follow-up)*

## Out of Scope (designed follow-ups, spike §7)
- **Time-range API** (`?start&end&step&metrics`) — designed in spike §4.2; requires the OTel Collector pipeline (ADR Option C). The snapshot contract is forward-compatible: unified names and availability semantics are identical.
- **GPU metrics via DCGM**, **persisted serving runtime** (replaces prefix sniffing), **metrics-driven autoscaling**, **UI panel**, multi-pod aggregation, long-term retention, alerting.

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
- Live smoke: `PodMetricsScrapeFunctionalTest` in the env-gated `k8s-local` suite (`K8S_TEST_METRICS_POD_NAME`)
- Related specs: `deployments` (pod introspection), `inference-deployments`, `nim-deployments`, `api-conventions` (ErrorView), `observability-and-logging` (trace pivot scenario), `kubernetes-events` (scale events are cross-referenced, not duplicated)
