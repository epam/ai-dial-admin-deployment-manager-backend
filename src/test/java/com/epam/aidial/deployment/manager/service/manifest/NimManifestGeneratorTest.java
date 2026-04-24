package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.NimDeployProperties;
import com.epam.aidial.deployment.manager.kubernetes.knative.KnativeAnnotations;
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
import com.nvidia.apps.v1alpha1.NIMService;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.Ingress;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.ingress.Spec;
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
class NimManifestGeneratorTest {

    private static final String DM_PREFIX = "dm-";
    private static final int STARTUP_TIMEOUT_SEC = 3600;

    @Mock
    private AppProperties appconfig;
    @Mock
    private NimProbeConverter nimProbeConverter;
    @Mock
    private ProgressDeadlineCalculator progressDeadlineCalculator;

    private NimDeployProperties nimDeployProperties;
    private NimManifestGenerator manifestGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @BeforeEach
    void setupMocks() throws JsonProcessingException {
        var baseServiceJson = ResourceUtils.readResource("/manifest/nim_service_template.json");
        var baseService = objectMapper.readValue(baseServiceJson, NIMService.class);

        lenient().when(appconfig.cloneNimServiceConfig()).thenReturn(baseService);
        lenient().when(progressDeadlineCalculator.compute(any(), anyInt())).thenReturn("3630s");

        nimDeployProperties = new NimDeployProperties();
        nimDeployProperties.setUseClusterInternalUrl(true);
        nimDeployProperties.setKserveModeEnabled(false);

        manifestGenerator = new NimManifestGenerator(appconfig, nimProbeConverter, progressDeadlineCalculator, nimDeployProperties);
    }

    @Test
    void testServiceConfig_withOverriddenEnvs() throws JsonProcessingException, JSONException {
        // Given
        var deploymentName = "basic-nim-app";
        var imageName = "my-registry.io/custom/my-model:v1.2.3";

        var simpleEnvs = List.of(new SimpleEnvVar("SIMPLE_VAR", new SimpleEnvVarValue("simple_value")));
        var sensitiveEnvs = List.of(new SensitiveEnvVar("SECRET_VAR", null, "my-secret", "secret-key"));
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, simpleEnvs, sensitiveEnvs, resources, imageName, 8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        var expected = ResourceUtils.readResource("/manifest/nim_service_with_envs.json");
        JSONAssert.assertEquals(expected, jsonOutput, true);
    }

    @Test
    void testServiceConfig_withOverriddenResources() throws JsonProcessingException, JSONException {
        // Given
        var deploymentName = "resource-nim-app";
        var imageName = "my-registry.io/models/resource-model:prod";

        var limits = Map.of("nvidia.com/gpu", "2", "memory", "8Gi");
        var requests = Map.of("cpu", "1", "memory", "4Gi");
        var resources = new Resources(limits, requests);

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName, 8000, null, null, null, null,
                STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        var expected = ResourceUtils.readResource("/manifest/nim_service_with_resources.json");
        JSONAssert.assertEquals(expected, jsonOutput, true);
    }

    @Test
    void testServiceConfig_withCustomPort() throws JsonProcessingException {
        // Given
        var deploymentName = "custom-port-nim-app";
        var imageName = "my-registry.io/custom/my-model:v1.2.3";
        var customPort = 9000;
        var customGrpcPort = 8888;

        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName, customPort,
                customGrpcPort, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        var jsonOutput = serialize(generatedService);

        // Verify the port is set correctly
        var service = objectMapper.readValue(jsonOutput, NIMService.class);
        assertThat(service.getSpec().getExpose().getService().getPort()).isEqualTo(customPort);
        assertThat(service.getSpec().getExpose().getService().getGrpcPort()).isEqualTo(customGrpcPort);
    }

    @Test
    void testServiceConfig_withNullPort_usesDefaultFromTemplate() throws JsonProcessingException {
        // Given
        var deploymentName = "default-port-nim-app";
        var imageName = "my-registry.io/custom/my-model:v1.2.3";

        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName, 8000, null, null, null, null,
                STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        var jsonOutput = serialize(generatedService);

        // Verify the default port from template is preserved (8000)
        var service = objectMapper.readValue(jsonOutput, NIMService.class);
        assertThat(service.getSpec().getExpose().getService().getPort()).isEqualTo(8000);
    }

