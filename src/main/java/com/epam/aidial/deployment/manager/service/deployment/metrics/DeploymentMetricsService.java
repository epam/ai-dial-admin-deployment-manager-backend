package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.MetricsCachingConfig;
import com.epam.aidial.deployment.manager.configuration.MetricsScrapeProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.exception.MetricsCollectionDisabledException;
import com.epam.aidial.deployment.manager.kubernetes.metrics.PodResourceUsageReader;
import com.epam.aidial.deployment.manager.model.PodInfo;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.metrics.AvailabilityStatus;
import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.PodResourceUsage;
import com.epam.aidial.deployment.manager.model.metrics.ResourceMetrics;
import com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentManager;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentManagerProvider;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final String REASON_SERVING_UNSUPPORTED = "serving metrics are available only for inference deployments";
    private static final String REASON_NO_PODS = "no pods to read resource usage from";
    private static final String REASON_USAGE_DISABLED = "pod resource usage collection is disabled by configuration";
    private static final String REASON_USAGE_UNAVAILABLE = "pod resource usage unavailable (metrics-server not available?)";
    private static final String REASON_GPU_REQUIRES_DCGM = "GPU telemetry requires the DCGM exporter cluster prerequisite (follow-up)";

    private final DeploymentService deploymentService;
    private final DeploymentManagerProvider deploymentManagerProvider;
    private final List<ServingMetricsCollector> servingMetricsCollectors;
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
        var availability = new HashMap<String, AvailabilityStatus>();

        var instances = listInstances(manager, id);
        var allPods = instances.all();
        var readyPods = instances.ready();

        var serving = collectServingMetrics(deployment, manager, readyPods);
        availability.put(AVAILABILITY_SERVING, serving.availability());
        availability.put(AVAILABILITY_OPERATIONAL, serving.availability());

        var resources = collectResources(manager, allPods, readyPods, availability);
        availability.put(AVAILABILITY_RESOURCES, AvailabilityStatus.AVAILABLE);
        availability.put(AVAILABILITY_RESOURCES_GPU, AvailabilityStatus.unavailable(REASON_GPU_REQUIRES_DCGM));

        var normalized = serving.normalized();
        return UnifiedDeploymentMetrics.builder()
                .collectedAt(Instant.now())
                .engine(serving.engine())
                .scrapedPod(serving.scrapedPod())
                .window(WINDOW_LIFETIME)
                .availability(Map.copyOf(availability))
                .serving(normalized == null ? null : normalized.serving())
                .resources(resources)
                .operational(normalized == null ? null : normalized.operational())
                .rawCounters(normalized == null ? Map.of() : Map.copyOf(normalized.rawCounters()))
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
     * Delegates serving-quality collection to the first {@link ServingMetricsCollector} that supports
     * the deployment type; types with no matching collector (non-inference) report the
     * serving/operational blocks as unavailable while resource metrics still apply.
     */
    private ServingMetricsResult collectServingMetrics(Deployment deployment, DeploymentManager<?> manager,
                                                       List<PodInfo> readyPods) {
        return servingMetricsCollectors.stream()
                .filter(collector -> collector.supports(deployment))
                .findFirst()
                .map(collector -> collector.collect(new EngineScrapeTarget(deployment, manager, readyPods)))
                .orElseGet(() -> ServingMetricsResult.unavailable(EngineFamily.UNKNOWN, null, REASON_SERVING_UNSUPPORTED));
    }

    private ResourceMetrics collectResources(DeploymentManager<?> manager, List<PodInfo> allPods,
                                             List<PodInfo> readyPods, Map<String, AvailabilityStatus> availability) {
        List<PodResourceUsage> podUsages = List.of();

        if (!resourceUsageEnabled()) {
            availability.put(AVAILABILITY_RESOURCES_USAGE, AvailabilityStatus.unavailable(REASON_USAGE_DISABLED));
        } else if (allPods.isEmpty()) {
            availability.put(AVAILABILITY_RESOURCES_USAGE, AvailabilityStatus.unavailable(REASON_NO_PODS));
        } else {
            var podToPrimaryContainer = new HashMap<String, String>();
            allPods.forEach(pod -> podToPrimaryContainer.put(pod.getName(), pod.getMainContainerName()));
            podUsages = podResourceUsageReader.readAll(manager.getNamespace(), podToPrimaryContainer);
            if (podUsages.isEmpty()) {
                availability.put(AVAILABILITY_RESOURCES_USAGE, AvailabilityStatus.unavailable(REASON_USAGE_UNAVAILABLE));
            } else {
                availability.put(AVAILABILITY_RESOURCES_USAGE, AvailabilityStatus.AVAILABLE);
            }
        }

        return new ResourceMetrics(allPods.size(), readyPods.size(), List.copyOf(podUsages));
    }

    /** Nested config can be absent under a partial override; treat a missing block as disabled. */
    private boolean resourceUsageEnabled() {
        return properties.getResourceUsage() != null && properties.getResourceUsage().isEnabled();
    }

}
