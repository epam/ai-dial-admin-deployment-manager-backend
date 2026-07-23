package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.MetricsScrapeProperties;
import com.epam.aidial.deployment.manager.kubernetes.metrics.GpuMetricsReader;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GpuMetricsCollectorTest {

    private static final String NAMESPACE = "model-ns";
    private static final double MIB = 1024d * 1024d;

    @Mock
    private GpuMetricsReader gpuMetricsReader;

    private GpuMetricsCollector collector;

    @BeforeEach
    void setUp() {
        var properties = new MetricsScrapeProperties();
        var gpu = new MetricsScrapeProperties.Gpu();
        gpu.setEnabled(true);
        gpu.setPodLabel("pod");
        gpu.setNamespaceLabel("namespace");
        properties.setGpu(gpu);
        collector = new GpuMetricsCollector(gpuMetricsReader, new PrometheusTextParser(), properties);
    }

    @Test
    void shouldAggregateMemorySumAndUtilizationAverageAcrossPodGpus() {
        when(gpuMetricsReader.readColocatedExporters(any()))
                .thenReturn(List.of(ResourceUtils.readResource("/metrics-fixtures/dcgm.txt")));

        var result = collector.collect(NAMESPACE, Set.of("model-pod-0", "model-pod-1"), Set.of("node1"));

        // model-pod-0 spans 2 GPUs: memory summed (10240+8192 used, +6144+8192 free MiB), util averaged (75,55)
        var pod0 = result.get("model-pod-0");
        assertThat(pod0).isNotNull();
        assertThat(pod0.utilization()).isCloseTo(0.65, within(1e-9));
        assertThat(pod0.memoryUsedBytes()).isCloseTo(18432 * MIB, within(1.0));
        assertThat(pod0.memoryTotalBytes()).isCloseTo(32768 * MIB, within(1.0));
        assertThat(pod0.memoryUsedBytes()).isLessThanOrEqualTo(pod0.memoryTotalBytes());

        // model-pod-1 is a single GPU
        var pod1 = result.get("model-pod-1");
        assertThat(pod1).isNotNull();
        assertThat(pod1.utilization()).isCloseTo(0.20, within(1e-9));
        assertThat(pod1.memoryUsedBytes()).isCloseTo(4096 * MIB, within(1.0));
        assertThat(pod1.memoryTotalBytes()).isCloseTo(16384 * MIB, within(1.0));
    }

    @Test
    void shouldExcludeSeriesFromOtherTenantsOnTheSameNode() {
        when(gpuMetricsReader.readColocatedExporters(any()))
                .thenReturn(List.of(ResourceUtils.readResource("/metrics-fixtures/dcgm.txt")));

        var result = collector.collect(NAMESPACE, Set.of("model-pod-0", "model-pod-1"), Set.of("node1"));

        // the other-ns/other-tenant-pod series share node1 but must never leak into this deployment
        assertThat(result).containsOnlyKeys("model-pod-0", "model-pod-1");
        assertThat(result).doesNotContainKey("other-tenant-pod");
    }

    @Test
    void shouldOmitPodWithNoTelemetry() {
        when(gpuMetricsReader.readColocatedExporters(any()))
                .thenReturn(List.of(ResourceUtils.readResource("/metrics-fixtures/dcgm.txt")));

        // model-pod-2 requested but the exporter reports nothing for it → absent from the result (null upstream)
        var result = collector.collect(NAMESPACE, Set.of("model-pod-0", "model-pod-2"), Set.of("node1"));

        assertThat(result).containsOnlyKeys("model-pod-0");
    }

    @Test
    void shouldReturnEmpty_whenExporterReturnsNothing() {
        when(gpuMetricsReader.readColocatedExporters(any())).thenReturn(List.of());

        var result = collector.collect(NAMESPACE, Set.of("model-pod-0"), Set.of("node1"));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenNoNodesOrPods() {
        var result = collector.collect(NAMESPACE, Set.of(), Set.of("node1"));
        assertThat(result).isEmpty();
    }

}
