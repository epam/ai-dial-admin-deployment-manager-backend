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
import com.nvidia.apps.v1alpha1.NIMService;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.Ingress;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.ingress.Spec;
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

    private static final String DM_PREFIX = "dm-";

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
        when(appconfig.getNimServiceExposeIngressConfig()).thenReturn(ingress);
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
                deploymentName, DM_PREFIX + deploymentName, simpleEnvs, sensitiveEnvs, resources, imageName, 8000, null, null, false, null, null, null
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
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName, 8000, null, null,
                false, null, null, null
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
                customGrpcPort, null, false, null, null, null
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
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName, 8000, null, null,
                false, null, null, null
        );

        // Then
        var jsonOutput = serialize(generatedService);

        // Verify the default port from template is preserved (8000)
        var service = objectMapper.readValue(jsonOutput, NIMService.class);
        assertThat(service.getSpec().getExpose().getService().getPort()).isEqualTo(8000);
    }

    @Test
    void testServiceConfig_withExternalUrlAndClusterHost_setsExposeIngress() {
        stubNimServiceExposeIngressConfig();

        // Given: external URL with cluster host
        var deploymentName = "external-nim-app";
        var imageName = "nvcr.io/nim/my-model:1.0";
        var clusterHost = "ext-aks.example.com";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());
        var httpPort = 8000;

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                httpPort, null, null, true, clusterHost, null, null
        );

        // Then: expose.ingress is set with host, tls secret, backend
        var expose = generatedService.getSpec().getExpose();
        assertThat(expose).isNotNull();
        var ingress = expose.getIngress();
        assertThat(ingress).isNotNull();
        assertThat(ingress.getEnabled()).isTrue();
        var spec = ingress.getSpec();
        assertThat(spec).isNotNull();
        var nimServiceName = DM_PREFIX + deploymentName;
        assertThat(spec.getIngressClassName()).isEqualTo("nginx");
        assertThat(spec.getTls()).hasSize(1);
        assertThat(spec.getTls().getFirst().getHosts()).containsExactly(nimServiceName + "." + clusterHost);
        assertThat(spec.getTls().getFirst().getSecretName()).isEqualTo(nimServiceName + "-tls-secret");
        assertThat(spec.getRules()).hasSize(1);
        assertThat(spec.getRules().getFirst().getHost()).isEqualTo(nimServiceName + "." + clusterHost);
        var backendService = spec.getRules().getFirst().getHttp().getPaths().getFirst().getBackend().getService();
        assertThat(backendService.getName()).isEqualTo(nimServiceName);
        assertThat(backendService.getPort().getNumber()).isEqualTo(httpPort);
    }

    @Test
    void testServiceConfig_withExternalUrl_setsIngressAnnotationsFromConfig() {
        stubNimServiceExposeIngressConfig();

        // Given: external URL with cluster host
        var deploymentName = "annotated-nim-app";
        var imageName = "nvcr.io/nim/my-model:1.0";
        var clusterHost = "ext.example.com";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, true, clusterHost, null, null
        );

        // Then: expose.ingress has annotations from nim-service-expose-ingress-config (proxy-body-size, proxy-read-timeout, cert-manager, force-ssl-redirect)
        var ingress = generatedService.getSpec().getExpose().getIngress();
        assertThat(ingress).isNotNull();
        assertThat(ingress.getAnnotations())
                .containsEntry("nginx.ingress.kubernetes.io/proxy-body-size", "0")
                .containsEntry("nginx.ingress.kubernetes.io/proxy-read-timeout", "600")
                .containsEntry("cert-manager.io/cluster-issuer", "letsencrypt-production")
                .containsEntry("nginx.ingress.kubernetes.io/force-ssl-redirect", "true");
    }

    @Test
    void testServiceConfig_withUseExternalUrlFalse_doesNotSetExposeIngress() throws JsonProcessingException {
        // Given: internal URL only
        var deploymentName = "internal-nim-app";
        var imageName = "nvcr.io/nim/my-model:1.0";
        var resources = new Resources(Collections.emptyMap(), Collections.emptyMap());

        // When
        var generatedService = manifestGenerator.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), resources, imageName,
                8000, null, null, false, null, null, null
        );

        // Then: expose.ingress is not set
        var expose = generatedService.getSpec().getExpose();
        assertThat(expose).isNotNull();
        assertThat(expose.getIngress()).isNull();
    }

    @Test
    void testServiceConfig_withProbeProperties_setsStartupProbeOnSpec() {
        // Given: generator with real NimProbeConverter so probe is built from properties
        var generatorWithRealConverter = new NimManifestGenerator(appconfig, new NimProbeConverter(new ProbeConverter()));
        var deploymentName = "probe-nim-app";
        var imageName = "my-registry.io/probe-image:v1";
        var httpGet = new HttpGetProbe("/ready", 9090);
        var probeProperties = new ProbeProperties(true, 5, 10, 3, 2, httpGet);

        // When
        var generatedService = generatorWithRealConverter.serviceConfig(
                deploymentName, DM_PREFIX + deploymentName, Collections.emptyList(), Collections.emptyList(), new Resources(), imageName,
                8000, null, probeProperties, false, null, null, null
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
                8000, null, null, false, null, command, args
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
                8000, null, null, false, null, command, null
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
                8000, null, null, false, null, null, null
        );

        // Then
        assertThat(generatedService.getSpec().getCommand()).isNull();
        assertThat(generatedService.getSpec().getArgs()).isNull();
    }

    private String serialize(Object obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }

}