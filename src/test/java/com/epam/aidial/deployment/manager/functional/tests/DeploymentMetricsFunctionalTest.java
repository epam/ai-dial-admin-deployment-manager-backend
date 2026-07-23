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
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsList;
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
        stubPods(KSERVE_SERVICE_LABEL, "metrics-vllm-svc", readyPredictorPod("metrics-vllm-pod-0"));
        stubScrape("metrics-vllm-pod-0", 8080, "/metrics", ResourceUtils.readResource("/metrics-fixtures/vllm.txt"));
        stubPodUsage("metrics-vllm-pod-0", "250m", "1Gi");

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
    void shouldReturnGpuMetricsSnapshotForGpuInferenceDeployment() {
        // Given — a GPU-requesting inference deployment; DCGM exporter co-located on the pod's node
        var id = "metrics-gpu-deployment";
        createGpuInferenceDeployment(id, "metrics-gpu-svc");
        var predictor = readyGpuPredictorPod("metrics-gpu-pod-0", "gpu-node-1");
        stubGpuScenario("metrics-gpu-svc", predictor, "gpu-operator", dcgmExporterPod("dcgm-abc", "gpu-node-1"));
        stubScrape("metrics-gpu-pod-0", 8080, "/metrics", ResourceUtils.readResource("/metrics-fixtures/vllm.txt"));
        var dcgmBody = String.join("\n",
                "DCGM_FI_DEV_GPU_UTIL{namespace=\"default\",pod=\"metrics-gpu-pod-0\",gpu=\"0\"} 80",
                "DCGM_FI_DEV_FB_USED{namespace=\"default\",pod=\"metrics-gpu-pod-0\",gpu=\"0\"} 8192",
                "DCGM_FI_DEV_FB_FREE{namespace=\"default\",pod=\"metrics-gpu-pod-0\",gpu=\"0\"} 8192");
        stubScrapeNs("gpu-operator", "dcgm-abc", 9400, "/metrics", dcgmBody);
        stubPodUsage("metrics-gpu-pod-0", "250m", "1Gi");

        // When
        var metrics = deploymentController.getMetrics(id);

        // Then — GPU block is available and populated on top of the CPU/memory block
        assertThat(metrics.availability().get("resources.gpu").available()).isTrue();
        var pod = metrics.resources().pods().getFirst();
        assertThat(pod.cpuMillicores()).isEqualTo(250.0);
        assertThat(pod.gpuUtilization()).isEqualTo(0.8);
        assertThat(pod.gpuMemoryBytes()).isEqualTo(8192 * 1024d * 1024d);
        assertThat(pod.gpuMemoryTotalBytes()).isEqualTo(16384 * 1024d * 1024d);
    }

    @Test
    void shouldDegradeGpuBlock_whenExporterAbsentForGpuDeployment() {
        // Given — GPU deployment but no DCGM exporter pods on the node
        var id = "metrics-gpu-noexporter";
        createGpuInferenceDeployment(id, "metrics-gpu-noexporter-svc");
        var predictor = readyGpuPredictorPod("metrics-gpu-noexporter-pod-0", "gpu-node-2");
        // no exporter pod: the dcgm namespace lists nothing
        stubGpuScenario("metrics-gpu-noexporter-svc", predictor, "gpu-operator");
        stubScrape("metrics-gpu-noexporter-pod-0", 8080, "/metrics", ResourceUtils.readResource("/metrics-fixtures/vllm.txt"));
        stubPodUsage("metrics-gpu-noexporter-pod-0", "100m", "512Mi");

        // When
        var metrics = deploymentController.getMetrics(id);

        // Then — still 200, GPU block unavailable, CPU/memory unaffected
        assertThat(metrics.availability().get("resources.gpu").available()).isFalse();
        assertThat(metrics.availability().get("resources.gpu").reason()).contains("unavailable");
        assertThat(metrics.availability().get("resources.usage").available()).isTrue();
        assertThat(metrics.resources().pods().getFirst().gpuUtilization()).isNull();
    }

    @Test
    void shouldReturnResourceOnlySnapshotForNimDeployment() {
        // Given — a NIM deployment (not INFERENCE): resource metrics apply, serving does not
        var id = "metrics-nim-deployment";
        createNimDeployment(id, "metrics-nim-svc");
        stubPods(NIM_SERVICE_LABEL, "metrics-nim-svc", readyPod("metrics-nim-pod-0"));
        stubPodUsage("metrics-nim-pod-0", "100m", "512Mi");

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
        stubPods(KSERVE_SERVICE_LABEL, "metrics-scrape-fail-svc", readyPredictorPod("metrics-fail-pod-0"));
        when(kubernetesClient.raw(anyString())).thenReturn(null);
        stubPodUsage("metrics-fail-pod-0", "100m", "256Mi");

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

    private void createGpuInferenceDeployment(String id, String serviceName) {
        CreateDeployment request = CreateInferenceDeployment.builder()
                .id(id)
                .displayName("Metrics test " + id)
                .modelFormat("huggingface")
                .metadata(new DeploymentMetadata(List.of()))
                .resources(new Resources(Map.of("nvidia.com/gpu", "1"), Map.of("nvidia.com/gpu", "1")))
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
        stubScrapeNs(NAMESPACE, podName, port, path, body);
    }

    private void stubScrapeNs(String namespace, String podName, int port, String path, String body) {
        var proxyUri = "/api/v1/namespaces/%s/pods/http:%s:%d/proxy%s".formatted(namespace, podName, port, path);
        when(kubernetesClient.raw(proxyUri)).thenReturn(body);
    }

    /**
     * Stubs pod listing for both the deployment's service pods (in {@code default}) and the DCGM
     * exporter pods (in {@code dcgmNamespace}) off the same {@code client.pods()} mock, differentiated
     * by namespace. An empty {@code exporterPods} models a cluster with no exporter.
     */
    @SuppressWarnings({"unchecked"})
    private void stubGpuScenario(String serviceName, Pod servicePod, String dcgmNamespace, Pod... exporterPods) {
        var svcOp = Mockito.mock(MixedOperation.class);
        var dcgmOp = Mockito.mock(MixedOperation.class);
        when(kubernetesClient.pods()).thenReturn(svcOp);

        var servicePodList = new PodList();
        servicePodList.setItems(List.of(servicePod));
        when(svcOp.inNamespace(NAMESPACE)).thenReturn(svcOp);
        when(svcOp.withLabels(Map.of(KSERVE_SERVICE_LABEL, serviceName))).thenReturn(svcOp);
        when(svcOp.list()).thenReturn(servicePodList);

        var dcgmPodList = new PodList();
        dcgmPodList.setItems(List.of(exporterPods));
        when(svcOp.inNamespace(dcgmNamespace)).thenReturn(dcgmOp);
        when(dcgmOp.withLabels(Map.of("app", "nvidia-dcgm-exporter"))).thenReturn(dcgmOp);
        when(dcgmOp.list()).thenReturn(dcgmPodList);
    }

    private void stubPodUsage(String podName, String cpu, String memory) {
        var containerMetrics = new ContainerMetrics();
        containerMetrics.setUsage(Map.of("cpu", new Quantity(cpu), "memory", new Quantity(memory)));
        var podMetrics = new PodMetrics();
        podMetrics.setMetadata(new ObjectMetaBuilder().withName(podName).build());
        podMetrics.setContainers(List.of(containerMetrics));
        var podMetricsList = new PodMetricsList();
        podMetricsList.setItems(List.of(podMetrics));

        var metricsDsl = Mockito.mock(MetricAPIGroupDSL.class);
        var podMetricOperation = Mockito.mock(PodMetricOperation.class);
        when(kubernetesClient.top()).thenReturn(metricsDsl);
        when(metricsDsl.pods()).thenReturn(podMetricOperation);
        when(podMetricOperation.metrics(NAMESPACE)).thenReturn(podMetricsList);
    }

    /** A Ready KServe predictor pod — carries the {@code component=predictor} label KServe always stamps. */
    private static Pod readyPredictorPod(String name) {
        return new PodBuilder(readyPod(name))
                .editMetadata()
                .addToLabels("component", "predictor")
                .endMetadata()
                .build();
    }

    /** A Ready KServe predictor pod scheduled on {@code nodeName} (needed for co-located exporter discovery). */
    private static Pod readyGpuPredictorPod(String name, String nodeName) {
        return new PodBuilder(readyPredictorPod(name))
                .editOrNewSpec()
                .withNodeName(nodeName)
                .addNewContainer()
                .withName("kserve-container")
                .endContainer()
                .endSpec()
                .build();
    }

    /** A DCGM exporter DaemonSet pod on {@code nodeName}. */
    private static Pod dcgmExporterPod(String name, String nodeName) {
        return new PodBuilder()
                .withNewMetadata()
                .withName(name)
                .withCreationTimestamp("2026-06-05T10:00:00Z")
                .endMetadata()
                .withNewSpec()
                .withNodeName(nodeName)
                .endSpec()
                .build();
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
