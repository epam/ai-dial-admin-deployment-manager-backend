package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.MetricsCachingConfig;
import com.epam.aidial.deployment.manager.configuration.MetricsScrapeProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.exception.MetricsCollectionDisabledException;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.metrics.PodResourceUsageReader;
import com.epam.aidial.deployment.manager.model.PodInfo;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.metrics.BlockAvailability;
import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.NormalizedEngineMetrics;
import com.epam.aidial.deployment.manager.model.metrics.ParsedExposition;
import com.epam.aidial.deployment.manager.model.metrics.PodResourceUsage;
import com.epam.aidial.deployment.manager.model.metrics.ResourceMetrics;
import com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentManager;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentManagerProvider;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_OPERATIONAL;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_RESOURCES;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_RESOURCES_GPU;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_RESOURCES_USAGE;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_SERVING;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.WINDOW_LIFETIME;

/**
 * On-demand live metrics snapshot for any deployment: replica counts plus optional per-pod
 * resource usage are reported for every deployment type, and INFERENCE deployments additionally
 * get serving-quality metrics by scraping the engine's Prometheus {@code /metrics} from the Ready
 * predictor pod through the API-server pod proxy, detecting the engine family and normalizing to the
 * unified schema. For a chained KServe Python ModelServer deployment the transformer pod is also
 * scraped so its pre/post-processing latency combines with the predictor's inference latency.
 *
 * <p>Graceful degradation is a hard contract rule: a non-inference type, no Ready pods, an
 * unreachable metrics endpoint, an unrecognized engine, or an absent metrics-server each null out
 * only the affected block(s) with the reason recorded in {@code availability} — the request still
 * succeeds with a partial payload, never a 500. Collection is request-triggered only; a short-TTL
 * response cache bounds API-server load under rapid repeated requests.</p>
 */
