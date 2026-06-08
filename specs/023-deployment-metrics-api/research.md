# Phase 0 Research: Unified Model Metrics API (PoC)

**Date**: 2026-06-05 · **Inputs**: `docs/deployment-metrics-api-spike.md` (the spike already performed the
heavy research — ADR, schema, engine matrix); this document records the decisions, the codebase
verification performed for this plan, and the resolution of every remaining unknown. No
NEEDS CLARIFICATION markers remain.

## R-1. Telemetry foundation

- **Decision**: Direct scrape of engine `/metrics` through the Kubernetes API-server pod-proxy
  subresource, normalized in-process (spike §2 ADR, Option A).
- **Rationale**: zero new infrastructure; works when DM runs outside the cluster (pod IPs not
  routable, API server is); rides existing kube auth/TLS; always-fresh values. Snapshot-only
  semantics are acceptable for the PoC and are honestly labelled (`window: "lifetime"`).
- **Alternatives considered**: cluster Prometheus + PromQL (Option B — true windowed rates, but new
  stateful infra and a second observability stack); OTel Collector → OTLP backend (Option C —
  aligns with existing OTel investment, chosen direction for the *time-range follow-up*, but still
  new infra and not needed to read what engines already expose). The §3 unified schema is the
  stable contract surviving either future transport.

## R-2. Scrape transport mechanics

- **Decision**: `K8sClient.scrapePodMetrics(ns, pod, port)` builds
  `/api/v1/namespaces/{ns}/pods/http:{podName}:{port}/proxy/metrics` and calls Fabric8
  `client.raw(url)`. Non-2xx and exceptions map to an empty result (→ availability reason), never
  propagate.
- **Verified**: `K8sClient` exists at `kubernetes/K8sClient.java`, wraps a `KubernetesClient` field,
  and currently has no `raw()` usage — this is a new addition; Fabric8 7.5.2 exposes
  `Client.raw(String)` (returns body or null on 404-class failures; throws `KubernetesClientException`
  on others — both paths handled). No `PodResource.proxy()` exists in 7.5.2, confirming the spike's
  transport choice.
- **Open check carried into implementation** (flagged in spike §6): confirm the `http:{pod}:{port}`
  proxy URL scheme form against the target clusters during the k8s-local smoke test.
  **Resolved (2026-06-05, live on the dev cluster)**: the URL scheme form works as designed for both
  a KServe vLLM pod and an LLM NIM pod. One correction found: LLM NIMs serve their metrics at
  `/v1/metrics`, not `/metrics` — the scrape is therefore path-aware (NIM probes `/v1/metrics`,
  falls back to `/metrics`).
- **Alternatives considered**: direct pod-IP HTTP via OkHttp — rejected (unroutable in local dev,
  second HTTP egress path, violates the "all workload access via Fabric8 inside `kubernetes/`"
  reading of Constitution III).

## R-3. Resolving the deployment and its manager by id

- **Decision**: `DeploymentMetricsService` uses `DeploymentManagerProvider.provide(String deploymentId)`
  (verified present — `DeploymentManagerProvider.java:58`, resolves via the persisted deployment's
  class) exactly as `DeploymentService.getActiveInstances(id)` does (`DeploymentService.java:403-405`).
  Unknown id → `EntityNotFoundException` (existing behaviour of the provider path) → 404 `ErrorView`.
