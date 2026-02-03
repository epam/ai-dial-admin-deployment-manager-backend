package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.kserve.serving.v1beta1.InferenceService;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferenceManifestGeneratorTest {

    private static final String MODEL_FORMAT = "huggingface";

    @Mock
    private AppProperties appconfig;
    @Mock
    private ProbeConverter probeConverter;
    @InjectMocks
    private InferenceManifestGenerator manifestGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @BeforeEach
    void setupMocks() throws JsonProcessingException {
        var baseServiceJson = ResourceUtils.readResource("/manifest/inference_service_template.json");
        var baseService = objectMapper.readValue(baseServiceJson, InferenceService.class);

        when(appconfig.cloneInferenceServiceConfig()).thenReturn(baseService);
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
                deploymentName, MODEL_FORMAT, storageUri, simpleEnvs, sensitiveEnvs, resources,
                null, null, null, null, null, null
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
                deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), resources,
                null, null, null, null, null, null
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
        var minScale = 1;
        var maxScale = 5;

        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), resources,
                minScale, maxScale, null, null, null, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        var expected = ResourceUtils.readResource("/manifest/inference_service_with_scaling.json");
        JSONAssert.assertEquals(expected, jsonOutput, true);
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
                deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), resources,
                null, null, null, null, containerPort, null
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
                deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), resources,
                null, null, null, args, null, null
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
                deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), new Resources(),
                null, null, null, args, null, null
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
                deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), new Resources(),
                null, null, null, args, null, null
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
                deploymentName, MODEL_FORMAT, storageUri, Collections.emptyList(), Collections.emptyList(), new Resources(),
                null, null, command, Collections.emptyList(), null, null
        );

        // Then
        var model = generatedService.getSpec().getPredictor().getModel();
        assertThat(model.getCommand()).containsExactlyElementsOf(command);
        assertThat(model.getArgs()).isEmpty();
    }

    private String serialize(Object obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }

}

