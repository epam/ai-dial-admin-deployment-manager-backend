package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.MetricsScrapeProperties;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.exception.MetricsCollectionDisabledException;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.metrics.PodResourceUsageReader;
import com.epam.aidial.deployment.manager.model.PodInfo;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
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
import java.util.Map;
import java.util.Optional;

import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_OPERATIONAL;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_RESOURCES;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_RESOURCES_GPU;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_RESOURCES_USAGE;
import static com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics.AVAILABILITY_SERVING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
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

        var inferenceCollector = new InferenceServingMetricsCollector(
                k8sClient,
                new PrometheusTextParser(),
                new EngineDetector(),
                List.of(new VllmMetricsNormalizer(), new TgiMetricsNormalizer(), new SglangMetricsNormalizer(),
                        new KserveModelServerMetricsNormalizer()),
                properties);

        service = new DeploymentMetricsService(
                deploymentService,
                deploymentManagerProvider,
                List.of(inferenceCollector),
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
        when(podResourceUsageReader.readAll(eq(NAMESPACE), eq(Map.of(POD_NAME, "kserve-container"))))
                .thenReturn(List.of(new PodResourceUsage(POD_NAME, 250.0, 1073741824.0, null, null)));

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
    void shouldDegradeServing_whenReadyPodsHaveNoComponentLabel() {
        // KServe stamps component=predictor/transformer on every pod it manages, so unlabelled Ready
        // pods can't occur for this KServe-only collector; if they ever did, degrade with a truthful
        // reason rather than scraping whichever pod sorts first. Resource metrics still count replicas.
        givenDeployment(inferenceDeployment(null));
        when(deploymentManager.getInstancesWithReadiness(DEPLOYMENT_ID))
                .thenReturn(new DeploymentManager.PodInstances(
                        List.of(podInfo("pod-a", null), podInfo("pod-b", null), podInfo("pod-c", null)),
                        List.of(podInfo("pod-b", null), podInfo("pod-c", null))));
        when(podResourceUsageReader.readAll(anyString(), any())).thenReturn(List.of());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.scrapedPod()).isNull();
        assertThat(snapshot.serving()).isNull();
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).reason()).contains("no ready predictor");
        assertThat(snapshot.resources().replicasTotal()).isEqualTo(3);
        assertThat(snapshot.resources().replicasReady()).isEqualTo(2);
        verify(k8sClient, never()).scrapePodMetrics(anyString(), anyString(), anyInt(), anyString(), anyLong());
    }

    @Test
    void shouldScrapePredictorPod_whenTransformerSortsFirst() {
        givenDeployment(inferenceDeployment(null));
        // KServe returns both components under one InferenceService label in an undefined order;
        // the transformer exposes no engine metrics, so the predictor must be scraped regardless.
        givenPods(List.of(podInfo("transformer-pod", "transformer"), podInfo("predictor-pod", "predictor")));
        when(k8sClient.scrapePodMetrics(anyString(), anyString(), anyInt(), anyString(), anyLong())).thenReturn(Optional.empty());
        when(podResourceUsageReader.readAll(anyString(), any())).thenReturn(List.of());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.scrapedPod()).isEqualTo("predictor-pod");
        verify(k8sClient).scrapePodMetrics(NAMESPACE, "predictor-pod", DEFAULT_PORT, "/metrics", TIMEOUT_MS);
    }

    @Test
    void shouldScrapeLowestNamedPredictor_whenMultipleReplicas() {
        givenDeployment(inferenceDeployment(null));
        // KServe returns predictor replicas in undefined order; selection must be deterministic
        // (lowest pod name) so the same replica is sampled across polls.
        givenPods(List.of(podInfo("predictor-b", "predictor"), podInfo("predictor-a", "predictor")));
        when(k8sClient.scrapePodMetrics(NAMESPACE, "predictor-a", DEFAULT_PORT, "/metrics", TIMEOUT_MS))
                .thenReturn(Optional.of(ResourceUtils.readResource("/metrics-fixtures/vllm.txt")));
        when(podResourceUsageReader.readAll(anyString(), any())).thenReturn(List.of());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.scrapedPod()).isEqualTo("predictor-a");
        assertThat(snapshot.engine()).isEqualTo(EngineFamily.VLLM);
        verify(k8sClient).scrapePodMetrics(NAMESPACE, "predictor-a", DEFAULT_PORT, "/metrics", TIMEOUT_MS);
    }

    @Test
    void shouldDegradeServing_whenComponentLabelledButNoReadyPredictor() {
        givenDeployment(inferenceDeployment(null));
        // Chained InferenceService whose predictor is still starting: only the transformer is Ready.
        // The transformer carries no engine metrics, so this must degrade as "no ready predictor"
        // rather than scraping it and misreporting an unrecognized engine.
        givenPods(podInfo("transformer-pod", "transformer"));
        when(podResourceUsageReader.readAll(anyString(), any())).thenReturn(List.of());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.engine()).isEqualTo(EngineFamily.UNKNOWN);
        assertThat(snapshot.serving()).isNull();
        assertThat(snapshot.scrapedPod()).isNull();
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).reason()).contains("no ready predictor");
        verify(k8sClient, never()).scrapePodMetrics(anyString(), anyString(), anyInt(), anyString(), anyLong());
    }

    @Test
    void shouldDegradeServing_whenNoReadyPods() {
        givenDeployment(inferenceDeployment(null));
        when(deploymentManager.getInstancesWithReadiness(DEPLOYMENT_ID))
                .thenReturn(new DeploymentManager.PodInstances(List.of(), List.of()));

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
        when(deploymentManager.getInstancesWithReadiness(DEPLOYMENT_ID))
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
        when(podResourceUsageReader.readAll(eq(NAMESPACE), eq(Map.of(POD_NAME, "kserve-container"))))
                .thenReturn(List.of(new PodResourceUsage(POD_NAME, 100.0, 1000.0, null, null)));

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
        when(podResourceUsageReader.readAll(anyString(), any())).thenReturn(List.of());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.engine()).isEqualTo(EngineFamily.UNKNOWN);
        assertThat(snapshot.serving()).isNull();
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).reason()).contains("not recognized");
    }

    @Test
    void shouldReturnTgiSnapshotThroughSameContract() {
        givenDeployment(inferenceDeployment(null));
        givenPods(podInfo(POD_NAME));
        when(k8sClient.scrapePodMetrics(NAMESPACE, POD_NAME, DEFAULT_PORT, "/metrics", TIMEOUT_MS))
                .thenReturn(Optional.of(ResourceUtils.readResource("/metrics-fixtures/tgi.txt")));
        when(podResourceUsageReader.readAll(anyString(), any())).thenReturn(List.of());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.engine()).isEqualTo(EngineFamily.TGI);
        assertThat(snapshot.serving()).isNotNull();
        assertThat(snapshot.operational()).isNotNull();
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).available()).isTrue();
        assertThat(snapshot.availability().get(AVAILABILITY_OPERATIONAL).available()).isTrue();
    }

    @Test
    void shouldReturnSglangSnapshotThroughSameContract() {
        givenDeployment(inferenceDeployment(null));
        givenPods(podInfo(POD_NAME));
        when(k8sClient.scrapePodMetrics(NAMESPACE, POD_NAME, DEFAULT_PORT, "/metrics", TIMEOUT_MS))
                .thenReturn(Optional.of(ResourceUtils.readResource("/metrics-fixtures/sglang.txt")));
        when(podResourceUsageReader.readAll(anyString(), any())).thenReturn(List.of());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.engine()).isEqualTo(EngineFamily.SGLANG);
        assertThat(snapshot.serving()).isNotNull();
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).available()).isTrue();
    }

    @Test
    void shouldReturnKserveModelServerSnapshot_combiningPredictorAndTransformer() {
        givenDeployment(inferenceDeployment(null));
        // The transformer advertises a Prometheus endpoint (prometheus.kserve.io/port present), so it is scraped.
        givenPods(List.of(podInfo("transformer-pod", "transformer", DEFAULT_PORT, "/metrics"),
                podInfo("predictor-pod", "predictor")));
        when(k8sClient.scrapePodMetrics(NAMESPACE, "predictor-pod", DEFAULT_PORT, "/metrics", TIMEOUT_MS))
                .thenReturn(Optional.of(ResourceUtils.readResource("/metrics-fixtures/kserve-modelserver-predictor.txt")));
        when(k8sClient.scrapePodMetrics(NAMESPACE, "transformer-pod", DEFAULT_PORT, "/metrics", TIMEOUT_MS))
                .thenReturn(Optional.of(ResourceUtils.readResource("/metrics-fixtures/kserve-modelserver-transformer.txt")));
        when(podResourceUsageReader.readAll(anyString(), any())).thenReturn(List.of());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.engine()).isEqualTo(EngineFamily.KSERVE_MODELSERVER);
        assertThat(snapshot.scrapedPod()).isEqualTo("predictor-pod");

        var serving = snapshot.serving();
        assertThat(serving).isNotNull();
        assertThat(serving.requestLatency()).isNotNull();
        assertThat(serving.requestLatency().mean()).isCloseTo(0.2, within(1e-9));
        assertThat(serving.requestsPerSecond()).isNotNull();
        // generative fields do not apply to this engine family
        assertThat(serving.ttft()).isNull();
        assertThat(serving.kvCacheUsage()).isNull();
        assertThat(serving.queueDepth()).isNull();

        var operational = snapshot.operational();
        // e2e mean combines transformer pre (0.05) + predictor predict (0.2) + transformer post (0.03)
        assertThat(operational.e2eLatency()).isNotNull();
        assertThat(operational.e2eLatency().mean()).isCloseTo(0.28, within(1e-9));
        assertThat(operational.e2eLatency().count()).isEqualTo(100);
        // cross-pod histogram percentiles are not additive
        assertThat(operational.e2eLatency().p50()).isNull();
        assertThat(operational.requestErrorRatio()).isNull();

        assertThat(snapshot.rawCounters())
                .containsEntry("request_predict_total", 100.0)
                .containsEntry("request_preprocess_total", 100.0)
                .containsEntry("request_postprocess_total", 100.0);

        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).available()).isTrue();
        assertThat(snapshot.availability().get(AVAILABILITY_OPERATIONAL).available()).isTrue();
    }

    @Test
    void shouldReturnKserveModelServerSnapshot_predictorOnly_whenNoTransformer() {
        givenDeployment(inferenceDeployment(null));
        givenPods(podInfo("predictor-pod", "predictor"));
        when(k8sClient.scrapePodMetrics(NAMESPACE, "predictor-pod", DEFAULT_PORT, "/metrics", TIMEOUT_MS))
                .thenReturn(Optional.of(ResourceUtils.readResource("/metrics-fixtures/kserve-modelserver-predictor.txt")));
        when(podResourceUsageReader.readAll(anyString(), any())).thenReturn(List.of());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.engine()).isEqualTo(EngineFamily.KSERVE_MODELSERVER);
        var operational = snapshot.operational();
        // without a transformer e2e is the predict histogram itself, percentiles included
        assertThat(operational.e2eLatency().mean()).isCloseTo(0.2, within(1e-9));
        assertThat(operational.e2eLatency().p50()).isNotNull();
        // pre/post counters absent on the predictor-only fixture
        assertThat(snapshot.rawCounters())
                .containsEntry("request_predict_total", 100.0)
                .doesNotContainKey("request_preprocess_total");
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).available()).isTrue();
    }

    @Test
    void shouldNotScrapeTransformer_whenItAdvertisesNoMetricsEndpoint() {
        // Real-world case: the chained transformer has metric aggregation disabled, so KServe
        // stamps no prometheus.kserve.io/port on it (metricsPort == null). Scraping it would only ever
        // time out (while starting) or be reset (once running), so it must be skipped — the snapshot
        // degrades cleanly to predictor-only and the serving block stays available.
        givenDeployment(inferenceDeployment(null));
        givenPods(List.of(podInfo("transformer-pod", "transformer"), podInfo("predictor-pod", "predictor")));
        when(k8sClient.scrapePodMetrics(NAMESPACE, "predictor-pod", DEFAULT_PORT, "/metrics", TIMEOUT_MS))
                .thenReturn(Optional.of(ResourceUtils.readResource("/metrics-fixtures/kserve-modelserver-predictor.txt")));
        when(podResourceUsageReader.readAll(anyString(), any())).thenReturn(List.of());

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        assertThat(snapshot.engine()).isEqualTo(EngineFamily.KSERVE_MODELSERVER);
        assertThat(snapshot.scrapedPod()).isEqualTo("predictor-pod");
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).available()).isTrue();
        // predictor-only: e2e is the predict histogram itself, no pre/post counters from the transformer
        assertThat(snapshot.operational().e2eLatency().mean()).isCloseTo(0.2, within(1e-9));
        assertThat(snapshot.rawCounters())
                .containsEntry("request_predict_total", 100.0)
                .doesNotContainKey("request_preprocess_total");
        // the transformer was never scraped
        verify(k8sClient, never()).scrapePodMetrics(eq(NAMESPACE), eq("transformer-pod"), anyInt(), anyString(), anyLong());
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
        when(podResourceUsageReader.readAll(anyString(), any())).thenReturn(List.of());

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
                .isInstanceOf(MetricsCollectionDisabledException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void shouldReturnResourceOnlySnapshot_forNonInferenceDeployment() {
        givenDeployment(McpDeployment.builder().id(DEPLOYMENT_ID).build());
        givenPods(podInfo(POD_NAME));
        when(podResourceUsageReader.readAll(eq(NAMESPACE), eq(Map.of(POD_NAME, "kserve-container"))))
                .thenReturn(List.of(new PodResourceUsage(POD_NAME, 50.0, 2000.0, null, null)));

        var snapshot = service.getSnapshot(DEPLOYMENT_ID);

        // resource metrics are reported for every deployment type
        assertThat(snapshot.resources().replicasTotal()).isEqualTo(1);
        assertThat(snapshot.resources().replicasReady()).isEqualTo(1);
        assertThat(snapshot.resources().pods()).hasSize(1);
        assertThat(snapshot.availability().get(AVAILABILITY_RESOURCES).available()).isTrue();
        assertThat(snapshot.availability().get(AVAILABILITY_RESOURCES_USAGE).available()).isTrue();

        // serving metrics are skipped entirely — no pod is scraped for a non-inference type
        assertThat(snapshot.engine()).isEqualTo(EngineFamily.UNKNOWN);
        assertThat(snapshot.serving()).isNull();
        assertThat(snapshot.operational()).isNull();
        assertThat(snapshot.scrapedPod()).isNull();
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).available()).isFalse();
        assertThat(snapshot.availability().get(AVAILABILITY_SERVING).reason()).contains("inference");
        verify(k8sClient, never()).scrapePodMetrics(anyString(), anyString(), anyInt(), anyString(), anyLong());
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
        givenPods(List.of(pod));
    }

    private void givenPods(List<PodInfo> pods) {
        when(deploymentManager.getInstancesWithReadiness(DEPLOYMENT_ID))
                .thenReturn(new DeploymentManager.PodInstances(pods, pods));
    }

    private static InferenceDeployment inferenceDeployment(Integer containerPort) {
        return InferenceDeployment.builder()
                .id(DEPLOYMENT_ID)
                .containerPort(containerPort)
                .build();
    }

    private static PodInfo podInfo(String name) {
        // KServe stamps component=predictor on the model-serving pod, so the realistic single-pod case
        // for this KServe-only collector is a labelled predictor.
        return podInfo(name, "predictor");
    }

    private static PodInfo podInfo(String name, String component) {
        // No prometheus.kserve.io/* annotations advertised — metricsPort/metricsPath are null, so the
        // collector falls back to the engine default port for the predictor and skips the transformer.
        return podInfo(name, component, null, null);
    }

    private static PodInfo podInfo(String name, String component, Integer metricsPort, String metricsPath) {
        return new PodInfo(name, component, "kserve-container", Instant.now(), 0, null, null, null, null, null,
                metricsPort, metricsPath);
    }

}