- **Type gating**: supported types = `InferenceDeployment`, `NimDeployment` (domain classes — there is
  **no** domain-level `DeploymentType` enum; type is expressed via the `Deployment` class hierarchy
  with `@JsonSubTypes`; `DeploymentTypeDto` exists only at the web layer). Unsupported types throw
  `IllegalArgumentException` → 400 `ErrorView` via `DefaultExceptionHandler` (implementation note:
  plain `ValidationException` has **no** dedicated handler and would fall to the generic 500 path;
  `IllegalArgumentException` is the repo's established 400 idiom — see `DeploymentManagerProvider`).

## R-4. Metrics port resolution

- **Decision**: `deployment.getContainerPort()` (field on the abstract `Deployment` domain model,
  verified) with per-type fallback to the manager defaults. The constants
  `InferenceDeploymentManager.DEFAULT_KSERVE_SERVICE_PORT = 8080` and
  `NimDeploymentManager.DEFAULT_NIM_SERVICE_PORT = 8000` are **currently `private static final`** —
  the plan exposes them through the managers (small accessor or widened visibility) rather than
  re-hardcoding values in the metrics service, per spike §5.

## R-5. Prometheus exposition parsing

- **Decision**: hand-rolled `PrometheusTextParser` (~100 LOC) for text format 0.0.4: `# TYPE`-aware;
  parses `name{labels} value [timestamp]` lines; groups `_bucket`/`_sum`/`_count` series; handles
  `+Inf`, `NaN`, scientific notation; ignores comments and exemplars.
- **Rationale / verified**: nothing on the classpath parses this format — Micrometer and the OTel
  SDK only *produce* it; `io.prometheus:simpleclient_common` et al. are not dependencies and adding
  one violates the "no new Gradle dependencies" constraint (spike §5 explicit non-change).
- **Alternatives considered**: `io.prometheus.metrics:prometheus-metrics-exposition-textformats`
  (new dependency for ~100 LOC of stable grammar — rejected); regex-only ad-hoc parsing (too fragile
  for histogram grouping — rejected).

## R-6. Engine detection

- **Decision**: `NimDeployment` → `EngineFamily.NIM` by deployment type; `InferenceDeployment` →
  prefix sniffing over parsed metric names (`vllm:` → VLLM, `tgi_` → TGI, `sglang:` → SGLANG);
  otherwise `UNKNOWN` (serving block unavailable with reason, no error).
- **Rationale**: stable, distinctive namespaces; no extra network call beyond the scrape already in
  hand. Durable fix (persisting serving runtime on `InferenceDeployment` at deploy time) is recorded
  follow-up (c) in spike §7 — explicitly out of PoC scope.
- **NIM nuance**: LLM NIMs expose vLLM-style names → the NIM path reuses the vLLM normalizer rules;
  Triton-based NIMs (`nv_*`) get partial availability (most serving-quality metrics flagged
  unavailable). PoC acceptance targets an LLM NIM.

## R-7. Normalizer architecture

- **Decision**: `EngineMetricsNormalizer` interface with `supports(EngineFamily)` + per-family
  implementations, selected via injected `List<…>` (mirrors the existing `DeploymentManagerProvider`
  switch / `HealthChecker.supports` pattern). Output is the layer-neutral
  `UnifiedDeploymentMetrics` (model/metrics), mapped to DTO records by a MapStruct
  `DeploymentMetricsDtoMapper` (`componentModel = "spring"`).
- **Percentiles**: approximated from cumulative `le` histogram buckets in a single snapshot (linear
  interpolation within the bucket containing the target rank; `+Inf` bucket → upper-bound clamp to
  the last finite bucket boundary). vLLM/TGI/SGLang all export native histograms (verified in
  upstream docs; to be re-verified against captured fixtures — spike §3 warning). Shared helper
  `HistogramSummaries` keeps this logic out of individual normalizers.

## R-8. Pod CPU/memory (resource block)

- **Decision**: `PodResourceUsageReader` in `kubernetes/metrics/` using Fabric8
  `client.top().pods().metrics(ns, pod)` — currently **unused anywhere** in the codebase (verified:
  no `top()`/`PodMetrics` references), so this is a clean addition. Absent metrics-server →
  `KubernetesClientException` caught → block unavailable with reason. Independently toggleable via
  `app.metrics.scrape.resource-usage.enabled`.
- **Replica counts**: from the existing `getInstances`/`getActiveInstances` (`List<PodInfo>`,
  Lombok `@Data` class — *not* a record) — total vs ready.
- **GPU fields**: schema carries them; PoC always reports unavailable (DCGM is follow-up (b)).

## R-9. Caching & load bounding

- **Decision**: short-TTL Caffeine response cache keyed by deployment id, default 5 s, in a new
  `MetricsCachingConfig` mirroring the verified `HuggingFaceCachingConfig` pattern (named cache,
  `expireAfterWrite` from properties, `@Cacheable` on the service method). Request-triggered only —
  no scheduler. The stretch "windowed rates via cached previous sample" (spike §5) stays **out of
  the PoC critical path**: `app.metrics.scrape.rate-window.enabled=false` reserved in config but the
  PoC may ship without the implementation behind it.
- **Cache + degradation interaction**: degraded (partial) payloads are cacheable like any other —
  TTL is short enough that recovery is observed within seconds.

## R-10. Configuration & docs

- **Decision**: `MetricsScrapeProperties` (`@ConfigurationProperties(prefix = "app.metrics.scrape")`)
  in `configuration/`, fields **without initializers**; defaults exclusively in `application.yml`
  via `${ENV_VAR:default}` (constitution Key Patterns rule, verified against `AppProperties` and the
  existing `app.*` block layout — new block fits after `app.kserve`). Properties per spike §5:
  `enabled` (master switch, default true), `timeout-ms` (3000), `cache-ttl-ms` (5000),
  `resource-usage.enabled` (true), `rate-window.enabled` (false) + `rate-window.ttl-seconds` (60).
  `docs/configuration.md` (manually maintained env-var table — verified format) gains matching rows;
  this is a mandatory task per the constitution.
- **Disabled behaviour**: `enabled=false` → endpoint returns 404-style not-available response?
  **Resolved**: follow the platform's standard pattern — the endpoint stays registered and returns
  `IllegalArgumentException` → 400 with a clear "metrics collection is disabled" message
  (predictable, documented; avoids conditional controller registration complexity in a PoC).

## R-11. Error semantics & graceful degradation matrix

| Condition | HTTP | Mechanism |
|---|---|---|
| Unknown deployment id | 404 `ErrorView` | `EntityNotFoundException` (existing handler mapping verified at `DefaultExceptionHandler.java:63-68`) |
| Unsupported deployment type | 400 `ErrorView` | `IllegalArgumentException` |
| Feature disabled by config | 400 `ErrorView` | `IllegalArgumentException` ("disabled") |
| No Ready pods | 200 partial | serving + per-pod resource blocks unavailable, reason recorded; replicas still reported |
| Scrape non-2xx / timeout / exception | 200 partial | serving block unavailable, reason recorded |
| Unknown engine vocabulary | 200 partial | engine=UNKNOWN, serving unavailable; resource block unaffected |
| metrics-server absent | 200 partial | resource-usage unavailable; serving unaffected |
| Engine restart (counter reset) | 200 | values are lifetime-since-restart; `collectedAt` + `window` labelling make this interpretable |

## R-12. Testing strategy (resolved against actual infra)

- Unit: parser (real captured fixtures under `src/test/resources/metrics-fixtures/`), detector,
  per-engine normalizers (incl. percentile approximation), service (every degradation branch),
  `K8sClientTest` addition (proxy URL + failure mapping), `PodResourceUsageReader`.
- Functional (H2): stub `client.raw(proxyUrl)` and `top()` on the Mockito `KubernetesClient` bean
  from `FunctionalTestConfiguration` (verified pattern: `FullWorkflowWithMockedK8sClientFunctionalTest`
  uses captors/stubs on the same bean); exercise the endpoint end-to-end through controller +
  mapper; cover 200-full, 200-partial, 400, 404.
- Live: `K8sFunctionalTests` suite is gated by `@EnabledIfEnvironmentVariable(named =
  "SPRING_PROFILES_ACTIVE", matches = "k8s-local")` (verified) — add a metrics smoke test scraping a
  real KServe pod; assert non-empty unified payload.
- PoC acceptance on the dev cluster (T4 pool): one vLLM HuggingFace deployment + one LLM NIM;
  verify §3 metric names live (vLLM V0 vs V1 rename `gpu_cache_usage_perc` → `kv_cache_usage_perc`
  — normalizers accept both); capture fixtures; demo the endpoint. Note (env): the only GPU pool is
  NVIDIA T4 — dtype constraints may shape which HF model is used for the demo.

## R-13. Endpoint annotations gap

- **Finding**: existing `getPods`/`getActivePods` lack `@Operation`/`@ApiResponse` despite the
  constitution mandating them; `rollbackDeployment` carries them properly.
- **Decision**: the new `getMetrics` endpoint follows the constitution (full `@Operation` +
  `@ApiResponse` set per the contract), not the legacy gap. No retrofit of the pod endpoints in this
  feature.

## R-14. RBAC additions (runbook/Helm — outside this repo's code)

```yaml
- apiGroups: [""]
  resources: ["pods", "pods/proxy"]   # pods get/list already required for logs/instances
  verbs: ["get", "list"]
- apiGroups: ["metrics.k8s.io"]       # only if resource-usage block enabled
  resources: ["pods"]
  verbs: ["get", "list"]
```
Documented in quickstart.md; read-only only (spec FR-014).
