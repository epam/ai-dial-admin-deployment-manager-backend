package com.epam.aidial.deployment.manager.kubernetes.metrics;

import com.epam.aidial.deployment.manager.configuration.MetricsScrapeProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads raw GPU telemetry from the NVIDIA DCGM exporter through the API-server pod proxy. The
 * exporter runs as a DaemonSet (one pod per GPU node); to keep cost proportional to the deployment
 * rather than the whole GPU fleet, only the exporter pods <em>co-located</em> on the nodes running
 * the deployment's pods are scraped. Returns each exporter's raw Prometheus exposition text for the
 * service layer to parse — Fabric8 access and the scrape transport stay in this layer.
 *
 * <p>Degrades gracefully: an absent exporter namespace, a label mismatch, or any scrape failure maps
 * to an empty result with context logging, so the GPU block degrades to unavailable rather than
 * failing the snapshot.</p>
 */
@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class GpuMetricsReader {

    private final K8sClient k8sClient;
    private final MetricsScrapeProperties properties;

    /**
     * Scrapes the dcgm-exporter pods on the given nodes and returns their raw exposition bodies.
     * An empty node set (no scheduled pods) or no matching exporter yields an empty list.
     */
    public List<String> readColocatedExporters(Set<String> nodeNames) {
        if (CollectionUtils.isEmpty(nodeNames)) {
            return List.of();
        }
        var gpu = properties.getGpu();
        var namespace = gpu.getNamespace();
        var labels = parseSelector(gpu.getPodLabelSelector());
        if (labels.isEmpty()) {
            // A blank selector would list (and try to scrape as exporters) every pod in the namespace;
            // treat it as a misconfiguration and degrade rather than fan out over unrelated workloads.
            log.warn("DCGM exporter pod-label-selector is blank; refusing to scrape all pods in namespace '{}'", namespace);
            return List.of();
        }
        try {
            // Keep at most one Running exporter per node: a DaemonSet rolling update can briefly run two
            // pods on a node, and scraping both would double-count that node's GPU series (memory sums,
            // total sums). Newest wins — during a rollout the newer pod is the current replacement. The
            // Running filter also stops Pending/Failed pods from burning the scrape-timeout budget.
            var exporterPodNames = k8sClient.getPods(namespace, labels).getItems().stream()
                    .filter(pod -> pod.getSpec() != null && nodeNames.contains(pod.getSpec().getNodeName()))
                    .filter(pod -> pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase()))
                    .filter(pod -> pod.getMetadata() != null && StringUtils.isNotBlank(pod.getMetadata().getName()))
                    .collect(Collectors.toMap(
                            pod -> pod.getSpec().getNodeName(),
                            Function.identity(),
                            GpuMetricsReader::newestOf))
                    .values().stream()
                    .map(pod -> pod.getMetadata().getName())
                    .toList();
            return k8sClient.scrapePodMetrics(namespace, exporterPodNames, gpu.getPort(),
                    gpu.getMetricsPath(), properties.getTimeoutMs());
        } catch (KubernetesClientException e) {
            log.warn("Failed to list dcgm-exporter pods in namespace '{}' (DCGM exporter not present?): {}",
                    namespace, e.getMessage());
            return List.of();
        }
    }

    /**
     * Picks the newer of two exporter pods on the same node by creation timestamp (ISO-8601, UTC, so a
     * lexicographic compare is chronological). A pod with a blank timestamp is treated as older.
     */
    private static Pod newestOf(Pod left, Pod right) {
        var leftTs = StringUtils.trimToEmpty(left.getMetadata().getCreationTimestamp());
        var rightTs = StringUtils.trimToEmpty(right.getMetadata().getCreationTimestamp());
        return leftTs.compareTo(rightTs) >= 0 ? left : right;
    }

    /** Parses a {@code k=v,k2=v2} label selector into a map; a blank selector matches all pods. */
    private static Map<String, String> parseSelector(String selector) {
        var labels = new HashMap<String, String>();
        if (StringUtils.isBlank(selector)) {
            return labels;
        }
        for (var pair : selector.split(",")) {
            var kv = pair.split("=", 2);
            if (kv.length == 2 && StringUtils.isNotBlank(kv[0])) {
                labels.put(kv[0].trim(), kv[1].trim());
            }
        }
        return labels;
    }

}
