package com.epam.aidial.deployment.manager.kubernetes.metrics;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.metrics.PodResourceUsage;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads per-pod CPU/memory usage from the {@code metrics.k8s.io} API (metrics-server) via
 * Fabric8 {@code top()}. Degrades gracefully — an absent metrics-server or any API error maps
 * to {@link Optional#empty()} with context logging, so the metrics snapshot stays partial
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
            var podMetrics = client.top().pods().metrics(namespace, podName);
            if (podMetrics == null || CollectionUtils.isEmpty(podMetrics.getContainers())) {
                log.debug("No resource metrics for pod '{}' in namespace '{}'", podName, namespace);
                return Optional.empty();
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
            return Optional.of(new PodResourceUsage(podName, cpuMillicores, memoryBytes, null, null));
        } catch (KubernetesClientException e) {
            log.warn("Failed to read resource usage of pod '{}' in namespace '{}' (metrics-server unavailable?): {}",
                    podName, namespace, e.getMessage());
            return Optional.empty();
        }
    }

}
