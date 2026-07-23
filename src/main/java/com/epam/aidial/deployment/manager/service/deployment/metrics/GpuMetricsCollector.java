package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.MetricsScrapeProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.metrics.GpuMetricsReader;
import com.epam.aidial.deployment.manager.model.metrics.GpuPodUsage;
import com.epam.aidial.deployment.manager.model.metrics.MetricSample;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Collects per-pod GPU usage from the DCGM exporter and normalizes it to {@link GpuPodUsage}.
 * Scraping + exporter discovery live in the kubernetes layer ({@link GpuMetricsReader}); this class
 * parses the raw exposition with the shared {@link PrometheusTextParser}, keeps only series for the
 * target deployment's pods (matched on the DCGM {@code namespace}/{@code pod} labels), and
 * aggregates across each pod's GPUs — summing framebuffer memory and averaging utilization.
 *
 * <p>DCGM framebuffer series ({@code DCGM_FI_DEV_FB_USED} / {@code _FB_FREE}) are in MiB; utilization
 * ({@code DCGM_FI_DEV_GPU_UTIL}) is a percentage 0–100. Output memory is bytes and utilization a
 * ratio 0–1. Returns an empty map when no telemetry is available, so the caller degrades gracefully.</p>
 */
@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class GpuMetricsCollector {

    private static final String DCGM_FB_USED = "DCGM_FI_DEV_FB_USED";
    private static final String DCGM_FB_FREE = "DCGM_FI_DEV_FB_FREE";
    private static final String DCGM_GPU_UTIL = "DCGM_FI_DEV_GPU_UTIL";
    private static final double MIB_TO_BYTES = 1024d * 1024d;

    private final GpuMetricsReader gpuMetricsReader;
    private final PrometheusTextParser prometheusTextParser;
    private final MetricsScrapeProperties properties;

    /**
     * @param namespace the deployment's namespace (matched against the DCGM {@code namespace} label)
     * @param podNames  the deployment's pod names (matched against the DCGM {@code pod} label)
     * @param nodeNames the nodes those pods run on (which exporter pods to scrape)
     * @return per-pod GPU usage keyed by pod name; empty when no telemetry could be joined
     */
    public Map<String, GpuPodUsage> collect(String namespace, Set<String> podNames, Set<String> nodeNames) {
        if (CollectionUtils.isEmpty(podNames) || CollectionUtils.isEmpty(nodeNames)) {
            return Map.of();
        }
        var expositions = gpuMetricsReader.readColocatedExporters(nodeNames);
        if (CollectionUtils.isEmpty(expositions)) {
            return Map.of();
        }

        var gpu = properties.getGpu();
        var podLabel = gpu.getPodLabel();
        var namespaceLabel = gpu.getNamespaceLabel();

        var accumulators = new LinkedHashMap<String, Accumulator>();
        for (var exposition : expositions) {
            for (var sample : prometheusTextParser.parse(exposition).samples()) {
                accumulate(sample, namespace, podNames, podLabel, namespaceLabel, accumulators);
            }
        }

        var result = new LinkedHashMap<String, GpuPodUsage>();
        accumulators.forEach((pod, acc) -> result.put(pod, acc.toUsage(pod)));
        return result;
    }

    private static void accumulate(MetricSample sample, String namespace, Set<String> podNames,
                                   String podLabel, String namespaceLabel,
                                   Map<String, Accumulator> accumulators) {
        var labels = sample.labels();
        if (!namespace.equals(labels.get(namespaceLabel))) {
            return;
        }
        var pod = labels.get(podLabel);
        if (StringUtils.isBlank(pod) || !podNames.contains(pod)) {
            return;
        }
        var acc = accumulators.computeIfAbsent(pod, p -> new Accumulator());
        switch (sample.name()) {
            case DCGM_FB_USED -> {
                acc.usedMiB += sample.value();
                acc.sawMemory = true;
            }
            case DCGM_FB_FREE -> {
                acc.freeMiB += sample.value();
                acc.sawMemory = true;
            }
            case DCGM_GPU_UTIL -> {
                acc.utilSum += sample.value();
                acc.utilCount++;
            }
            default -> { /* not a GPU series we consume */ }
        }
    }

    /** Mutable per-pod tally over that pod's GPUs; sum memory, average utilization. */
    private static final class Accumulator {
        private double usedMiB;
        private double freeMiB;
        private double utilSum;
        private int utilCount;
        private boolean sawMemory;

        private GpuPodUsage toUsage(String pod) {
            Double utilization = utilCount > 0 ? (utilSum / utilCount) / 100d : null;
            Double usedBytes = sawMemory ? usedMiB * MIB_TO_BYTES : null;
            Double totalBytes = sawMemory ? (usedMiB + freeMiB) * MIB_TO_BYTES : null;
            return new GpuPodUsage(pod, utilization, usedBytes, totalBytes);
        }
    }

}
