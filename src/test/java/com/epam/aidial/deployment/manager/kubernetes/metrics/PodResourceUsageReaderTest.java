package com.epam.aidial.deployment.manager.kubernetes.metrics;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MetricAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.PodMetricOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PodResourceUsageReaderTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String POD_NAME = "test-pod";

    @Mock
    private KubernetesClient kubernetesClient;
    @Mock
    private MetricAPIGroupDSL metricApiGroupDsl;
    @Mock
    private PodMetricOperation podMetricOperation;

    private PodResourceUsageReader reader;

    @BeforeEach
    void setUp() {
        reader = new PodResourceUsageReader(kubernetesClient);
        when(kubernetesClient.top()).thenReturn(metricApiGroupDsl);
        when(metricApiGroupDsl.pods()).thenReturn(podMetricOperation);
    }

    @Test
    void shouldReadPodCpuAndMemoryUsage() {
        // Given — two containers; usage sums across them
        var podMetrics = new PodMetrics();
        podMetrics.setContainers(List.of(
                containerMetrics("250m", "1Gi"),
                containerMetrics("100m", "512Mi")));
        when(podMetricOperation.metrics(NAMESPACE, POD_NAME)).thenReturn(podMetrics);

        // When
        var result = reader.read(NAMESPACE, POD_NAME);

        // Then
        assertThat(result).isPresent();
        var usage = result.get();
        assertThat(usage.name()).isEqualTo(POD_NAME);
        assertThat(usage.cpuMillicores()).isCloseTo(350.0, within(1e-6));
        assertThat(usage.memoryBytes()).isCloseTo(1073741824.0 + 536870912.0, within(1e-6));
        // GPU fields require DCGM (follow-up) — always null in the PoC
        assertThat(usage.gpuUtilization()).isNull();
        assertThat(usage.gpuMemoryUsedBytes()).isNull();
    }

    @Test
    void shouldFailReadToEmpty_whenMetricsServerAbsent() {
        // Given — metrics.k8s.io not served
        when(podMetricOperation.metrics(NAMESPACE, POD_NAME))
                .thenThrow(new KubernetesClientException("the server could not find the requested resource", 404, null));

        // When / Then
        assertThat(reader.read(NAMESPACE, POD_NAME)).isEmpty();
    }

    @Test
    void shouldFailReadToEmpty_whenNoContainerMetrics() {
        when(podMetricOperation.metrics(NAMESPACE, POD_NAME)).thenReturn(new PodMetrics());

        assertThat(reader.read(NAMESPACE, POD_NAME)).isEmpty();
    }

    private static ContainerMetrics containerMetrics(String cpu, String memory) {
        var containerMetrics = new ContainerMetrics();
        containerMetrics.setUsage(Map.of("cpu", new Quantity(cpu), "memory", new Quantity(memory)));
        return containerMetrics;
    }

}
