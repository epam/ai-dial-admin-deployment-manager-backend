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
import com.epam.aidial.deployment.manager.model.metrics.GpuPodUsage;
import com.epam.aidial.deployment.manager.model.metrics.PodResourceUsage;
import com.epam.aidial.deployment.manager.model.metrics.ResourceMetrics;
import com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentManager;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentManagerProvider;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private static final String REASON_GPU_NOT_REQUESTED = "deployment does not request GPU";
    private static final String REASON_GPU_DISABLED = "GPU metrics collection is disabled by configuration";
    private static final String REASON_GPU_NO_PODS = "no pods to read GPU usage from";
    private static final String REASON_GPU_UNAVAILABLE = "GPU telemetry unavailable (DCGM exporter not present or unreachable?)";

    private final DeploymentService deploymentService;
    private final DeploymentManagerProvider deploymentManagerProvider;
    private final List<ServingMetricsCollector> servingMetricsCollectors;
    private final PodResourceUsageReader podResourceUsageReader;
    private final GpuMetricsCollector gpuMetricsCollector;
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

        var podUsages = collectPodUsages(manager, allPods, availability);
        podUsages = collectGpuUsages(deployment, manager, allPods, podUsages, availability);
        var resources = new ResourceMetrics(allPods.size(), readyPods.size(), List.copyOf(podUsages));
        availability.put(AVAILABILITY_RESOURCES, AvailabilityStatus.AVAILABLE);

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

    /**
     * Reads per-pod CPU/memory from the metrics-server and records the {@code resources.usage}
     * availability. Returns an empty list (with the reason recorded) when disabled, when there are no
     * pods, or when the metrics-server is absent.
     */
    private List<PodResourceUsage> collectPodUsages(DeploymentManager<?> manager, List<PodInfo> allPods,
                                                    Map<String, AvailabilityStatus> availability) {
        if (!resourceUsageEnabled()) {
            availability.put(AVAILABILITY_RESOURCES_USAGE, AvailabilityStatus.unavailable(REASON_USAGE_DISABLED));
            return List.of();
        }
        if (allPods.isEmpty()) {
            availability.put(AVAILABILITY_RESOURCES_USAGE, AvailabilityStatus.unavailable(REASON_NO_PODS));
            return List.of();
        }
        var podToPrimaryContainer = new HashMap<String, String>();
        allPods.forEach(pod -> podToPrimaryContainer.put(pod.getName(), pod.getMainContainerName()));
        var podUsages = podResourceUsageReader.readAll(manager.getNamespace(), podToPrimaryContainer);
        if (podUsages.isEmpty()) {
            availability.put(AVAILABILITY_RESOURCES_USAGE, AvailabilityStatus.unavailable(REASON_USAGE_UNAVAILABLE));
        } else {
            availability.put(AVAILABILITY_RESOURCES_USAGE, AvailabilityStatus.AVAILABLE);
        }
        return podUsages;
    }

    /**
     * Joins GPU telemetry (DCGM exporter) onto the per-pod usages for GPU-requesting deployments and
     * records the {@code resources.gpu} availability. Attempted only when GPU collection is enabled,
     * the deployment requests {@code nvidia.com/gpu}, and pods exist; each of those preconditions and a
     * missing/unreachable exporter degrade gracefully with a distinct reason — never an error.
     */
    private List<PodResourceUsage> collectGpuUsages(Deployment deployment, DeploymentManager<?> manager,
                                                    List<PodInfo> allPods, List<PodResourceUsage> podUsages,
                                                    Map<String, AvailabilityStatus> availability) {
        if (!gpuEnabled()) {
            availability.put(AVAILABILITY_RESOURCES_GPU, AvailabilityStatus.unavailable(REASON_GPU_DISABLED));
            return podUsages;
        }
        if (!GpuRequest.isRequested(deployment)) {
            availability.put(AVAILABILITY_RESOURCES_GPU, AvailabilityStatus.unavailable(REASON_GPU_NOT_REQUESTED));
            return podUsages;
        }
        if (allPods.isEmpty()) {
            availability.put(AVAILABILITY_RESOURCES_GPU, AvailabilityStatus.unavailable(REASON_GPU_NO_PODS));
            return podUsages;
        }
        var podNames = allPods.stream().map(PodInfo::getName).collect(Collectors.toSet());
        var nodeNames = allPods.stream().map(PodInfo::getNodeName).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        var gpuByPod = gpuMetricsCollector.collect(manager.getNamespace(), podNames, nodeNames);
        if (gpuByPod.isEmpty()) {
            availability.put(AVAILABILITY_RESOURCES_GPU, AvailabilityStatus.unavailable(REASON_GPU_UNAVAILABLE));
            return podUsages;
        }
        availability.put(AVAILABILITY_RESOURCES_GPU, AvailabilityStatus.AVAILABLE);
        return mergeGpu(podUsages, gpuByPod);
    }

    /**
     * Merges GPU usage into the CPU/memory usages by pod name. A pod with GPU data but no
     * metrics-server entry still appears (GPU fields set, CPU/memory null) so the two sources degrade
     * independently.
     */
    private static List<PodResourceUsage> mergeGpu(List<PodResourceUsage> podUsages, Map<String, GpuPodUsage> gpuByPod) {
        var byPod = new LinkedHashMap<String, PodResourceUsage>();
        podUsages.forEach(usage -> byPod.put(usage.name(), usage));
        gpuByPod.forEach((pod, gpu) -> {
            var existing = byPod.get(pod);
            var cpu = existing != null ? existing.cpuMillicores() : null;
            var memory = existing != null ? existing.memoryBytes() : null;
            byPod.put(pod, new PodResourceUsage(pod, cpu, memory,
                    gpu.utilization(), gpu.memoryUsedBytes(), gpu.memoryTotalBytes()));
        });
        return List.copyOf(byPod.values());
    }

    /** Nested config can be absent under a partial override; treat a missing block as disabled. */
    private boolean resourceUsageEnabled() {
        return properties.getResourceUsage() != null && properties.getResourceUsage().isEnabled();
    }

    /** Nested config can be absent under a partial override; treat a missing block as disabled. */
    private boolean gpuEnabled() {
        return properties.getGpu() != null && properties.getGpu().isEnabled();
    }

}
