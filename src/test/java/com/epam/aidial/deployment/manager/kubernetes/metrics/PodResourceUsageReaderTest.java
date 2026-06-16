package com.epam.aidial.deployment.manager.kubernetes.metrics;

import com.epam.aidial.deployment.manager.model.metrics.PodResourceUsage;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MetricAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.PodMetricOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void shouldReadAllWantedPodsWithOneNamespaceCall() {
        // Given — namespace metrics for three pods; only two are wanted
        when(podMetricOperation.metrics(NAMESPACE)).thenReturn(podMetricsList(
                podMetrics("pod-a", "250m", "1Gi"),
                podMetrics("pod-b", "100m", "512Mi"),
                podMetrics("pod-c", "50m", "256Mi")));

        // When
        var result = reader.readAll(NAMESPACE, wanted("pod-a", "pod-c"));

        // Then — filtered to the wanted pods via a single namespace-wide call (no per-pod calls)
        assertThat(result).extracting(PodResourceUsage::name).containsExactlyInAnyOrder("pod-a", "pod-c");
        verify(podMetricOperation).metrics(NAMESPACE);
        verify(podMetricOperation, never()).metrics(eq(NAMESPACE), anyString());
    }

    @Test
    void shouldSumOnlyPrimaryContainer_droppingSidecars() {
        // Given — a workload container plus injected sidecars in the same pod
        var podMetrics = new PodMetrics();
        podMetrics.setMetadata(new ObjectMetaBuilder().withName("pod-a").build());
        podMetrics.setContainers(List.of(
                namedContainerMetrics("kserve-container", "250m", "1Gi"),
                namedContainerMetrics("queue-proxy", "100m", "512Mi"),
                namedContainerMetrics("istio-proxy", "50m", "256Mi")));
        when(podMetricOperation.metrics(NAMESPACE)).thenReturn(podMetricsList(podMetrics));

        // When — the primary container is named for the pod
        var result = reader.readAll(NAMESPACE, Map.of("pod-a", "kserve-container"));

        // Then — only the primary container's usage is reported, sidecars excluded
        assertThat(result).singleElement().satisfies(usage -> {
            assertThat(usage.cpuMillicores()).isCloseTo(250.0, within(1e-6));
            assertThat(usage.memoryBytes()).isCloseTo(1073741824.0, within(1e-6));
        });
    }

    @Test
    void shouldSumAllContainers_whenPrimaryContainerAbsentFromMetrics() {
        // Given — the named primary container isn't present in the metrics
        var podMetrics = new PodMetrics();
        podMetrics.setMetadata(new ObjectMetaBuilder().withName("pod-a").build());
        podMetrics.setContainers(List.of(
                namedContainerMetrics("queue-proxy", "100m", "512Mi"),
                namedContainerMetrics("istio-proxy", "50m", "256Mi")));
        when(podMetricOperation.metrics(NAMESPACE)).thenReturn(podMetricsList(podMetrics));

        // When
        var result = reader.readAll(NAMESPACE, Map.of("pod-a", "kserve-container"));

        // Then — falls back to summing all containers
        assertThat(result).singleElement().satisfies(usage ->
                assertThat(usage.cpuMillicores()).isCloseTo(150.0, within(1e-6)));
    }

    @Test
    void shouldReadAllToEmpty_whenMetricsServerAbsent() {
        when(podMetricOperation.metrics(NAMESPACE))
                .thenThrow(new KubernetesClientException("the server could not find the requested resource", 404, null));

        assertThat(reader.readAll(NAMESPACE, wanted("pod-a"))).isEmpty();
    }

    /** Wanted pods with no primary-container hint — usage sums all containers. */
    private static Map<String, String> wanted(String... podNames) {
        var map = new HashMap<String, String>();
        for (var name : podNames) {
            map.put(name, null);
        }
        return map;
    }

    private static PodMetricsList podMetricsList(PodMetrics... items) {
        var list = new PodMetricsList();
        list.setItems(List.of(items));
        return list;
    }

    private static PodMetrics podMetrics(String name, String cpu, String memory) {
        var podMetrics = new PodMetrics();
        podMetrics.setMetadata(new ObjectMetaBuilder().withName(name).build());
        podMetrics.setContainers(List.of(containerMetrics(cpu, memory)));
        return podMetrics;
    }

    private static ContainerMetrics containerMetrics(String cpu, String memory) {
        var containerMetrics = new ContainerMetrics();
        containerMetrics.setUsage(Map.of("cpu", new Quantity(cpu), "memory", new Quantity(memory)));
        return containerMetrics;
    }

    private static ContainerMetrics namedContainerMetrics(String name, String cpu, String memory) {
        var containerMetrics = containerMetrics(cpu, memory);
        containerMetrics.setName(name);
        return containerMetrics;
    }

}
