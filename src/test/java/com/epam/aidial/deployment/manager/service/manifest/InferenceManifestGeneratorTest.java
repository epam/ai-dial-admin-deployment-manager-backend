package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.Scaling;
import com.epam.aidial.deployment.manager.model.ScalingStrategy;
import com.epam.aidial.deployment.manager.model.ScalingStrategyType;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.probe.HttpGetProbe;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.kserve.serving.v1beta1.InferenceService;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferenceManifestGeneratorTest {

    private static final String MODEL_FORMAT = "huggingface";
    private static final String DM_PREFIX = "dm-";
    private static final int STARTUP_TIMEOUT_SEC = 3600;

    @Mock
    private AppProperties appconfig;
    @Mock
    private KserveProbeConverter kserveProbeConverter;
    @Mock
    private ProgressDeadlineCalculator progressDeadlineCalculator;

    private final PoolPrimitivesConverter poolPrimitivesConverter =
            new PoolPrimitivesConverter(JsonMapperConfiguration.createJsonMapper());
    private InferenceManifestGenerator manifestGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @BeforeEach
    void setupMocks() throws JsonProcessingException {
        var baseServiceJson = ResourceUtils.readResource("/manifest/inference_service_template.json");
        var baseService = objectMapper.readValue(baseServiceJson, InferenceService.class);

        when(appconfig.cloneInferenceServiceConfig()).thenReturn(baseService);
        lenient().when(progressDeadlineCalculator.compute(any(), anyInt())).thenReturn("3630s");

        manifestGenerator = new InferenceManifestGenerator(appconfig, kserveProbeConverter,
                progressDeadlineCalculator, poolPrimitivesConverter);
    }

    @Test
    void testServiceConfig_withOverriddenEnvs() throws JsonProcessingException, JSONException {
        // Given
        var deploymentName = "basic-inference-app";
        var storageUri = "s3://my-bucket/my-model";

        var simpleEnvs = List.of(new SimpleEnvVar("SIMPLE_VAR", new SimpleEnvVarValue("simple_value")));
        var sensitiveEnvs = List.of(new SensitiveEnvVar("SECRET_VAR", null, "my-secret", "secret-key"));
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, simpleEnvs, sensitiveEnvs, resources,
                null, null, null, null, null, STARTUP_TIMEOUT_SEC, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        var expected = ResourceUtils.readResource("/manifest/inference_service_with_envs.json");
        JSONAssert.assertEquals(expected, jsonOutput, true);
    }

    @Test
    void testServiceConfig_withOverriddenResources() throws JsonProcessingException, JSONException {
        // Given
        var deploymentName = "resource-inference-app";
        var storageUri = "s3://my-bucket/resource-model";

        var limits = Map.of("cpu", "2000m", "memory", "8Gi");
        var requests = Map.of("memory", "2Gi");
        var resources = new Resources(limits, requests);

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), resources,
                null, null, null, null, null, STARTUP_TIMEOUT_SEC, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        var expected = ResourceUtils.readResource("/manifest/inference_service_with_resources.json");
        JSONAssert.assertEquals(expected, jsonOutput, true);
    }

    @Test
    void testServiceConfig_withOverriddenScaling() throws JsonProcessingException, JSONException {
        // Given
        var deploymentName = "scaling-inference-app";
        var storageUri = "s3://my-bucket/scaling-model";

        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());
        var scalingStrategy = new ScalingStrategy(ScalingStrategyType.ACTIVE_REQUESTS, 10);
        var scaling = new Scaling(1, 5, 600, scalingStrategy);

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), resources,
                scaling, null, null, null, null, STARTUP_TIMEOUT_SEC, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        var expected = ResourceUtils.readResource("/manifest/inference_service_with_scaling.json");
        JSONAssert.assertEquals(expected, jsonOutput, true);
    }

    @Test
    void testServiceConfig_withOverriddenScaling_onlyRequiredParam() throws JsonProcessingException, JSONException {
        // Given
        var deploymentName = "scale-zero-delay-app";
        var storageUri = "s3://my-bucket/model";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());
        var scalingStrategy = new ScalingStrategy(ScalingStrategyType.ACTIVE_REQUESTS, 10);
        var scaling = new Scaling(1, 5, null, scalingStrategy);

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), resources,
                scaling, null, null, null, null, STARTUP_TIMEOUT_SEC, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        var expected = ResourceUtils.readResource("/manifest/inference_service_with_scaling_only_required_params.json");
        JSONAssert.assertEquals(expected, jsonOutput, true);
    }

    @Test
    void testServiceConfig_withInvalidScalingStrategy() {
        // Given
        var deploymentName = "invalid-strategy-app";
        var storageUri = "s3://my-bucket/model";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());
        var scalingStrategy = new ScalingStrategy(ScalingStrategyType.HARDWARE_USAGE, 10);
        var scaling = new Scaling(1, 5, null, scalingStrategy);

        // When/Then
        assertThatThrownBy(() -> manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), resources,
                scaling, null, null, null, null, STARTUP_TIMEOUT_SEC, null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Scaling strategy 'HARDWARE_USAGE' is not supported. Supported strategies: [ACTIVE_REQUESTS]");
    }

    @Test
    void testServiceConfig_withCustomPort() throws JsonProcessingException, JSONException {
        // Given
        var deploymentName = "custom-port-inference-app";
        var storageUri = "s3://my-bucket/port-model";
        var containerPort = 9000;

        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), resources,
                null, null, null, containerPort, null, STARTUP_TIMEOUT_SEC, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        var expected = ResourceUtils.readResource("/manifest/inference_service_with_port.json");
        JSONAssert.assertEquals(expected, jsonOutput, true);
    }

    @Test
    void testServiceConfig_withArgs() throws JsonProcessingException, JSONException {
        // Given
        var deploymentName = "args-inference-app";
        var storageUri = "s3://my-bucket/args-model";
        var args = List.of("--arg1", "value1", "--arg2", "value2");

        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), resources,
                null, null, args, null, null, STARTUP_TIMEOUT_SEC, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        var expected = ResourceUtils.readResource("/manifest/inference_service_with_args.json");
        JSONAssert.assertEquals(expected, jsonOutput, true);
    }

    @Test
    void testServiceConfig_doesNotOverrideExplicitModelNameArg_valueForm() {
        // Given
        var deploymentName = "explicit-model-name-app";
        var storageUri = "s3://my-bucket/model";
        var args = List.of("--arg1", "value1", "--model_name", "my-explicit-name", "--arg2", "value2");

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), new Resources(),
                null, null, args, null, null, STARTUP_TIMEOUT_SEC, null
        );

        // Then
        var actualArgs = generatedService.getSpec().getPredictor().getModel().getArgs();
        assertThat(actualArgs).containsSubsequence("--model_name", "my-explicit-name");
        assertThat(actualArgs).doesNotContain("--model_name=" + deploymentName);
        assertThat(actualArgs).doesNotContainSubsequence("--model_name", deploymentName);
    }

    @Test
    void testServiceConfig_doesNotOverrideExplicitModelNameArg_equalsForm() {
        // Given
        var deploymentName = "explicit-equals-model-name-app";
        var storageUri = "s3://my-bucket/model";
        var args = List.of("--arg1", "value1", "--model_name=my-explicit-name", "--arg2", "value2");

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), new Resources(),
                null, null, args, null, null, STARTUP_TIMEOUT_SEC, null
        );

        // Then
        var actualArgs = generatedService.getSpec().getPredictor().getModel().getArgs();
        assertThat(actualArgs).contains("--model_name=my-explicit-name");
        assertThat(actualArgs).doesNotContain("--model_name=" + deploymentName);
        assertThat(actualArgs).doesNotContainSubsequence("--model_name", deploymentName);
    }

    @Test
    void testServiceConfig_doesNotAddModelNameWhenPresentInCommand() {
        // Given
        var deploymentName = "command-model-name-app";
        var storageUri = "s3://my-bucket/model";
        var command = List.of("--model_name=" + deploymentName, "--serve");

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), new Resources(),
                null, command, Collections.emptyList(), null, null, STARTUP_TIMEOUT_SEC, null
        );

        // Then
        var model = generatedService.getSpec().getPredictor().getModel();
        assertThat(model.getCommand()).containsExactlyElementsOf(command);
        assertThat(model.getArgs()).isEmpty();
    }

    @Test
    void testServiceConfig_withProbeProperties_setsProgressDeadlineAnnotation() {
        // Given
        var realCalculator = new ProgressDeadlineCalculator(0, 10, 3, 30);
        var generatorWithRealConverter = new InferenceManifestGenerator(appconfig, new KserveProbeConverter(new ProbeConverter()),
                realCalculator, poolPrimitivesConverter);
        var deploymentName = "deadline-inference-app";
        var storageUri = "s3://my-bucket/deadline-model";
        // deadline = 5 + ((2-1) * 10) + 3 + 30 = 48
        var httpGet = new HttpGetProbe("/health", 8080);
        var probeProperties = new ProbeProperties(true, 5, 10, 3, 2, httpGet);

        // When
        var generatedService = generatorWithRealConverter.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), new Resources(),
                null, null, null, null, probeProperties, STARTUP_TIMEOUT_SEC, null
        );

        // Then
        var annotations = generatedService.getMetadata().getAnnotations();
        assertThat(annotations).containsEntry("serving.knative.dev/progress-deadline", "48s");
    }

    @Test
    void testServiceConfig_withoutProbe_setsFallbackProgressDeadlineAnnotation() {
        // Given
        var realCalculator = new ProgressDeadlineCalculator(0, 10, 3, 30);
        var generatorWithRealCalculator = new InferenceManifestGenerator(appconfig, new KserveProbeConverter(new ProbeConverter()),
                realCalculator, poolPrimitivesConverter);
        var deploymentName = "fallback-deadline-inference-app";
        var storageUri = "s3://my-bucket/fallback-deadline-model";

        // When
        var generatedService = generatorWithRealCalculator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), new Resources(),
                null, null, null, null, null, STARTUP_TIMEOUT_SEC, null
        );

        // Then: fallback deadline = 3600 + 30 = 3630s
        var annotations = generatedService.getMetadata().getAnnotations();
        assertThat(annotations).containsEntry("serving.knative.dev/progress-deadline", "3630s");
    }

    @Test
    void testServiceConfig_withProbeProperties_setsStartupProbeOnModel() {
        // Given: generator with real KserveProbeConverter so probe is built from properties
        var realCalculator = new ProgressDeadlineCalculator(0, 10, 3, 30);
        var generatorWithRealConverter = new InferenceManifestGenerator(appconfig, new KserveProbeConverter(new ProbeConverter()),
                realCalculator, poolPrimitivesConverter);
        var deploymentName = "probe-inference-app";
        var storageUri = "s3://my-bucket/probe-model";
        var httpGet = new HttpGetProbe("/health", 8080);
        var probeProperties = new ProbeProperties(true, 5, 10, 3, 2, httpGet);

        // When
        var generatedService = generatorWithRealConverter.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), new Resources(),
                null, null, null, null, probeProperties, STARTUP_TIMEOUT_SEC, null
        );

        // Then: predictor model has startup probe with expected path, port and timing
        var model = generatedService.getSpec().getPredictor().getModel();
        var startupProbe = model.getStartupProbe();
        assertThat(startupProbe).isNotNull();
        assertThat(startupProbe.getHttpGet()).isNotNull();
        assertThat(startupProbe.getHttpGet().getPath()).isEqualTo("/health");
        assertThat(startupProbe.getHttpGet().getPort().getIntVal()).isEqualTo(8080);
        assertThat(startupProbe.getInitialDelaySeconds()).isEqualTo(5);
        assertThat(startupProbe.getPeriodSeconds()).isEqualTo(10);
        assertThat(startupProbe.getTimeoutSeconds()).isEqualTo(3);
        assertThat(startupProbe.getFailureThreshold()).isEqualTo(2);
    }

    @Test
    void testServiceConfig_projectsPoolPrimitivesOntoPredictor() {
        var deploymentName = "node-pool-inference-app";
        var storageUri = "s3://my-bucket/node-pool-model";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());
        var affinity = new io.fabric8.kubernetes.api.model.AffinityBuilder()
                .withNewNodeAffinity()
                .withNewRequiredDuringSchedulingIgnoredDuringExecution()
                .addNewNodeSelectorTerm()
                .addNewMatchExpression()
                .withKey("accelerator-type").withOperator("In").addToValues("nvidia-a100")
                .endMatchExpression()
                .endNodeSelectorTerm()
                .endRequiredDuringSchedulingIgnoredDuringExecution()
                .endNodeAffinity()
                .build();
        var toleration = new io.fabric8.kubernetes.api.model.TolerationBuilder()
                .withKey("dedicated").withOperator("Equal").withValue("gpu").withEffect("NoSchedule")
                .build();
        var primitives = new PoolSchedulingPrimitives(Map.of("workload", "gpu"), affinity, java.util.List.of(toleration));

        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), resources,
                null, null, null, null, null, STARTUP_TIMEOUT_SEC, primitives
        );

        var predictor = generatedService.getSpec().getPredictor();
        assertThat(predictor.getNodeSelector()).containsEntry("workload", "gpu");
        assertThat(predictor.getAffinity()).isNotNull();
        assertThat(predictor.getTolerations()).hasSize(1);
        assertThat(predictor.getTolerations().get(0).getKey()).isEqualTo("dedicated");
    }

    @Test
    void testServiceConfig_withEmptyPrimitives_doesNotSetSchedulingFields() {
        var deploymentName = "no-pool-inference-app";
        var storageUri = "s3://my-bucket/no-pool-model";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), resources,
                null, null, null, null, null, STARTUP_TIMEOUT_SEC, PoolSchedulingPrimitives.EMPTY
        );

        var predictor = generatedService.getSpec().getPredictor();
        assertThat(predictor.getNodeSelector()).isNullOrEmpty();
        assertThat(predictor.getAffinity()).isNull();
        assertThat(predictor.getTolerations()).isNullOrEmpty();
    }

    private String serialize(Object obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }

}

