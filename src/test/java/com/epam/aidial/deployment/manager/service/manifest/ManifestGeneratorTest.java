package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.fabric8.kubernetes.api.model.Secret;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;

import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManifestGeneratorTest {

    private static final String BASE_SECRET_JSON = """
            {
              "apiVersion": "v1",
              "kind": "Secret"
            }
            """;

    @Mock
    private AppProperties appconfig;

    @Mock
    private RegistryService registryService;

    @InjectMocks
    private ManifestGenerator manifestGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @BeforeEach
    void setupMocks() throws JsonProcessingException {
        var baseSecret = objectMapper.readValue(BASE_SECRET_JSON, Secret.class);
        when(appconfig.cloneBuilderSecretConfig()).thenReturn(baseSecret);
    }

    private String serialize(Object obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }

    @Test
    void testSecretConfig_createsSecretWithProvidedData() throws JsonProcessingException, JSONException {
        // Given
        var secretName = "my-custom-secret";
        var secretData = Map.of(
                "API_KEY", "some-super-secret-key",
                "ENDPOINT_URL", "https://example.com/api"
        );
        var secretBinaryData = Map.of(
                "SAMPLE_JSON", "ewogIHRva2VuOiAxMjMKfQ=="
        );

        var expectedJson = """
                {
                  "apiVersion": "v1",
                  "kind": "Secret",
                  "metadata": {
                    "name": "my-custom-secret"
                  },
                  "stringData": {
                    "API_KEY": "some-super-secret-key",
                    "ENDPOINT_URL": "https://example.com/api"
                  },
                  "data": {
                    "SAMPLE_JSON": "ewogIHRva2VuOiAxMjMKfQ=="
                  }
                }
                """;

        // When
        var generatedSecret = manifestGenerator.secretConfig(secretName, secretData, secretBinaryData);

        // Then
        var jsonOutput = serialize(generatedSecret);
        JSONAssert.assertEquals(expectedJson, jsonOutput, true);
    }

    @Test
    void testDialRegistryAuthSecretConfig_withBasicAuth() throws JsonProcessingException, JSONException {
        // Given
        var secretName = "dial-registry-secret";
        var dockerConfigContent = """
                {"auths":{"dial-registry:5000":{"auth":"some_secret_value=="}}}""";

        when(registryService.getAuthScheme()).thenReturn(DockerAuthScheme.BASIC);
        when(registryService.dockerConfig()).thenReturn(dockerConfigContent);

        // The value of "config.json" must be a JSON string, so it needs to be escaped in the final output.
        var expectedJson = """
                {
                  "apiVersion": "v1",
                  "kind": "Secret",
                  "metadata": {
                    "name": "dial-registry-secret"
                  },
                  "stringData": {
                    "config.json": "{\\"auths\\":{\\"dial-registry:5000\\":{\\"auth\\":\\"some_secret_value==\\"}}}"
                  }
                }
                """;

        // When
        var generatedSecret = manifestGenerator.dialRegistryAuthSecretConfig(secretName);

        // Then
        var jsonOutput = serialize(generatedSecret);
        JSONAssert.assertEquals(expectedJson, jsonOutput, true);
    }

    @Test
    void testDialRegistryAuthSecretConfig_withoutBasicAuth() throws JsonProcessingException, JSONException {
        // Given
        var secretName = "dial-registry-secret-no-auth";
        // Mock the registry service to return a non-basic auth scheme
        when(registryService.getAuthScheme()).thenReturn(DockerAuthScheme.NONE);

        var expectedJson = """
                {
                  "apiVersion": "v1",
                  "kind": "Secret",
                  "metadata": {
                    "name": "dial-registry-secret-no-auth"
                  }
                }
                """;

        // When
        var generatedSecret = manifestGenerator.dialRegistryAuthSecretConfig(secretName);

        // Then
        var jsonOutput = serialize(generatedSecret);
        JSONAssert.assertEquals(expectedJson, jsonOutput, true);

        // Crucially, verify that dockerConfig() was NEVER called because the scheme was not BASIC
        verify(registryService).getAuthScheme();
        verify(registryService, never()).dockerConfig();
    }

}