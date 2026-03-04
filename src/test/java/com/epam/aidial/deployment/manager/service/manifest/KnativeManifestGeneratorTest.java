package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.probe.HttpGetProbe;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.kubernetes.api.model.Container;
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
class KnativeManifestGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    @Mock
    private AppProperties appconfig;
    @Mock
    private ProbeConverter probeConverter;
    @InjectMocks
    private KnativeManifestGenerator manifestGenerator;

    @BeforeEach
    void setupMocks() throws JsonProcessingException {
        var baseServiceJson = ResourceUtils.readResource("/manifest/knative_service_template.json");
        var baseService = objectMapper.readValue(baseServiceJson, Service.class);
        var baseContainerJson = ResourceUtils.readResource("/manifest/knative_service_container_template.json");
        var baseContainer = objectMapper.readValue(baseContainerJson, Container.class);

        when(appconfig.cloneKnativeServiceConfig()).thenReturn(baseService);
        when(appconfig.cloneKnativeServiceContainer()).thenReturn(baseContainer);
        when(appconfig.getKnativeServiceContainerConfig()).thenReturn(baseContainer);
    }

    @Test
    void testServiceConfig_withOverriddenEnvs() throws JsonProcessingException, JSONException {
        // Given
        var deploymentName = "basic-app";
        var imageName = "my-registry/my-image:latest";

        var simpleEnvs = List.of(new SimpleEnvVar("SIMPLE_VAR", new SimpleEnvVarValue("simple_value")));
        var sensitiveEnvs = List.of(new SensitiveEnvVar("SECRET_VAR", null, "my-secret", "secret-key"));
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, simpleEnvs, sensitiveEnvs, Collections.emptyList(), imageName,
                null, null, null, resources, null, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        var expected = ResourceUtils.readResource("/manifest/knative_service_with_envs.json");
        JSONAssert.assertEquals(expected, jsonOutput, true);
    }

    @Test
    void testServiceConfig_withOverriddenScaling() throws JsonProcessingException, JSONException {
        // Given
        var deploymentName = "scaling-app";
        var imageName = "my-registry/scaling-image:v1";

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), imageName,
                0, 0, 10, new Resources(), null, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        var expected = ResourceUtils.readResource("/manifest/knative_service_with_scaling.json");
        JSONAssert.assertEquals(expected, jsonOutput, true);
    }

    @Test
    void testServiceConfig_withOverriddenResources() throws JsonProcessingException, JSONException {
        // Given
        var deploymentName = "resource-app";
        var imageName = "my-registry/resource-image:v1";

        var limits = Map.of("cpu", "2000m", "memory", "8Gi");
        var requests = Map.of("memory", "2Gi");
        var resources = new Resources(limits, requests);

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), imageName,
                null, null, null, resources, null, null
        );

        // Then
        var jsonOutput = serialize(generatedService);
        var expected = ResourceUtils.readResource("/manifest/knative_service_with_resources.json");
        JSONAssert.assertEquals(expected, jsonOutput, true);
    }

    @Test
    public void testGenerateKnativeServiceWithContainerPort() {
        // Given
        var deploymentName = "port-app";
        var imageName = "my-registry/port-image:v1";
        var containerPort = 9000;

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), imageName,
                null, null, null, new Resources(), containerPort, null
        );

        // Then
        assertThat(generatedService.getSpec().getTemplate().getSpec().getContainers())
                .hasSize(1);

        var container = generatedService.getSpec().getTemplate().getSpec().getContainers().getFirst();
        assertThat(container.getPorts())
                .hasSize(1)
                .first()
                .satisfies(port -> assertThat(port.getContainerPort()).isEqualTo(9000));
    }

    @Test
    public void testGenerateKnativeServiceWithoutContainerPortWhenNull() {
        // Given
        var deploymentName = "no-port-app";
        var imageName = "my-registry/no-port-image:v1";

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), imageName,
                null, null, null, new Resources(), null, null
        );

        // Then
        var container = generatedService.getSpec().getTemplate().getSpec().getContainers().getFirst();
        assertThat(container.getPorts()).isNullOrEmpty();
    }

    @Test
    void testServiceConfig_withProbeProperties_setsProgressDeadlineAnnotation() {
        // Given
        var generatorWithRealConverter = new KnativeManifestGenerator(appconfig, new ProbeConverter());
        var deploymentName = "deadline-app";
        var imageName = "my-registry/deadline-image:v1";
        // deadline = 5 + (10 * 2) + 30 = 55
        var httpGet = new HttpGetProbe("/ready", 9090);
        var probeProperties = new ProbeProperties(true, 5, 10, 3, 2, httpGet);

        // When
        var generatedService = generatorWithRealConverter.serviceConfig(
                deploymentName, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), imageName,
                null, null, null, new Resources(), null, probeProperties
        );

        // Then
        var annotations = generatedService.getSpec().getTemplate().getMetadata().getAnnotations();
        assertThat(annotations).containsEntry("serving.knative.dev/progress-deadline", "55s");
    }

    @Test
    void testServiceConfig_withoutProbe_doesNotSetProgressDeadlineAnnotation() {
        // Given
        var deploymentName = "no-deadline-app";
        var imageName = "my-registry/no-deadline-image:v1";

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), imageName,
                null, null, null, new Resources(), null, null
        );

        // Then
        var annotations = generatedService.getSpec().getTemplate().getMetadata().getAnnotations();
        assertThat(annotations).doesNotContainKey("serving.knative.dev/progress-deadline");
    }

    @Test
    void testServiceConfig_withProbeProperties_setsStartupProbeOnContainer() {
        // Given: generator with real ProbeConverter so probe is built from properties
        var generatorWithRealConverter = new KnativeManifestGenerator(appconfig, new ProbeConverter());
        var deploymentName = "probe-app";
        var imageName = "my-registry/probe-image:v1";
        var httpGet = new HttpGetProbe("/ready", 9090);
        var probeProperties = new ProbeProperties(true, 5, 10, 3, 2, httpGet);

        // When
        var generatedService = generatorWithRealConverter.serviceConfig(
                deploymentName, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), imageName,
                null, null, null, new Resources(), null, probeProperties
        );

        // Then: container has startup probe with expected path, port and timing
        var container = generatedService.getSpec().getTemplate().getSpec().getContainers().getFirst();
        var startupProbe = container.getStartupProbe();
        assertThat(startupProbe).isNotNull();
        assertThat(startupProbe.getHttpGet()).isNotNull();
        assertThat(startupProbe.getHttpGet().getPath()).isEqualTo("/ready");
        assertThat(startupProbe.getHttpGet().getPort().getIntVal()).isEqualTo(9090);
        assertThat(startupProbe.getInitialDelaySeconds()).isEqualTo(5);
        assertThat(startupProbe.getPeriodSeconds()).isEqualTo(10);
        assertThat(startupProbe.getTimeoutSeconds()).isEqualTo(3);
        assertThat(startupProbe.getFailureThreshold()).isEqualTo(2);
    }

    private String serialize(Object obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }

}