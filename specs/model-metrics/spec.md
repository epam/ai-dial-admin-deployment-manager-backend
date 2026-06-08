# Model Metrics

## Purpose
This spec describes the unified model metrics API — an on-demand live metrics snapshot for model deployments (INFERENCE and NIM). Engine-specific Prometheus metrics (vLLM, TGI, SGLang, NIM) are scraped from one Ready pod through the Kubernetes API-server pod proxy and normalized to a single engine-neutral schema so clients never depend on a particular engine's vocabulary.

Status: **Implemented** *(Implemented via 023-model-metrics-api — PoC scope: live snapshot only)*

Design source: `docs/model-metrics-api-spike.md` (issue #162 spike — ADR, engine-availability matrix, follow-up tickets). OpenAPI contract: `specs/023-model-metrics-api/contracts/deployment-metrics-api.yaml`.

## Key Terms
- **Unified schema**: engine-neutral metric names with explicit units, grouped into `serving`, `resources`, and `operational` blocks plus a `rawCounters` echo of directly-exposed cumulative counters.
- **Engine family**: the recognized serving-engine vocabulary — `VLLM`, `TGI`, `SGLANG`, `NIM`, or `UNKNOWN`. NIM is identified by deployment type; inference engines by metric-name prefix sniffing (`vllm:` / `tgi_` / `sglang:`).
- **Availability marker**: per-block `{available, reason}` entry; every response carries one entry per block key (`serving`, `operational`, `resources`, `resources.usage`, `resources.gpu`).
- **Lifetime window**: counter-derived values are aggregates since engine process start, labelled `window: "lifetime"`. There is no time-series backend in the PoC.
- **Pod proxy scrape**: `GET /api/v1/namespaces/{ns}/pods/http:{pod}:{port}/proxy{path}` through the kube API server — no new infrastructure, works when the deployment manager runs outside the cluster. The exposition path is engine-dependent: `/metrics` for vLLM-class engines; LLM NIMs serve `/v1/metrics` (verified live — the NIM scrape probes `/v1/metrics` first and falls back to `/metrics`).

## Requirements

### Requirement: Live metrics snapshot endpoint
The system SHALL expose `GET /api/v1/deployments/{id}/metrics` returning a live unified metrics snapshot for INFERENCE and NIM deployments. Other deployment types are rejected with HTTP 400; an unknown id yields HTTP 404 (`ErrorView`, per `api-conventions`). Exactly one Ready pod is scraped per request and named in `scrapedPod`.

Status: **Implemented** *(Implemented via 023-model-metrics-api)*

#### Scenario: vLLM inference snapshot
- **WHEN** the endpoint is called for a running vLLM-class inference deployment with at least one Ready pod
- **THEN** the response contains serving-quality metrics (TTFT, inter-token latency, tokens/second, queue depth, running requests, KV-cache usage), operational metrics (request error ratio, e2e latency), the detected engine (`VLLM`), the scraped pod name, and a collection timestamp

#### Scenario: NIM snapshot through the same contract
- **WHEN** the endpoint is called for a running LLM NIM deployment
- **THEN** the response uses exactly the same schema and unified names with engine `NIM`; vLLM-style NIM metrics (bare names, without the `vllm:` prefix — verified live) map through the vLLM rules via aliasing; the error ratio derives from NIM's explicit `request_success_total`/`request_failure_total` outcome counters; Triton-based NIMs report only what exists (`nv_*`) and flag the rest unavailable

#### Scenario: Unsupported type rejected
- **WHEN** the endpoint is called for an MCP/adapter/application/interceptor deployment
- **THEN** the response is HTTP 400 with an `ErrorView` stating the type does not support model metrics

#### Scenario: Unknown deployment
- **WHEN** the endpoint is called with a nonexistent id
- **THEN** the response is HTTP 404 with an `ErrorView`

### Requirement: Graceful degradation — never 500 for missing telemetry
The system SHALL respond HTTP 200 with a partial payload when telemetry is missing: no Ready pods, an unreachable/erroring metrics endpoint, an unrecognized engine vocabulary, or an absent metrics-server each null out only the affected block(s) and record a human-readable reason in `availability`. Every 200 response carries an availability entry for every block key.

Status: **Implemented** *(Implemented via 023-model-metrics-api)*

#### Scenario: No Ready pods
- **WHEN** the deployment has no Ready pods (e.g. undeployed or stopped)
- **THEN** the response is 200 with `serving`/`operational` null, the reason recorded, and replica counts still reported (0/0 when no pods exist)

#### Scenario: Scrape failure
- **WHEN** the pod's `/metrics` endpoint is unreachable, times out, or returns an error
- **THEN** the response is 200 with the serving block unavailable and the reason recorded; the resource block is unaffected

#### Scenario: Unknown engine
- **WHEN** the scraped vocabulary matches no recognized engine prefix
- **THEN** the response is 200 with engine `UNKNOWN`, serving metrics unavailable with that reason, and independently obtainable blocks still populated

#### Scenario: Metrics-server absent
- **WHEN** the cluster lacks the `metrics.k8s.io` resource-metrics capability
- **THEN** the response is 200 with the per-pod usage unavailable (`resources.usage` reason recorded) while replica counts and serving metrics are unaffected

### Requirement: Lifetime window labelling and raw counters
Counter-derived values (tokens/second, request error ratio) SHALL be lifetime aggregates labelled via `window: "lifetime"`, and directly-exposed cumulative counters SHALL be echoed under unified names in `rawCounters` so clients (or a future TSDB) can derive their own windowed rates. Latency metrics are distribution summaries (mean, p50, p95, p99, count) approximated from cumulative Prometheus histogram buckets in a single snapshot. Token rates require the engine to expose `process_start_time_seconds`; otherwise the rate is null while the raw counter remains.

Status: **Implemented** *(Implemented via 023-model-metrics-api)*

#### Scenario: Raw counters echoed
- **WHEN** a vLLM deployment is scraped
- **THEN** `rawCounters` contains `prompt_tokens_total`, `generation_tokens_total`, and `request_success_total` as raw cumulative values

#### Scenario: Percentile approximation
- **WHEN** an engine exposes a native latency histogram
- **THEN** p50/p95/p99 are interpolated from the cumulative `le` buckets, clamped at the last finite bucket boundary when the target rank falls into the `+Inf` bucket

### Requirement: Request-triggered collection with bounded staleness
Metric collection SHALL happen only in response to an API request — never via background polling (constitution Principle III). Repeated requests within a short interval are served from a per-deployment response cache with bounded staleness (default TTL 5 s) to limit kube API-server load. Degraded/partial snapshots are cached on the same terms as complete ones — there is no separate negative-caching path — so a transient failure (e.g. a momentary scrape timeout) may be served for up to the TTL before re-collection. Concurrent first requests for the same id are coalesced (single-flight) so only one collection runs per key per TTL.

Status: **Implemented** *(Implemented via 023-model-metrics-api)*

#### Scenario: Rapid repeated polling
- **WHEN** a client polls the endpoint faster than the cache TTL
- **THEN** cached responses are returned and the API server is not re-queried until the TTL expires

### Requirement: Operator configuration
Operators SHALL be able to disable the capability, tune the per-scrape time budget and cache TTL, and independently toggle the per-pod resource-usage block via `app.metrics.scrape.*` properties (see `docs/configuration.md` § Model Metrics Scrape Configuration). When disabled, requests are rejected with HTTP 400 and a clear message.

Status: **Implemented** *(Implemented via 023-model-metrics-api)*

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
- Scrape transport: `kubernetes/K8sClient.scrapePodMetrics(ns, pod, port, metricsPath, timeoutMs)` via Fabric8 `client.raw(...)` on the pod-proxy subresource; failures map to empty, never propagate. Path order per type: NIM → `/v1/metrics` then `/metrics`; everything else → `/metrics`
- Parsing: hand-rolled `PrometheusTextParser` (exposition format 0.0.4; no new dependency — nothing on the classpath parses this format)
- Normalization: `EngineDetector` (prefix sniffing driven by `EngineFamily.metricNamePrefix()`) + `EngineMetricsNormalizer` implementations (`Vllm`/`Tgi`/`Sglang`/`Nim`, selected via `supports(EngineFamily)`); within NIM, an LLM NIM is identified by *positive* evidence — any `vllm:`-prefixed series or any bare vLLM-style base name (the recognized set is sourced from `VllmMetricsNormalizer.BASE_METRIC_NAMES`) — so a hybrid exposition mixing bare vLLM names with `nv_*` GPU series stays classified as an LLM NIM; everything else falls to the Triton path; percentiles via `HistogramSummaries`; the vLLM normalizer accepts both V0 and V1 names where they drifted — KV cache `vllm:gpu_cache_usage_perc` → `vllm:kv_cache_usage_perc`, inter-token latency `vllm:time_per_output_token_seconds` → `vllm:inter_token_latency_seconds` — and counts both `abort` (V0) and `error` (V1) `finished_reason` values in the error ratio (verified against a live dev-cluster vLLM V1 capture)
- Pod CPU/memory: `kubernetes/metrics/PodResourceUsageReader` via Fabric8 `top().pods()`; replica counts from the existing `getInstances`/`getActiveInstances`
- Domain types: `model/metrics/*` (`UnifiedDeploymentMetrics`, `NormalizedEngineMetrics`, …); web DTOs in `web/dto/metrics/*` mapped by `DeploymentMetricsDtoMapper`
- Ports: `deployment.containerPort` with fallback to the manager defaults exposed via `DeploymentManager.getDefaultContainerPort()` (8080 KServe inference, 8000 NIM)
- RBAC (runbook/Helm, read-only): `pods`, `pods/proxy` get/list; `metrics.k8s.io` pods get/list when the resource-usage block is enabled
- Test fixtures: `src/test/resources/metrics-fixtures/*` — `vllm.txt` (real dev-cluster vLLM V1 capture, KServe pod) and `nim-llm.txt` (real dev-cluster LLM NIM `/v1/metrics` capture) are live captures; `vllm-v0.txt` preserves V0-vocabulary coverage with hand-computed percentile expectations; `tgi.txt`/`sglang.txt`/`nim-triton.txt` remain synthetic per spike §3 until captured live
- Live smoke: `PodMetricsScrapeFunctionalTest` in the env-gated `k8s-local` suite (`K8S_TEST_METRICS_POD_NAME`)
- Related specs: `deployments` (pod introspection), `inference-deployments`, `nim-deployments`, `api-conventions` (ErrorView), `observability-and-logging` (trace pivot scenario), `kubernetes-events` (scale events are cross-referenced, not duplicated)