@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class DeploymentMetricsService {

    /** Standard Prometheus exposition path served by vLLM/TGI/SGLang. */
    private static final String METRICS_PATH = "/metrics";

    /**
     * KServe {@link PodInfo#getComponent() component} label value of the model-serving pod. A
     * transformer pod sits in front of it for pre/post-processing and exposes no engine metrics, so
     * when an InferenceService has both we must scrape the predictor, not whichever pod sorts first.
     */
    private static final String COMPONENT_PREDICTOR = "predictor";

    /**
     * KServe {@link PodInfo#getComponent() component} label value of the pre/post-processing pod of
     * a chained InferenceService. For the KServe Python ModelServer engine its
     * {@code request_preprocess_seconds}/{@code request_postprocess_seconds} histograms are combined
     * with the predictor's {@code request_predict_seconds} into an end-to-end latency.
     */
    private static final String COMPONENT_TRANSFORMER = "transformer";

    private static final String REASON_SERVING_UNSUPPORTED = "serving metrics are available only for inference deployments";
    private static final String REASON_NO_READY_PODS = "no ready pods to read metrics from";
    private static final String REASON_NO_PODS = "no pods to read resource usage from";
    private static final String REASON_SCRAPE_FAILED = "metrics endpoint unreachable or returned an error";
    private static final String REASON_UNKNOWN_ENGINE = "engine not recognized from exposed metrics";
    private static final String REASON_USAGE_DISABLED = "pod resource usage collection is disabled by configuration";
    private static final String REASON_USAGE_UNAVAILABLE = "pod resource usage unavailable (metrics-server not available?)";
    private static final String REASON_GPU_REQUIRES_DCGM = "GPU telemetry requires the DCGM exporter cluster prerequisite (follow-up)";

    private final DeploymentService deploymentService;
    private final DeploymentManagerProvider deploymentManagerProvider;
    private final K8sClient k8sClient;
    private final PrometheusTextParser prometheusTextParser;
    private final EngineDetector engineDetector;
    private final List<EngineMetricsNormalizer> normalizers;
    private final PodResourceUsageReader podResourceUsageReader;
    private final MetricsScrapeProperties properties;

    @Cacheable(cacheNames = MetricsCachingConfig.DEPLOYMENT_METRICS_CACHE_NAME, sync = true)
    public UnifiedDeploymentMetrics getSnapshot(String id) {
        if (!properties.isEnabled()) {
            throw new MetricsCollectionDisabledException("Metrics collection is disabled by configuration (app.metrics.scrape.enabled=false)");
        }

        var deployment = deploymentService.getDeployment(id, false)
                .orElseThrow(() -> new EntityNotFoundException("Deployment not found: %s".formatted(id)));

        var manager = deploymentManagerProvider.provide(id);
        var availability = new HashMap<String, BlockAvailability>();

        var instances = listInstances(manager, id);
        var allPods = instances.all();
        var readyPods = instances.ready();

        var engineMetrics = collectServingMetrics(deployment, manager, readyPods, availability);
        var resources = collectResources(manager, allPods, readyPods, availability);
        availability.put(AVAILABILITY_RESOURCES, BlockAvailability.AVAILABLE);
        availability.put(AVAILABILITY_RESOURCES_GPU, BlockAvailability.unavailable(REASON_GPU_REQUIRES_DCGM));

        return UnifiedDeploymentMetrics.builder()
                .collectedAt(Instant.now())
                .engine(engineMetrics.engine())
                .scrapedPod(engineMetrics.scrapedPod())
                .window(WINDOW_LIFETIME)
                .availability(Map.copyOf(availability))
                .serving(engineMetrics.normalized() == null ? null : engineMetrics.normalized().serving())
                .resources(resources)
                .operational(engineMetrics.normalized() == null ? null : engineMetrics.normalized().operational())
                .rawCounters(engineMetrics.normalized() == null ? Map.of() : Map.copyOf(engineMetrics.normalized().rawCounters()))
                .build();
    }

    /**
     * Lists all pods and the Ready subset in a single API round-trip. An undeployed/stopped
     * deployment has no service name yet — the pod listing then throws {@link EntityNotFoundException},
     * which is a degradation here (no pods), not an error.
     */
    private static DeploymentManager.PodInstances listInstances(DeploymentManager<?> manager, String id) {
        try {
            return manager.getInstancesWithReadiness(id);
        } catch (EntityNotFoundException e) {
            log.debug("No service for deployment '{}' yet ({}); treating as no pods", id, e.getMessage());
            return new DeploymentManager.PodInstances(List.of(), List.of());
        }
    }

    /**
     * Serving-quality metrics are scraped only for INFERENCE deployments; every other type reports
     * the serving/operational blocks as unavailable (resource metrics still apply to all types).
     */
    private EngineScrapeResult collectServingMetrics(Deployment deployment, DeploymentManager<?> manager,
                                                     List<PodInfo> readyPods, Map<String, BlockAvailability> availability) {
        if (!(deployment instanceof InferenceDeployment)) {
            markEngineBlocksUnavailable(availability, REASON_SERVING_UNSUPPORTED);
            return new EngineScrapeResult(EngineFamily.UNKNOWN, null, null);
        }
        if (CollectionUtils.isEmpty(readyPods)) {
            markEngineBlocksUnavailable(availability, REASON_NO_READY_PODS);
            return new EngineScrapeResult(EngineFamily.UNKNOWN, null, null);
        }

        var predictorPod = selectServingPod(readyPods);
        var pod = predictorPod.getName();
        // Assumption: the engine exposes Prometheus on its container/traffic port over plain HTTP
        // (true for vLLM/TGI/SGLang/KServe ModelServer), and the transformer pod serves on the same
        // port. Sidecar exporters on other ports (e.g. the KServe queue-proxy on 8012) are not read.
        var port = deployment.getContainerPort() != null ? deployment.getContainerPort() : manager.getDefaultContainerPort();

        var exposition = scrapeAndParse(manager, pod, port);
        if (exposition.isEmpty()) {
            markEngineBlocksUnavailable(availability, REASON_SCRAPE_FAILED);
            return new EngineScrapeResult(EngineFamily.UNKNOWN, pod, null);
        }

        var engine = engineDetector.detect(exposition.get());
        if (engine == EngineFamily.UNKNOWN) {
            markEngineBlocksUnavailable(availability, REASON_UNKNOWN_ENGINE);
            return new EngineScrapeResult(engine, pod, null);
        }

        var context = buildScrapeContext(exposition.get(),
                scrapeTransformerIfChained(engine, manager, readyPods, predictorPod, port));
        var normalized = normalizers.stream()
                .filter(normalizer -> normalizer.supports(engine))
                .findFirst()
                .map(normalizer -> normalizer.normalize(context))
                .orElse(null);
        if (normalized == null) {
            markEngineBlocksUnavailable(availability, REASON_UNKNOWN_ENGINE);
            return new EngineScrapeResult(engine, pod, null);
        }

        availability.put(AVAILABILITY_SERVING, BlockAvailability.AVAILABLE);
        availability.put(AVAILABILITY_OPERATIONAL, BlockAvailability.AVAILABLE);
        return new EngineScrapeResult(engine, pod, normalized);
    }

    /** Scrapes one pod's {@code /metrics} and parses the exposition; empty when blank or unreachable. */
    private Optional<ParsedExposition> scrapeAndParse(DeploymentManager<?> manager, String pod, int port) {
        return k8sClient.scrapePodMetrics(manager.getNamespace(), pod, port, METRICS_PATH, properties.getTimeoutMs())
                .filter(StringUtils::isNotBlank)
                .map(prometheusTextParser::parse);
    }

    /**
     * Scrapes the transformer pod only for a chained KServe Python ModelServer deployment — the sole
     * engine that consumes it. Gated on the detected engine (known only after the predictor scrape),
     * so other engines never pay the extra pod-proxy round-trip. Returns empty when the engine isn't
     * KSERVE_MODELSERVER, no transformer pod is Ready, or the scrape fails — the context then degrades
     * gracefully to predictor-only.
     */
    private Optional<ParsedExposition> scrapeTransformerIfChained(EngineFamily engine, DeploymentManager<?> manager,
                                                                  List<PodInfo> readyPods, PodInfo predictorPod, int port) {
        if (engine != EngineFamily.KSERVE_MODELSERVER) {
            return Optional.empty();
        }
        return selectTransformerPod(readyPods, predictorPod)
                .flatMap(transformerPod -> scrapeAndParse(manager, transformerPod.getName(), port));
    }

    /**
     * Builds the normalizer input. Generative engines (vLLM/TGI/SGLang) run on a single pod and use
     * the predictor index alone. For a chained KServe Python ModelServer deployment the transformer
     * pod's exposition is supplied so its pre/post-processing latency can be combined with predictor
     * inference latency. When the transformer is absent or unreachable the context degrades gracefully
     * to predictor-only (pre/post stay null) and the serving block stays available.
     */
    private static EngineScrapeContext buildScrapeContext(ParsedExposition predictorExposition,
                                                          Optional<ParsedExposition> transformerExposition) {
        var predictorIndex = new MetricSampleIndex(predictorExposition.samples());
        return transformerExposition
                .map(transformer -> new EngineScrapeContext(
                        predictorIndex, new MetricSampleIndex(transformer.samples())))
                .orElseGet(() -> EngineScrapeContext.of(predictorIndex));
    }

    /** The Ready {@code transformer} pod of a chained InferenceService, other than the predictor. */
    private static Optional<PodInfo> selectTransformerPod(List<PodInfo> readyPods, PodInfo predictorPod) {
        return readyPods.stream()
                .filter(pod -> COMPONENT_TRANSFORMER.equalsIgnoreCase(pod.getComponent()))
                .filter(pod -> !pod.getName().equals(predictorPod.getName()))
                .findFirst();
    }

    /**
     * Chooses which Ready pod to scrape for serving metrics. A KServe InferenceService with a
     * transformer carries the engine on its {@code predictor} pod while the {@code transformer} pod
     * exposes no engine metrics; both share the InferenceService label, so the pod listing returns
     * both in an undefined order. Prefer the predictor when present; otherwise (KNative/raw
     * inference, single-pod, or no component labels) fall back to the first Ready pod.
     */
    private static PodInfo selectServingPod(List<PodInfo> readyPods) {
        return readyPods.stream()
                .filter(pod -> COMPONENT_PREDICTOR.equalsIgnoreCase(pod.getComponent()))
                .findFirst()
                .orElseGet(readyPods::getFirst);
    }

    private ResourceMetrics collectResources(DeploymentManager<?> manager, List<PodInfo> allPods,
                                             List<PodInfo> readyPods, Map<String, BlockAvailability> availability) {
        List<PodResourceUsage> podUsages = List.of();

        if (!resourceUsageEnabled()) {
            availability.put(AVAILABILITY_RESOURCES_USAGE, BlockAvailability.unavailable(REASON_USAGE_DISABLED));
        } else if (allPods.isEmpty()) {
            availability.put(AVAILABILITY_RESOURCES_USAGE, BlockAvailability.unavailable(REASON_NO_PODS));
        } else {
            var podToPrimaryContainer = new HashMap<String, String>();
            allPods.forEach(pod -> podToPrimaryContainer.put(pod.getName(), pod.getMainContainerName()));
            podUsages = podResourceUsageReader.readAll(manager.getNamespace(), podToPrimaryContainer);
            if (podUsages.isEmpty()) {
                availability.put(AVAILABILITY_RESOURCES_USAGE, BlockAvailability.unavailable(REASON_USAGE_UNAVAILABLE));
            } else {
                availability.put(AVAILABILITY_RESOURCES_USAGE, BlockAvailability.AVAILABLE);
            }
        }

        return new ResourceMetrics(allPods.size(), readyPods.size(), List.copyOf(podUsages));
    }

    /** Nested config can be absent under a partial override; treat a missing block as disabled. */
    private boolean resourceUsageEnabled() {
        return properties.getResourceUsage() != null && properties.getResourceUsage().isEnabled();
    }

    private static void markEngineBlocksUnavailable(Map<String, BlockAvailability> availability, String reason) {
        availability.put(AVAILABILITY_SERVING, BlockAvailability.unavailable(reason));
        availability.put(AVAILABILITY_OPERATIONAL, BlockAvailability.unavailable(reason));
    }

    private record EngineScrapeResult(EngineFamily engine, String scrapedPod, NormalizedEngineMetrics normalized) {
    }

}
