package com.epam.aidial.deployment.manager.kubernetes.metrics;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.metrics.PodResourceUsage;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads per-pod CPU/memory usage from the {@code metrics.k8s.io} API (metrics-server) via
 * Fabric8 {@code top()}. Degrades gracefully — an absent metrics-server or any API error maps
 * to an empty result with context logging, so the metrics snapshot stays partial
 * instead of failing. GPU fields stay {@code null} — they require the DCGM exporter (follow-up).
 */
@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class PodResourceUsageReader {

    private static final String CPU = "cpu";
    private static final String MEMORY = "memory";

    private final KubernetesClient client;

    public Optional<PodResourceUsage> read(String namespace, String podName) {
        try {
            return Optional.ofNullable(toUsage(client.top().pods().metrics(namespace, podName), podName, null));
        } catch (KubernetesClientException e) {
            log.warn("Failed to read resource usage of pod '{}' in namespace '{}' (metrics-server unavailable?): {}",
                    podName, namespace, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reads usage for several pods with a single namespace-wide {@code metrics.k8s.io} call, then
     * filters to the requested pods client-side — one API round-trip regardless of replica count,
     * instead of one per pod. Only pods present as map <em>keys</em> are read; any other pod in the
     * namespace is ignored.
     * Each value names that pod's primary (workload) container so usage is attributed to it rather
     * than summing injected sidecars (queue-proxy, istio-proxy); a {@code null}/blank value — or a
     * name not found among the pod's reported containers — sums all containers instead (see
     * {@link #toUsage}). An empty or {@code null} map reads nothing; pods without metrics are absent
     * from the result.
     */
    public List<PodResourceUsage> readAll(String namespace, Map<String, String> podToPrimaryContainer) {
        if (MapUtils.isEmpty(podToPrimaryContainer)) {
            return List.of();
        }
        try {
            var podMetricsList = client.top().pods().metrics(namespace);
            if (podMetricsList == null || CollectionUtils.isEmpty(podMetricsList.getItems())) {
                log.debug("No resource metrics in namespace '{}'", namespace);
                return List.of();
            }
            return podMetricsList.getItems().stream()
                    .filter(metrics -> metrics.getMetadata() != null
                            && podToPrimaryContainer.containsKey(metrics.getMetadata().getName()))
                    .map(metrics -> {
                        var podName = metrics.getMetadata().getName();
                        return toUsage(metrics, podName, podToPrimaryContainer.get(podName));
                    })
                    .filter(Objects::nonNull)
                    .toList();
        } catch (KubernetesClientException e) {
            log.warn("Failed to read resource usage in namespace '{}' (metrics-server unavailable?): {}",
                    namespace, e.getMessage());
            return List.of();
        }
    }

    /**
     * Sums CPU/memory for a pod's <em>primary</em> container when {@code primaryContainer} is named
     * and present, so injected sidecars (queue-proxy, istio-proxy) don't inflate the figure; falls
     * back to summing all containers when the name is {@code null} or absent from the metrics.
     * Returns {@code null} when the metrics carry no containers.
     */
    private static PodResourceUsage toUsage(PodMetrics podMetrics, String podName, String primaryContainer) {
        if (podMetrics == null || CollectionUtils.isEmpty(podMetrics.getContainers())) {
            log.debug("No resource metrics for pod '{}'", podName);
            return null;
        }
        var containers = podMetrics.getContainers();
        if (StringUtils.isNotBlank(primaryContainer)) {
            var primary = containers.stream()
                    .filter(c -> primaryContainer.equals(c.getName()))
                    .findFirst()
                    .orElse(null);
            if (primary != null) {
                return sumContainers(podName, List.of(primary));
            }
            log.debug("Primary container '{}' absent from metrics for pod '{}'; summing all containers",
                    primaryContainer, podName);
        }
        return sumContainers(podName, containers);
    }

    private static PodResourceUsage sumContainers(String podName, List<ContainerMetrics> containers) {
        double cpuMillicores = 0;
        double memoryBytes = 0;
        for (var container : containers) {
            var usage = container.getUsage();
            if (usage == null) {
                continue;
            }
            var cpu = usage.get(CPU);
            if (cpu != null) {
                cpuMillicores += Quantity.getAmountInBytes(cpu).doubleValue() * 1000;
            }
            var memory = usage.get(MEMORY);
            if (memory != null) {
                memoryBytes += Quantity.getAmountInBytes(memory).doubleValue();
            }
        }
        return new PodResourceUsage(podName, cpuMillicores, memoryBytes, null, null);
    }

}
