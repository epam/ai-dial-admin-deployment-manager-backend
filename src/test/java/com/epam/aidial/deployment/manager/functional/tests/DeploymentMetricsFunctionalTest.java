package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateNimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.HuggingFaceSource;
import com.epam.aidial.deployment.manager.model.deployment.InternalImageSource;
import com.epam.aidial.deployment.manager.model.deployment.NgcRegistrySource;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.epam.aidial.deployment.manager.web.controller.DeploymentController;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MetricAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodMetricOperation;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * End-to-end (controller → service → mapper) coverage of the deployment metrics snapshot with
 * the {@code FunctionalTestConfiguration}-mocked {@link KubernetesClient}: the API-server proxy
 * scrape is stubbed via {@code client.raw(...)} and pod resource usage via {@code client.top()},
 * per the {@link FullWorkflowWithMockedK8sClientFunctionalTest} pattern.
 */
public abstract class DeploymentMetricsFunctionalTest {

    private static final String NAMESPACE = "default";
    private static final String KSERVE_SERVICE_LABEL = "serving.kserve.io/inferenceservice";
    private static final String NIM_SERVICE_LABEL = "app.kubernetes.io/name";

    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private DeploymentRepository deploymentRepository;
    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private DeploymentController deploymentController;
    @Autowired
    private KubernetesClient kubernetesClient;

    @Test
    void shouldReturnVllmMetricsSnapshotForInferenceDeployment() {
        // Given
        var id = "metrics-vllm-deployment";
        createInferenceDeployment(id, "metrics-vllm-svc");
        stubPods(KSERVE_SERVICE_LABEL, "metrics-vllm-svc", readyPod("metrics-vllm-pod-0"));
        stubScrape("metrics-vllm-pod-0", 8080, "/metrics", ResourceUtils.readResource("/metrics-fixtures/vllm.txt"));
        stubPodUsage("250m", "1Gi");

        // When
        var metrics = deploymentController.getMetrics(id);

        // Then
        assertThat(metrics.engine()).isEqualTo("VLLM");
        assertThat(metrics.scrapedPod()).isEqualTo("metrics-vllm-pod-0");
        assertThat(metrics.window()).isEqualTo("lifetime");
        assertThat(metrics.collectedAt()).isNotNull();

        assertThat(metrics.serving()).isNotNull();
        // values from the real dev-cluster vLLM V1 capture (metrics-fixtures/vllm.txt)
        assertThat(metrics.serving().kvCacheUsage()).isEqualTo(0.0);
        assertThat(metrics.serving().runningRequests()).isZero();
        assertThat(metrics.serving().ttft().count()).isEqualTo(14);
        assertThat(metrics.serving().interTokenLatency().count()).isEqualTo(690);
        assertThat(metrics.serving().tokensPerSecond().prompt()).isPositive();

        assertThat(metrics.operational()).isNotNull();
        assertThat(metrics.operational().requestErrorRatio()).isEqualTo(0.0);

        assertThat(metrics.resources().replicas().total()).isEqualTo(1);
        assertThat(metrics.resources().replicas().ready()).isEqualTo(1);
        assertThat(metrics.resources().pods()).hasSize(1);
        assertThat(metrics.resources().pods().getFirst().cpuMillicores()).isEqualTo(250.0);
        assertThat(metrics.resources().pods().getFirst().gpuUtilization()).isNull();

        assertThat(metrics.rawCounters())
                .containsEntry("prompt_tokens_total", 34304.0)
                .containsEntry("request_success_total", 14.0);

        assertThat(metrics.availability()).containsKeys("serving", "operational", "resources", "resources.usage", "resources.gpu");
        assertThat(metrics.availability().get("serving").available()).isTrue();
        assertThat(metrics.availability().get("resources.gpu").available()).isFalse();
    }

