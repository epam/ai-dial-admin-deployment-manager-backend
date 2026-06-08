package com.epam.aidial.deployment.manager.kubernetes.metrics;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.metrics.PodResourceUsage;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
            return Optional.ofNullable(toUsage(client.top().pods().metrics(namespace, podName), podName));
        } catch (KubernetesClientException e) {
            log.warn("Failed to read resource usage of pod '{}' in namespace '{}' (metrics-server unavailable?): {}",
                    podName, namespace, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reads usage for several pods with a single namespace-wide {@code metrics.k8s.io} call,
     * then filters to {@code podNames} client-side — one API round-trip regardless of replica
     * count, instead of one per pod. Pods without metrics are simply absent from the result.
     */
    public List<PodResourceUsage> readAll(String namespace, Collection<String> podNames) {
        if (CollectionUtils.isEmpty(podNames)) {
            return List.of();
        }
        Set<String> wanted = Set.copyOf(podNames);
        try {
            var podMetricsList = client.top().pods().metrics(namespace);
            if (podMetricsList == null || CollectionUtils.isEmpty(podMetricsList.getItems())) {
                log.debug("No resource metrics in namespace '{}'", namespace);
                return List.of();
            }
            return podMetricsList.getItems().stream()
                    .filter(metrics -> metrics.getMetadata() != null && wanted.contains(metrics.getMetadata().getName()))
                    .map(metrics -> toUsage(metrics, metrics.getMetadata().getName()))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (KubernetesClientException e) {
            log.warn("Failed to read resource usage in namespace '{}' (metrics-server unavailable?): {}",
                    namespace, e.getMessage());
            return List.of();
        }
    }

    /** Sums CPU/memory across a pod's containers; {@code null} when the metrics carry no containers. */
    private static PodResourceUsage toUsage(PodMetrics podMetrics, String podName) {
        if (podMetrics == null || CollectionUtils.isEmpty(podMetrics.getContainers())) {
            log.debug("No resource metrics for pod '{}'", podName);
            return null;
        }
        double cpuMillicores = 0;
        double memoryBytes = 0;
        for (var container : podMetrics.getContainers()) {
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
