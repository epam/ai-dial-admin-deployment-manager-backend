package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.MetricsScrapeProperties;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.metrics.PodResourceUsageReader;
import com.epam.aidial.deployment.manager.model.PodInfo;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.PodResourceUsage;
import com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentManager;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentManagerProvider;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_OPERATIONAL;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_RESOURCES;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_RESOURCES_GPU;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_RESOURCES_USAGE;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_SERVING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeploymentMetricsServiceTest {

    private static final String DEPLOYMENT_ID = "dep-1";
    private static final String NAMESPACE = "model-ns";
    private static final String POD_NAME = "model-pod-0";
    private static final int DEFAULT_PORT = 8080;
    private static final long TIMEOUT_MS = 3000;

    @Mock
    private DeploymentService deploymentService;
    @Mock
    private DeploymentManagerProvider deploymentManagerProvider;
    @Mock
    private DeploymentManager<Object> deploymentManager;
    @Mock
    private K8sClient k8sClient;
    @Mock
    private PodResourceUsageReader podResourceUsageReader;

    private MetricsScrapeProperties properties;
    private DeploymentMetricsService service;

    @BeforeEach
    void setUp() {
        properties = new MetricsScrapeProperties();
        properties.setEnabled(true);
        properties.setTimeoutMs(TIMEOUT_MS);
        properties.setCacheTtlMs(5000);
        var resourceUsage = new MetricsScrapeProperties.ResourceUsage();
        resourceUsage.setEnabled(true);
        properties.setResourceUsage(resourceUsage);
        var rateWindow = new MetricsScrapeProperties.RateWindow();
        properties.setRateWindow(rateWindow);

        var vllmNormalizer = new VllmMetricsNormalizer();
        service = new DeploymentMetricsService(
                deploymentService,
                deploymentManagerProvider,
                k8sClient,
                new PrometheusTextParser(),
                new EngineDetector(),
                List.of(vllmNormalizer, new TgiMetricsNormalizer(), new SglangMetricsNormalizer(),
                        new NimMetricsNormalizer(vllmNormalizer)),
                podResourceUsageReader,
                properties);

        doReturn(deploymentManager).when(deploymentManagerProvider).provide(DEPLOYMENT_ID);
        when(deploymentManager.getNamespace()).thenReturn(NAMESPACE);
        when(deploymentManager.getDefaultContainerPort()).thenReturn(DEFAULT_PORT);
    }

    @Test
    void shouldReturnFullSnapshotForInferenceDeployment() {
        givenDeployment(inferenceDeployment(null));
        givenPods(podInfo(POD_NAME));
        when(k8sClient.scrapePodMetrics(NAMESPACE, POD_NAME, DEFAULT_PORT, "/metrics", TIMEOUT_MS))
                .thenReturn(Optional.of(ResourceUtils.readResource("/metrics-fixtures/vllm.txt")));
        when(podResourceUsageReader.read(NAMESPACE, POD_NAME))
                .thenReturn(Optional.of(new PodResourceUsage(POD_NAME, 250.0, 1073741824.0, null, null)));

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.engine()).isEqualTo(EngineFamily.VLLM);
        assertThat(snapshot.scrapedPod()).isEqualTo(POD_NAME);
        assertThat(snapshot.window()).isEqualTo(UnifiedDeploymentMetrics.WINDOW_LIFETIME);
        assertThat(snapshot.collectedAt()).isNotNull();

        assertThat(snapshot.serving()).isNotNull();
        assertThat(snapshot.serving().kvCacheUsage()).isEqualTo(0.0);
        assertThat(snapshot.operational()).isNotNull();
        assertThat(snapshot.rawCounters()).containsKey("prompt_tokens_total");

        assertThat(snapshot.resources().replicasTotal()).isEqualTo(1);
        assertThat(snapshot.resources().replicasReady()).isEqualTo(1);
        assertThat(snapshot.resources().pods()).hasSize(1);

        // every availability key is present on every response
        assertThat(snapshot.availability()).containsKeys(AVAILABILITY_SERVING, AVAILABILITY_OPERATIONAL,
                AVAILABILITY_RESOURCES, AVAILABILITY_RESOURCES_USAGE, AVAILABILITY_RESOURCES_GPU);
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).available()).isTrue();
        assertThat(snapshot.availability().get(AVAILABILITY_OPERATIONAL).available()).isTrue();
        assertThat(snapshot.availability().get(AVAILABILITY_RESOURCES_USAGE).available()).isTrue();
        assertThat(snapshot.availability().get(AVAILABILITY_RESOURCES_GPU).available()).isFalse();
    }

    @Test
    void shouldUseDeploymentContainerPort_whenConfigured() {
        givenDeployment(inferenceDeployment(9090));
        givenPods(podInfo(POD_NAME));
        when(k8sClient.scrapePodMetrics(anyString(), anyString(), anyInt(), anyString(), anyLong())).thenReturn(Optional.empty());

        service.getSnapshot(DEPLOYMENT_ID);

        verify(k8sClient).scrapePodMetrics(NAMESPACE, POD_NAME, 9090, "/metrics", TIMEOUT_MS);
    }

    @Test
    void shouldFallBackToManagerDefaultPort_whenContainerPortMissing() {
        givenDeployment(inferenceDeployment(null));
        givenPods(podInfo(POD_NAME));
        when(k8sClient.scrapePodMetrics(anyString(), anyString(), anyInt(), anyString(), anyLong())).thenReturn(Optional.empty());

        service.getSnapshot(DEPLOYMENT_ID);

        verify(k8sClient).scrapePodMetrics(NAMESPACE, POD_NAME, DEFAULT_PORT, "/metrics", TIMEOUT_MS);
    }

    @Test
    void shouldScrapeFirstReadyPod_whenMultiplePodsExist() {
        givenDeployment(inferenceDeployment(null));
        when(deploymentManager.getInstances(DEPLOYMENT_ID))
                .thenReturn(List.of(podInfo("pod-a"), podInfo("pod-b"), podInfo("pod-c")));
        when(deploymentManager.getActiveInstances(DEPLOYMENT_ID))
                .thenReturn(List.of(podInfo("pod-b"), podInfo("pod-c")));
        when(k8sClient.scrapePodMetrics(anyString(), anyString(), anyInt(), anyString(), anyLong())).thenReturn(Optional.empty());
        when(podResourceUsageReader.read(anyString(), anyString())).thenReturn(Optional.empty());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.scrapedPod()).isEqualTo("pod-b");
        assertThat(snapshot.resources().replicasTotal()).isEqualTo(3);
        assertThat(snapshot.resources().replicasReady()).isEqualTo(2);
    }

    @Test
    void shouldDegradeServing_whenNoReadyPods() {
        givenDeployment(inferenceDeployment(null));
        when(deploymentManager.getInstances(DEPLOYMENT_ID)).thenReturn(List.of());
        when(deploymentManager.getActiveInstances(DEPLOYMENT_ID)).thenReturn(List.of());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.serving()).isNull();
        assertThat(snapshot.operational()).isNull();
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).available()).isFalse();
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).reason()).contains("no ready pods");
        // replicas are still reported
        assertThat(snapshot.resources().replicasTotal()).isZero();
        assertThat(snapshot.resources().replicasReady()).isZero();
    }

    @Test
    void shouldDegradeServing_whenDeploymentHasNoServiceYet() {
        givenDeployment(inferenceDeployment(null));
        when(deploymentManager.getInstances(DEPLOYMENT_ID))
                .thenThrow(new EntityNotFoundException("Service name not found for deployment: " + DEPLOYMENT_ID));
        when(deploymentManager.getActiveInstances(DEPLOYMENT_ID))
                .thenThrow(new EntityNotFoundException("Service name not found for deployment: " + DEPLOYMENT_ID));

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.serving()).isNull();
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).available()).isFalse();
        assertThat(snapshot.resources().replicasTotal()).isZero();
    }

    @Test
    void shouldDegradeServing_whenScrapeFails() {
        givenDeployment(inferenceDeployment(null));
        givenPods(podInfo(POD_NAME));
        when(k8sClient.scrapePodMetrics(anyString(), anyString(), anyInt(), anyString(), anyLong())).thenReturn(Optional.empty());
        when(podResourceUsageReader.read(NAMESPACE, POD_NAME))
                .thenReturn(Optional.of(new PodResourceUsage(POD_NAME, 100.0, 1000.0, null, null)));

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.serving()).isNull();
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).available()).isFalse();
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).reason()).contains("unreachable");
        // resource block is unaffected by the scrape failure
        assertThat(snapshot.resources().pods()).hasSize(1);
        assertThat(snapshot.availability().get(AVAILABILITY_RESOURCES_USAGE).available()).isTrue();
    }

    @Test
    void shouldReportUnknownEngine_whenVocabularyUnrecognized() {
        givenDeployment(inferenceDeployment(null));
        givenPods(podInfo(POD_NAME));
        when(k8sClient.scrapePodMetrics(anyString(), anyString(), anyInt(), anyString(), anyLong()))
                .thenReturn(Optional.of("# TYPE http_requests_total counter\nhttp_requests_total 5\n"));
        when(podResourceUsageReader.read(anyString(), anyString())).thenReturn(Optional.empty());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.engine()).isEqualTo(EngineFamily.UNKNOWN);
        assertThat(snapshot.serving()).isNull();
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).reason()).contains("not recognized");
    }

    @Test
    void shouldReturnNimSnapshotThroughSameContract() {
        givenDeployment(NimDeployment.builder().id(DEPLOYMENT_ID).build());
        givenPods(podInfo(POD_NAME));
        when(deploymentManager.getDefaultContainerPort()).thenReturn(8000);
        when(k8sClient.scrapePodMetrics(NAMESPACE, POD_NAME, 8000, "/v1/metrics", TIMEOUT_MS))
                .thenReturn(Optional.of(ResourceUtils.readResource("/metrics-fixtures/nim-llm.txt")));
        when(podResourceUsageReader.read(anyString(), anyString())).thenReturn(Optional.empty());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.engine()).isEqualTo(EngineFamily.NIM);
        assertThat(snapshot.serving()).isNotNull();
        assertThat(snapshot.serving().kvCacheUsage()).isEqualTo(0.0);
        assertThat(snapshot.window()).isEqualTo(UnifiedDeploymentMetrics.WINDOW_LIFETIME);
    }

    @Test
    void shouldFallBackToStandardMetricsPath_whenNimV1PathUnavailable() {
        givenDeployment(NimDeployment.builder().id(DEPLOYMENT_ID).build());
        givenPods(podInfo(POD_NAME));
        when(deploymentManager.getDefaultContainerPort()).thenReturn(8000);
        when(k8sClient.scrapePodMetrics(NAMESPACE, POD_NAME, 8000, "/v1/metrics", TIMEOUT_MS))
                .thenReturn(Optional.empty());
        when(k8sClient.scrapePodMetrics(NAMESPACE, POD_NAME, 8000, "/metrics", TIMEOUT_MS))
                .thenReturn(Optional.of(ResourceUtils.readResource("/metrics-fixtures/nim-triton.txt")));
        when(podResourceUsageReader.read(anyString(), anyString())).thenReturn(Optional.empty());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.engine()).isEqualTo(EngineFamily.NIM);
        assertThat(snapshot.serving()).isNotNull();
        verify(k8sClient).scrapePodMetrics(NAMESPACE, POD_NAME, 8000, "/v1/metrics", TIMEOUT_MS);
        verify(k8sClient).scrapePodMetrics(NAMESPACE, POD_NAME, 8000, "/metrics", TIMEOUT_MS);
    }

    @Test
    void shouldSkipPodUsage_whenResourceUsageDisabled() {
        properties.getResourceUsage().setEnabled(false);
        givenDeployment(inferenceDeployment(null));
        givenPods(podInfo(POD_NAME));
        when(k8sClient.scrapePodMetrics(anyString(), anyString(), anyInt(), anyString(), anyLong())).thenReturn(Optional.empty());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.resources().pods()).isEmpty();
        assertThat(snapshot.availability().get(AVAILABILITY_RESOURCES_USAGE).available()).isFalse();
        assertThat(snapshot.availability().get(AVAILABILITY_RESOURCES_USAGE).reason()).contains("disabled");
    }

    @Test
    void shouldDegradePodUsage_whenMetricsServerAbsent() {
        givenDeployment(inferenceDeployment(null));
        givenPods(podInfo(POD_NAME));
        when(k8sClient.scrapePodMetrics(anyString(), anyString(), anyInt(), anyString(), anyLong()))
                .thenReturn(Optional.of(ResourceUtils.readResource("/metrics-fixtures/vllm.txt")));
        when(podResourceUsageReader.read(anyString(), anyString())).thenReturn(Optional.empty());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.resources().pods()).isEmpty();
        assertThat(snapshot.availability().get(AVAILABILITY_RESOURCES_USAGE).available()).isFalse();
        // serving block is unaffected by the resource degradation
        assertThat(snapshot.serving()).isNotNull();
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).available()).isTrue();
    }

    @Test
    void shouldFailGetSnapshot_whenMetricsDisabledByConfiguration() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.getSnapshot(DEPLOYMENT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void shouldFailGetSnapshot_whenDeploymentTypeUnsupported() {
        givenDeployment(McpDeployment.builder().id(DEPLOYMENT_ID).build());

        assertThatThrownBy(() -> service.getSnapshot(DEPLOYMENT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not support model metrics");
    }

    @Test
    void shouldFailGetSnapshot_whenDeploymentNotFound() {
        when(deploymentService.getDeployment(DEPLOYMENT_ID, false)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSnapshot(DEPLOYMENT_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(DEPLOYMENT_ID);
    }

    private void givenDeployment(Deployment deployment) {
        when(deploymentService.getDeployment(DEPLOYMENT_ID, false)).thenReturn(Optional.of(deployment));
    }

    private void givenPods(PodInfo pod) {
        when(deploymentManager.getInstances(DEPLOYMENT_ID)).thenReturn(List.of(pod));
        when(deploymentManager.getActiveInstances(DEPLOYMENT_ID)).thenReturn(List.of(pod));
    }

    private static InferenceDeployment inferenceDeployment(Integer containerPort) {
        return InferenceDeployment.builder()
                .id(DEPLOYMENT_ID)
                .containerPort(containerPort)
                .build();
    }

    private static PodInfo podInfo(String name) {
        return new PodInfo(name, Instant.now(), 0, null, null, null, null, null);
    }

}