    @Test
    void testServiceConfig_withProbeProperties_setsStartupProbeOnSpec() {
        // Given: generator with real NimProbeConverter so probe is built from properties
        var realCalculator = new ProgressDeadlineCalculator(0, 10, 3, 30);
        var generatorWithRealConverter = new NimManifestGenerator(appconfig, new NimProbeConverter(new ProbeConverter()),
                realCalculator, nimDeployProperties);
        var deploymentName = "probe-nim-app";
        var imageName = "my-registry.io/probe-image:v1";
        var httpGet = new HttpGetProbe("/ready", 9090);
        var probeProperties = new ProbeProperties(true, 5, 10, 3, 2, httpGet);

        // When
        var generatedService = generatorWithRealConverter.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), new Resources(), imageName,
                8000, null, null, null, probeProperties, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then: spec has startup probe with expected enabled, path, port and timing
        var startupProbe = generatedService.getSpec().getStartupProbe();
        assertThat(startupProbe).isNotNull();
        assertThat(startupProbe.getEnabled()).isTrue();
        assertThat(startupProbe.getProbe()).isNotNull();
        assertThat(startupProbe.getProbe().getHttpGet()).isNotNull();
        assertThat(startupProbe.getProbe().getHttpGet().getPath()).isEqualTo("/ready");
        assertThat(startupProbe.getProbe().getHttpGet().getPort().getIntVal()).isEqualTo(9090);
        assertThat(startupProbe.getProbe().getInitialDelaySeconds()).isEqualTo(5);
        assertThat(startupProbe.getProbe().getPeriodSeconds()).isEqualTo(10);
        assertThat(startupProbe.getProbe().getTimeoutSeconds()).isEqualTo(3);
        assertThat(startupProbe.getProbe().getFailureThreshold()).isEqualTo(2);
    }

    @Test
    void testServiceConfig_withCommandAndArgs_setsCommandAndArgsOnSpec() {
        // Given
        var deploymentName = "cmd-args-nim-app";
        var imageName = "my-registry.io/custom/my-model:v1";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());
        var command = List.of("/bin/sh", "-c");
        var args = List.of("echo hello");

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, command, args, null
        );

        // Then
        assertThat(generatedService.getSpec().getCommand()).containsExactly("/bin/sh", "-c");
        assertThat(generatedService.getSpec().getArgs()).containsExactly("echo hello");
    }

    @Test
    void testServiceConfig_withCommandOnly_setsCommandOnSpec() {
        // Given
        var deploymentName = "cmd-only-nim-app";
        var imageName = "my-registry.io/custom/my-model:v1";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());
        var command = List.of("python3");

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, command, null, null
        );

        // Then
        assertThat(generatedService.getSpec().getCommand()).containsExactly("python3");
        assertThat(generatedService.getSpec().getArgs()).isNull();
    }

    @Test
    void testServiceConfig_withNullCommandAndArgs_doesNotSetCommandOrArgs() {
        // Given
        var deploymentName = "no-cmd-nim-app";
        var imageName = "my-registry.io/custom/my-model:v1";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        assertThat(generatedService.getSpec().getCommand()).isNull();
        assertThat(generatedService.getSpec().getArgs()).isNull();
    }

    @Test
    void shouldNotOverrideServedModelName_whenProvidedAsSimpleEnvVar() {
        // Given
        var deploymentName = "nim-with-custom-model";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var customModelName = "my-custom-model";
        var simpleEnvs = List.of(new SimpleEnvVar("NIM_SERVED_MODEL_NAME", new SimpleEnvVarValue(customModelName)));
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, simpleEnvs, Collections.emptyList(), resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        var envList = generatedService.getSpec().getEnv();
        assertThat(envList)
                .filteredOn(env -> "NIM_SERVED_MODEL_NAME".equals(env.getName()))
                .hasSize(1)
                .first()
                .satisfies(env -> assertThat(env.getValue()).isEqualTo(customModelName));
    }

    @Test
    void shouldNotOverrideServedModelName_whenProvidedAsSensitiveEnvVar() {
        // Given
        var deploymentName = "nim-with-secret-model";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var sensitiveEnvs = List.of(new SensitiveEnvVar("NIM_SERVED_MODEL_NAME", null, "model-secret", "model-key"));
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), sensitiveEnvs, resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then — should have the sensitive env var (with valueFrom), not a simple value override
        var envList = generatedService.getSpec().getEnv();
        assertThat(envList)
                .filteredOn(env -> "NIM_SERVED_MODEL_NAME".equals(env.getName()))
                .hasSize(1)
                .first()
                .satisfies(env -> {
                    assertThat(env.getValue()).isNull();
                    assertThat(env.getValueFrom()).isNotNull();
                    assertThat(env.getValueFrom().getSecretKeyRef().getName()).isEqualTo("model-secret");
                    assertThat(env.getValueFrom().getSecretKeyRef().getKey()).isEqualTo("model-key");
                });
    }

    @Test
    void shouldSetServedModelName_whenNotProvidedByUser() {
        // Given
        var deploymentName = "my-nim-deployment";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        var envList = generatedService.getSpec().getEnv();
        assertThat(envList)
                .filteredOn(env -> "NIM_SERVED_MODEL_NAME".equals(env.getName()))
                .hasSize(1)
                .first()
                .satisfies(env -> assertThat(env.getValue()).isEqualTo(deploymentName));
    }

    @Test
    void testServiceConfig_withoutProbe_setsFallbackProgressDeadlineAnnotation() {
        // Given: generator with real calculator so fallback deadline is computed
        var realCalculator = new ProgressDeadlineCalculator(0, 10, 3, 30);
        var generatorWithRealCalculator = new NimManifestGenerator(appconfig, new NimProbeConverter(new ProbeConverter()),
                realCalculator, nimDeployProperties);
        var deploymentName = "fallback-deadline-nim-app";
        var imageName = "my-registry.io/probe-image:v1";

        // When: no probe properties provided
        var generatedService = generatorWithRealCalculator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), new Resources(), imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then: fallback deadline = 3600 + 30 = 3630s
        var annotations = generatedService.getMetadata().getAnnotations();
        assertThat(annotations).containsEntry("serving.knative.dev/progress-deadline", "3630s");
    }

    @Test
    void shouldSetAutoscalingAnnotations_whenKserveModeAndScalingWithStrategyProvided() {
        // Given
        nimDeployProperties.setKserveModeEnabled(true);
        var deploymentName = "scaled-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());
        var strategy = new ScalingStrategy(ScalingStrategyType.ACTIVE_REQUESTS, 10);
        var scaling = new Scaling(2, 5, null, strategy);

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, scaling, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then: min/max/initial-scale from Scaling + class/metric/target from strategy
        var annotations = generatedService.getMetadata().getAnnotations();
        assertThat(annotations)
                .containsEntry(KnativeAnnotations.AUTOSCALING_CLASS, KnativeAnnotations.AUTOSCALING_CLASS_KPA)
                .containsEntry(KnativeAnnotations.AUTOSCALING_METRIC, KnativeAnnotations.AUTOSCALING_METRIC_CONCURRENCY)
                .containsEntry(KnativeAnnotations.AUTOSCALING_TARGET, "10")
                .containsEntry(KnativeAnnotations.MIN_SCALE, "2")
                .containsEntry(KnativeAnnotations.MAX_SCALE, "5")
                .containsEntry(KnativeAnnotations.INITIAL_SCALE, "2");
    }

    @Test
    void shouldSetScalingAnnotationsWithoutTarget_whenKserveModeAndNoStrategy() {
        // Given
        nimDeployProperties.setKserveModeEnabled(true);
        var deploymentName = "no-strategy-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());
        var scaling = new Scaling(1, 3, null, null);

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, scaling, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then: min/max/initial-scale set, but no target annotation
        var annotations = generatedService.getMetadata().getAnnotations();
        assertThat(annotations)
                .containsEntry(KnativeAnnotations.MIN_SCALE, "1")
                .containsEntry(KnativeAnnotations.MAX_SCALE, "3")
                .containsEntry(KnativeAnnotations.INITIAL_SCALE, "1")
                .doesNotContainKey(KnativeAnnotations.AUTOSCALING_TARGET);
    }

    @Test
    void shouldSetInitialScaleToOne_whenKserveModeAndMinReplicasIsZero() {
        // Given
        nimDeployProperties.setKserveModeEnabled(true);
        var deploymentName = "scale-to-zero-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());
        var scaling = new Scaling(0, 3, 60, null);

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, scaling, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then: initial-scale should be 1 (Math.max(0, 1)) even though min-scale is 0
        var annotations = generatedService.getMetadata().getAnnotations();
        assertThat(annotations)
                .containsEntry(KnativeAnnotations.MIN_SCALE, "0")
                .containsEntry(KnativeAnnotations.MAX_SCALE, "3")
                .containsEntry(KnativeAnnotations.INITIAL_SCALE, "1")
                .containsEntry(KnativeAnnotations.SCALE_TO_ZERO_RETENTION, "60s");
    }

    @Test
    void shouldNotSetScalingAnnotations_inLegacyModeEvenWhenScalingProvided() {
        // Given: legacy mode (kserveModeEnabled=false), scaling provided
        var deploymentName = "legacy-scale-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());
        var scaling = new Scaling(2, 5, null, new ScalingStrategy(ScalingStrategyType.ACTIVE_REQUESTS, 10));

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, scaling, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then: no knative autoscaling annotations are set in legacy mode
        var annotations = generatedService.getMetadata().getAnnotations();
        assertThat(annotations)
                .doesNotContainKey(KnativeAnnotations.MIN_SCALE)
                .doesNotContainKey(KnativeAnnotations.MAX_SCALE)
                .doesNotContainKey(KnativeAnnotations.INITIAL_SCALE)
                .doesNotContainKey(KnativeAnnotations.AUTOSCALING_CLASS)
                .doesNotContainKey(KnativeAnnotations.AUTOSCALING_METRIC)
                .doesNotContainKey(KnativeAnnotations.AUTOSCALING_TARGET);
    }

    @Test
    void shouldSetDefaultScalingAnnotations_inKserveModeWhenScalingIsNull() {
        // Given: kserve mode without a Scaling object — generator should emit fixed
        // 1/1/1 defaults (initial/min/max) so behavior is deterministic and does not
        // depend on Knative's cluster-level configuration.
        nimDeployProperties.setKserveModeEnabled(true);
        var deploymentName = "kserve-no-scale-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        var annotations = generatedService.getMetadata().getAnnotations();
        assertThat(annotations)
                .containsEntry(KnativeAnnotations.INITIAL_SCALE, "1")
                .containsEntry(KnativeAnnotations.MIN_SCALE, "1")
                .containsEntry(KnativeAnnotations.MAX_SCALE, "1")
                .doesNotContainKey(KnativeAnnotations.AUTOSCALING_CLASS)
                .doesNotContainKey(KnativeAnnotations.AUTOSCALING_METRIC)
                .doesNotContainKey(KnativeAnnotations.AUTOSCALING_TARGET);
    }

    @Test
    void shouldSetExposeRouter_inKserveMode() {
        // Given
        nimDeployProperties.setKserveModeEnabled(true);
        var deploymentName = "router-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then: expose.router is set, expose.ingress is not set
        var expose = generatedService.getSpec().getExpose();
        assertThat(expose.getRouter()).isNotNull();
        assertThat(expose.getIngress()).isNull();
        assertThat(expose.getService()).isNotNull();
    }

    @Test
    void shouldSetInferencePlatformToKserve_inKserveMode() {
        // Given
        nimDeployProperties.setKserveModeEnabled(true);
        var deploymentName = "kserve-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        assertThat(generatedService.getSpec().getInferencePlatform().name()).isEqualTo("KSERVE");
    }

    @Test
    void shouldLeaveInferencePlatformDefault_inLegacyMode() {
        // Given: legacy mode (default)
        var deploymentName = "standalone-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then: inferencePlatform remains the CRD default (standalone)
        assertThat(generatedService.getSpec().getInferencePlatform().name()).isEqualTo("STANDALONE");
        assertThat(generatedService.getSpec().getExpose().getRouter()).isNull();
    }

    @Test
    void shouldSetIngress_inLegacyModeWithExternalUrl() {
        // Given: legacy mode with external URL enabled
        nimDeployProperties.setUseClusterInternalUrl(false);
        nimDeployProperties.setClusterHost("example.com");
        stubNimServiceExposeIngressConfig();

        var deploymentName = "ingress-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        var expose = generatedService.getSpec().getExpose();
        assertThat(expose.getIngress()).isNotNull();
        assertThat(expose.getIngress().getSpec().getRules()).hasSize(1);
        assertThat(expose.getIngress().getSpec().getRules().getFirst().getHost()).isEqualTo(DM_PREFIX + deploymentName + ".example.com");
        assertThat(expose.getIngress().getSpec().getTls()).hasSize(1);
        assertThat(expose.getRouter()).isNull();
    }

    @Test
    void shouldThrow_inLegacyModeWithExternalUrlAndMissingClusterHost() {
        // Given
        nimDeployProperties.setUseClusterInternalUrl(false);
        nimDeployProperties.setClusterHost(null);

        var deploymentName = "missing-host-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When / Then
        assertThatThrownBy(() -> manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cluster host is not configured");
    }

    @Test
    void shouldNotRequireClusterHost_inKserveModeEvenWithExternalUrl() {
        // Given: kserve mode ignores cluster host requirement since Knative handles routing
        nimDeployProperties.setKserveModeEnabled(true);
        nimDeployProperties.setUseClusterInternalUrl(false);
        nimDeployProperties.setClusterHost(null);

        var deploymentName = "kserve-external-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        var expose = generatedService.getSpec().getExpose();
        assertThat(expose.getRouter()).isNotNull();
        assertThat(expose.getIngress()).isNull();
    }

    @Test
    void testServiceConfig_kserveMode_matchesExpectedManifest() throws JsonProcessingException, JSONException {
        // Given
        nimDeployProperties.setKserveModeEnabled(true);
        var deploymentName = "kserve-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());
        var scaling = new Scaling(2, 5, null, new ScalingStrategy(ScalingStrategyType.ACTIVE_REQUESTS, 10));

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, scaling, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        var expected = ResourceUtils.readResource("/manifest/nim_service_kserve_mode.json");
        JSONAssert.assertEquals(expected, jsonOutput, true);
    }

    @Test
    void testServiceConfig_legacyModeWithExternalUrl_matchesExpectedManifest() throws JsonProcessingException, JSONException {
        // Given: legacy mode with external URL (ingress-based exposure)
        nimDeployProperties.setUseClusterInternalUrl(false);
        nimDeployProperties.setClusterHost("example.com");
        stubNimServiceExposeIngressConfig();

        var deploymentName = "legacy-external-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        var expected = ResourceUtils.readResource("/manifest/nim_service_legacy_external_url.json");
        JSONAssert.assertEquals(expected, jsonOutput, true);
    }

    private void stubNimServiceExposeIngressConfig() {
        var ingress = new Ingress();
        ingress.setEnabled(true);
        ingress.setAnnotations(Map.of(
                "nginx.ingress.kubernetes.io/proxy-body-size", "0",
                "nginx.ingress.kubernetes.io/proxy-read-timeout", "600",
                "cert-manager.io/cluster-issuer", "letsencrypt-production",
                "nginx.ingress.kubernetes.io/force-ssl-redirect", "true"
        ));
        var ingressSpec = new Spec();
        ingressSpec.setIngressClassName("nginx");
        ingress.setSpec(ingressSpec);
        when(appconfig.cloneNimServiceExposeIngressConfig()).thenReturn(ingress);
    }

    @Test
    void shouldNotSetIngress_inLegacyModeWithClusterInternalUrl() {
        // Given: legacy mode with cluster internal URL (default)
        var deploymentName = "cluster-internal-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        var expose = generatedService.getSpec().getExpose();
        assertThat(expose.getIngress()).isNull();
        assertThat(expose.getRouter()).isNull();
    }

    @Test
    void testServiceConfig_withStorageSize_overridesTemplateDefault() {
        // Given
        var deploymentName = "storage-nim-app";
        var imageName = "my-registry.io/custom/my-model:v1.2.3";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(),
                resources, imageName, 8000, null, "50Gi", null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        assertThat(generatedService.getSpec().getStorage().getPvc().getSize()).isEqualTo("50Gi");
    }

    @Test
    void testServiceConfig_withNullStorageSize_preservesTemplateDefault() {
        // Given
        var deploymentName = "default-storage-nim-app";
        var imageName = "my-registry.io/custom/my-model:v1.2.3";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(),
                resources, imageName, 8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        assertThat(generatedService.getSpec().getStorage().getPvc().getSize()).isEqualTo("20Gi");
    }

    @Test
    void testServiceConfig_withNodePoolLabels_setsNodeSelector() {
        // Given
        var deploymentName = "node-pool-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());
        var nodePoolLabels = Map.of("node-pool-key", "gpu-pool");

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(),
                resources, imageName, 8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, nodePoolLabels
        );

        // Then
        assertThat(generatedService.getSpec().getNodeSelector())
                .isNotNull()
                .containsEntry("node-pool-key", "gpu-pool")
                .hasSize(1);
    }

    @Test
    void testServiceConfig_withNullNodePoolLabels_doesNotSetNodeSelector() {
        // Given
        var deploymentName = "no-pool-nim-app";
        var imageName = "nvcr.io/nim/meta/llama-3.1-8b-instruct:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(),
                resources, imageName, 8000, null, null, null, null, STARTUP_TIMEOUT_SEC, null, null, null
        );

        // Then
        assertThat(generatedService.getSpec().getNodeSelector()).isNull();
    }

    private String serialize(Object obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }

}