    @Test
    void shouldReturnResourceOnlySnapshotForNimDeployment() {
        // Given — a NIM deployment (not INFERENCE): resource metrics apply, serving does not
        var id = "metrics-nim-deployment";
        createNimDeployment(id, "metrics-nim-svc");
        stubPods(NIM_SERVICE_LABEL, "metrics-nim-svc", readyPod("metrics-nim-pod-0"));
        stubPodUsage("100m", "512Mi");

        // When
        var metrics = deploymentController.getMetrics(id);

        // Then — resource block populated, serving block honestly unavailable, no scrape attempted
        assertThat(metrics.engine()).isEqualTo("UNKNOWN");
        assertThat(metrics.scrapedPod()).isNull();
        assertThat(metrics.serving()).isNull();
        assertThat(metrics.operational()).isNull();
        assertThat(metrics.resources().replicas().total()).isEqualTo(1);
        assertThat(metrics.resources().replicas().ready()).isEqualTo(1);
        assertThat(metrics.resources().pods()).hasSize(1);
        assertThat(metrics.resources().pods().getFirst().cpuMillicores()).isEqualTo(100.0);
        assertThat(metrics.availability().get("resources").available()).isTrue();
        assertThat(metrics.availability().get("resources.usage").available()).isTrue();
        assertThat(metrics.availability().get("serving").available()).isFalse();
        assertThat(metrics.availability().get("serving").reason()).contains("inference");
        Mockito.verify(kubernetesClient, Mockito.never()).raw(anyString());
    }

    @Test
    void shouldReturnPartialSnapshot_whenNoReadyPods() {
        // Given — deployment exists but no pods are running
        var id = "metrics-no-pods-deployment";
        createInferenceDeployment(id, "metrics-no-pods-svc");
        stubPods(KSERVE_SERVICE_LABEL, "metrics-no-pods-svc");

        // When
        var metrics = deploymentController.getMetrics(id);

        // Then — still a successful partial payload, never an error
        assertThat(metrics.serving()).isNull();
        assertThat(metrics.operational()).isNull();
        assertThat(metrics.availability().get("serving").available()).isFalse();
        assertThat(metrics.availability().get("serving").reason()).contains("no ready pods");
        assertThat(metrics.resources().replicas().total()).isZero();
        assertThat(metrics.resources().replicas().ready()).isZero();
    }

    @Test
    void shouldReturnPartialSnapshot_whenScrapeFails() {
        // Given — Ready pod but the metrics endpoint is unreachable
        var id = "metrics-scrape-fail-deployment";
        createInferenceDeployment(id, "metrics-scrape-fail-svc");
        stubPods(KSERVE_SERVICE_LABEL, "metrics-scrape-fail-svc", readyPod("metrics-fail-pod-0"));
        when(kubernetesClient.raw(anyString())).thenReturn(null);
        stubPodUsage("100m", "256Mi");

        // When
        var metrics = deploymentController.getMetrics(id);

        // Then
        assertThat(metrics.serving()).isNull();
        assertThat(metrics.availability().get("serving").available()).isFalse();
        assertThat(metrics.availability().get("serving").reason()).contains("unreachable");
        // the resource block is unaffected
        assertThat(metrics.resources().pods()).hasSize(1);
        assertThat(metrics.availability().get("resources.usage").available()).isTrue();
    }

    @Test
    void shouldReturnResourceOnlySnapshotForNonModelDeployment() {
        // Given — an interceptor deployment (not a model type): resource metrics still apply
        var imageDefinition = imageDefinitionService.createImageDefinition(
                FunctionalTestHelper.createInterceptorImageDefinition());
        imageDefinitionService.completeBuildSuccessfully(imageDefinition.getId(), "test-image", System.currentTimeMillis());
        var id = "metrics-interceptor-deployment";
        var request = CreateInterceptorDeployment.builder()
                .id(id)
                .displayName("Metrics interceptor type")
                .metadata(new DeploymentMetadata(List.of()))
                .resources(new Resources(Map.of(), Map.of()))
                .source(new InternalImageSource(imageDefinition.getId(), null, null, null))
                .allowedDomains(List.of())
                .containerPort(8080)
                .build();
        deploymentService.createDeployment(request);

        // When — the endpoint accepts every deployment type now (no 400 for the type)
        var metrics = deploymentController.getMetrics(id);

        // Then — replica counts are reported (zero here, no service yet); serving honestly unavailable
        assertThat(metrics.engine()).isEqualTo("UNKNOWN");
        assertThat(metrics.serving()).isNull();
        assertThat(metrics.resources()).isNotNull();
        assertThat(metrics.resources().replicas().total()).isZero();
        assertThat(metrics.availability().get("resources").available()).isTrue();
        assertThat(metrics.availability().get("serving").available()).isFalse();
        assertThat(metrics.availability().get("serving").reason()).contains("inference");
    }

