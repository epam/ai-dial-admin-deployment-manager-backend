package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.MetricsCachingConfig;
import com.epam.aidial.deployment.manager.configuration.MetricsScrapeProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.metrics.PodResourceUsageReader;
import com.epam.aidial.deployment.manager.model.PodInfo;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.metrics.BlockAvailability;
import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.MetricSample;
import com.epam.aidial.deployment.manager.model.metrics.NormalizedEngineMetrics;
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

import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_OPERATIONAL;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_RESOURCES;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_RESOURCES_GPU;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_RESOURCES_USAGE;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_SERVING;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.WINDOW_LIFETIME;

/**
 * On-demand live metrics snapshot for any deployment: replica counts plus optional per-pod
 * resource usage are reported for every deployment type, and INFERENCE deployments additionally
 * get serving-quality metrics by scraping the engine's Prometheus {@code /metrics} from one Ready
 * pod through the API-server pod proxy, detecting the engine family and normalizing to the unified
 * schema.
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
            throw new IllegalArgumentException("Metrics collection is disabled by configuration (app.metrics.scrape.enabled=false)");
        }

        var deployment = deploymentService.getDeployment(id, false)
                .orElseThrow(() -> new EntityNotFoundException("Deployment not found: %s".formatted(id)));

        var manager = deploymentManagerProvider.provide(id);
        var availability = new HashMap<String, BlockAvailability>();

        var allPods = listInstances(manager, id, false);
        var readyPods = listInstances(manager, id, true);

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
     * An undeployed/stopped deployment has no service name yet — the pod listing then throws
     * {@link EntityNotFoundException}, which is a degradation here (no pods), not an error.
     */
    private static List<PodInfo> listInstances(DeploymentManager<?> manager, String id, boolean readyOnly) {
        try {
            return readyOnly ? manager.getActiveInstances(id) : manager.getInstances(id);
        } catch (EntityNotFoundException e) {
            log.debug("No service for deployment '{}' yet ({}); treating as no pods", id, e.getMessage());
            return List.of();
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

        var pod = readyPods.getFirst().getName();
        var port = deployment.getContainerPort() != null ? deployment.getContainerPort() : manager.getDefaultContainerPort();
        var body = k8sClient.scrapePodMetrics(manager.getNamespace(), pod, port, METRICS_PATH, properties.getTimeoutMs())
                .filter(StringUtils::isNotBlank);
        if (body.isEmpty()) {
            markEngineBlocksUnavailable(availability, REASON_SCRAPE_FAILED);
            return new EngineScrapeResult(EngineFamily.UNKNOWN, pod, null);
        }

        List<MetricSample> samples = prometheusTextParser.parse(body.get());
        var engine = engineDetector.detect(samples);
        if (engine == EngineFamily.UNKNOWN) {
            markEngineBlocksUnavailable(availability, REASON_UNKNOWN_ENGINE);
            return new EngineScrapeResult(engine, pod, null);
        }

        var index = new MetricSampleIndex(samples);
        var normalized = normalizers.stream()
                .filter(normalizer -> normalizer.supports(engine))
                .findFirst()
                .map(normalizer -> normalizer.normalize(index))
                .orElse(null);
        if (normalized == null) {
            markEngineBlocksUnavailable(availability, REASON_UNKNOWN_ENGINE);
            return new EngineScrapeResult(engine, pod, null);
        }

        availability.put(AVAILABILITY_SERVING, BlockAvailability.AVAILABLE);
        availability.put(AVAILABILITY_OPERATIONAL, BlockAvailability.AVAILABLE);
        return new EngineScrapeResult(engine, pod, normalized);
    }

    private ResourceMetrics collectResources(DeploymentManager<?> manager, List<PodInfo> allPods,
                                             List<PodInfo> readyPods, Map<String, BlockAvailability> availability) {
        List<PodResourceUsage> podUsages = List.of();

        if (!resourceUsageEnabled()) {
            availability.put(AVAILABILITY_RESOURCES_USAGE, BlockAvailability.unavailable(REASON_USAGE_DISABLED));
        } else if (allPods.isEmpty()) {
            availability.put(AVAILABILITY_RESOURCES_USAGE, BlockAvailability.unavailable(REASON_NO_PODS));
        } else {
            var podNames = allPods.stream().map(PodInfo::getName).toList();
            podUsages = podResourceUsageReader.readAll(manager.getNamespace(), podNames);
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
