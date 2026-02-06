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
import com.nvidia.apps.v1alpha1.NIMService;
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
class NimManifestGeneratorTest {

    @Mock
    private AppProperties appconfig;
    @Mock
    private NimProbeConverter nimProbeConverter;
    @InjectMocks
    private NimManifestGenerator manifestGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @BeforeEach
    void setupMocks() throws JsonProcessingException {
        var baseServiceJson = ResourceUtils.readResource("/manifest/nim_service_template.json");
        var baseService = objectMapper.readValue(baseServiceJson, NIMService.class);

        when(appconfig.cloneNimServiceConfig()).thenReturn(baseService);
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
                deploymentName, simpleEnvs, sensitiveEnvs, resources, imageName, null, null, null
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
                deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName, null, null, null
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
                deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName, customPort,
                customGrpcPort, null
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
                deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName, null, null, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        
        // Verify the default port from template is preserved (8000)
        var service = objectMapper.readValue(jsonOutput, NIMService.class);
        assertThat(service.getSpec().getExpose().getService().getPort()).isEqualTo(8000);
    }

    private String serialize(Object obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }

}