    @Test
    void shouldFailGetMetrics_whenDeploymentNotFound() {
        // When / Then — maps to 404 ErrorView via DefaultExceptionHandler
        assertThatThrownBy(() -> deploymentController.getMetrics("metrics-missing-deployment"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("metrics-missing-deployment");
    }

    private void createInferenceDeployment(String id, String serviceName) {
        CreateDeployment request = CreateInferenceDeployment.builder()
                .id(id)
                .displayName("Metrics test " + id)
                .modelFormat("huggingface")
                .metadata(new DeploymentMetadata(List.of()))
                .resources(new Resources(Map.of(), Map.of()))
                .source(new HuggingFaceSource("test-org/metrics-test-model"))
                .allowedDomains(List.of())
                .containerPort(8080)
                .build();
        deploymentService.createDeployment(request);
        deploymentRepository.updateServiceName(id, serviceName);
    }

    private void createNimDeployment(String id, String serviceName) {
        CreateDeployment request = CreateNimDeployment.builder()
                .id(id)
                .displayName("Metrics test " + id)
                .metadata(new DeploymentMetadata(List.of()))
                .resources(new Resources(Map.of(), Map.of()))
                .source(new NgcRegistrySource("nvcr.io/nim/test-model:latest"))
                .allowedDomains(List.of())
                .containerPort(8000)
                .containerGrpcPort(50051)
                .build();
        deploymentService.createDeployment(request);
        deploymentRepository.updateServiceName(id, serviceName);
    }

    @SuppressWarnings({"unchecked"})
    private void stubPods(String labelKey, String serviceName, Pod... pods) {
        var podOperation = Mockito.mock(MixedOperation.class);
        var podList = new PodList();
        podList.setItems(List.of(pods));
        when(kubernetesClient.pods()).thenReturn(podOperation);
        when(podOperation.inNamespace(NAMESPACE)).thenReturn(podOperation);
        when(podOperation.withLabels(Map.of(labelKey, serviceName))).thenReturn(podOperation);
        when(podOperation.list()).thenReturn(podList);
    }

    private void stubScrape(String podName, int port, String path, String body) {
        var proxyUri = "/api/v1/namespaces/%s/pods/http:%s:%d/proxy%s".formatted(NAMESPACE, podName, port, path);
        when(kubernetesClient.raw(proxyUri)).thenReturn(body);
    }

    private void stubPodUsage(String cpu, String memory) {
        var containerMetrics = new ContainerMetrics();
        containerMetrics.setUsage(Map.of("cpu", new Quantity(cpu), "memory", new Quantity(memory)));
        var podMetrics = new PodMetrics();
        podMetrics.setContainers(List.of(containerMetrics));

        var metricsDsl = Mockito.mock(MetricAPIGroupDSL.class);
        var podMetricOperation = Mockito.mock(PodMetricOperation.class);
        when(kubernetesClient.top()).thenReturn(metricsDsl);
        when(metricsDsl.pods()).thenReturn(podMetricOperation);
        when(podMetricOperation.metrics(eq(NAMESPACE), anyString())).thenReturn(podMetrics);
    }

    private static Pod readyPod(String name) {
        return new PodBuilder()
                .withNewMetadata()
                .withName(name)
                .withCreationTimestamp("2026-06-05T10:00:00Z")
                .endMetadata()
                .withNewStatus()
                .addNewContainerStatus()
                .withNewState()
                .withNewRunning()
                .endRunning()
                .endState()
                .withRestartCount(0)
                .endContainerStatus()
                .endStatus()
                .build();
    }

}
