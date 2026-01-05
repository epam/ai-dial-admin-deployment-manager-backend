package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.docker.DockerRegistryClient;
import com.epam.aidial.deployment.manager.model.DistroInfo;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.ImageEntrypoint;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpecBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageWrapperBuildJobSpecificationTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String BUILD_ID = "test-build-id";
    private static final String IMAGE_NAME = BUILD_ID + ":1.0.0";
    private static final String DOCKER_CONFIG_PATH = "/config";
    private static final String SOURCE_IMAGE_URI = "test-build-id:1.0.0";
    private static final String MCP_PROXY_ALPINE_IMAGE_URI = "registry.example.com/mcp-proxy-alpine:latest";
    private static final String MCP_PROXY_DEBIAN_IMAGE_URI = "registry.example.com/mcp-proxy-debian:latest";
    private static final String DISTRO_ID = "alpine";
    private static final String DISTRO_VERSION = "1.0";

    @Mock
    private DockerRegistryClient registryClient;
    @Mock
    private RegistryService registryService;
    @Mock
    private ManifestGenerator manifestGenerator;
    @Mock
    private AppProperties appConfig;

    private DockerImageSource dockerImageSource;
    private DistroInfo distroInfo;
    private ImageWrapperBuildJobSpecification jobSpecification;

    @BeforeEach
    void setUp() {
        dockerImageSource = new DockerImageSource();
        dockerImageSource.setImageUri(SOURCE_IMAGE_URI);

        distroInfo = new DistroInfo(DISTRO_ID, DISTRO_VERSION);

        jobSpecification = new ImageWrapperBuildJobSpecification(
                registryClient,
                registryService,
                manifestGenerator,
                appConfig,
                NAMESPACE,
                DOCKER_CONFIG_PATH,
                BUILD_ID,
                IMAGE_NAME,
                dockerImageSource,
                distroInfo,
                MCP_PROXY_ALPINE_IMAGE_URI,
                MCP_PROXY_DEBIAN_IMAGE_URI
        );
    }

    @Test
    void getJobId_shouldReturnBuildId() {
        // When
        String jobId = jobSpecification.getJobId();

        // Then
        assertEquals(BUILD_ID, jobId);
    }

    @Test
    void getNamespace_shouldReturnNamespace() {
        // When
        String namespace = jobSpecification.getNamespace();

        // Then
        assertEquals(NAMESPACE, namespace);
    }

    @Test
    void getConfigMaps_shouldReturnDockerfileConfigMap() {
        // Given
        String expectedConfigMapName = K8sNamingUtils.generateName("dockerfile", BUILD_ID);
        ConfigMap expectedConfigMap = createDockerfileConfigMap(expectedConfigMapName, List.of());

        var entrypoint = new ImageEntrypoint(List.of("/bin/sh"), List.of("-c", "echo Hello World"));
        when(registryClient.getEntrypoint(SOURCE_IMAGE_URI)).thenReturn(entrypoint);

        // When
        List<ConfigMap> configMaps = jobSpecification.getConfigMaps();

        // Then
        assertNotNull(configMaps);
        assertEquals(1, configMaps.size());
        assertEquals(expectedConfigMap.getMetadata().getName(), configMaps.get(0).getMetadata().getName());
    }

    @Test
    void getSecrets_shouldReturnRegistryAuthSecret() {
        // Given
        String expectedSecretName = K8sNamingUtils.generateName("registry", BUILD_ID);
        Secret mockSecret = new Secret();
        when(manifestGenerator.dialRegistryAuthSecretConfig(expectedSecretName)).thenReturn(mockSecret);

        // When
        List<Secret> secrets = jobSpecification.getSecrets();

        // Then
        assertNotNull(secrets);
        assertEquals(1, secrets.size());
        assertEquals(mockSecret, secrets.get(0));

        verify(manifestGenerator).dialRegistryAuthSecretConfig(expectedSecretName);
    }

    @Test
    void getJob_shouldReturnJobWithCorrectConfiguration() {
        // Given
        when(appConfig.cloneBuilderJobConfig()).thenReturn(createDefaultJob());
        when(appConfig.getBuilderContainerConfig()).thenReturn(new ContainerBuilder().withName("builder").build());

        // When
        Job job = jobSpecification.getJob();

        // Then
        assertNotNull(job);
        assertNotNull(job.getMetadata());
        assertNotNull(job.getSpec());
        assertNotNull(job.getSpec().getTemplate());
        assertNotNull(job.getSpec().getTemplate().getSpec());
        assertNotNull(job.getSpec().getTemplate().getSpec().getContainers());
        assertEquals(1, job.getSpec().getTemplate().getSpec().getContainers().size());

        Container container = job.getSpec().getTemplate().getSpec().getContainers().get(0);
        List<String> args = container.getArgs();
        assertNotNull(args);
        assertTrue(args.contains("--destination=" + SOURCE_IMAGE_URI));
        assertTrue(args.contains("--context=/sources"));
        assertTrue(args.contains("--dockerfile=/templates/Dockerfile"));
    }

    @Test
    void getJob_shouldConfigureSecretVolumeWhenBasicAuthIsUsed() {
        // Given
        when(registryService.getAuthScheme()).thenReturn(DockerAuthScheme.BASIC);

        when(appConfig.cloneBuilderJobConfig()).thenReturn(createDefaultJobWithVolumes());
        when(appConfig.getBuilderContainerConfig()).thenReturn(new ContainerBuilder().withName("builder").build());

        jobSpecification = new ImageWrapperBuildJobSpecification(
                registryClient,
                registryService,
                manifestGenerator,
                appConfig,
                NAMESPACE,
                DOCKER_CONFIG_PATH,
                BUILD_ID,
                IMAGE_NAME,
                dockerImageSource,
                distroInfo,
                MCP_PROXY_ALPINE_IMAGE_URI,
                MCP_PROXY_DEBIAN_IMAGE_URI
        );

        // When
        Job job = jobSpecification.getJob();

        // Then
        assertNotNull(job.getSpec().getTemplate().getSpec().getVolumes());
        assertTrue(job.getSpec().getTemplate().getSpec().getVolumes().size() > 0);

        Container container = job.getSpec().getTemplate().getSpec().getContainers().get(0);
        assertNotNull(container.getVolumeMounts());
        assertTrue(container.getVolumeMounts().stream()
                .anyMatch(vm -> vm.getMountPath().equals(DOCKER_CONFIG_PATH)));

        assertTrue(container.getVolumeMounts().stream()
                .anyMatch(vm -> vm.getSubPath() != null && vm.getSubPath().equals(ManifestGenerator.DOCKER_CONFIG_KEY)));
    }

    private Job createDefaultJob() {
        Container builderContainer = new ContainerBuilder()
                .withName("builder")
                .withArgs(new ArrayList<>())
                .build();

        PodSpec podSpec = new PodSpecBuilder()
                .withContainers(builderContainer)
                .build();

        PodTemplateSpec podTemplate = new PodTemplateSpecBuilder()
                .withSpec(podSpec)
                .build();

        JobSpec jobSpec = new JobSpecBuilder()
                .withTemplate(podTemplate)
                .build();

        return new JobBuilder()
                .withSpec(jobSpec)
                .build();
    }

    private Job createDefaultJobWithVolumes() {
        Container builderContainer = new ContainerBuilder()
                .withName("builder")
                .withArgs(new ArrayList<>())
                .build();

        PodSpec podSpec = new PodSpecBuilder()
                .withContainers(builderContainer)
                .withVolumes(new ArrayList<>())
                .build();

        PodTemplateSpec podTemplate = new PodTemplateSpecBuilder()
                .withSpec(podSpec)
                .build();

        JobSpec jobSpec = new JobSpecBuilder()
                .withTemplate(podTemplate)
                .build();

        return new JobBuilder()
                .withSpec(jobSpec)
                .build();
    }

    private ConfigMap createDockerfileConfigMap(String name, List<String> sourceImageArgs) {
        var dockerfileTemplate = """
                FROM {{BASE_IMAGE}}
                CMD [{{BASE_IMAGE_COMMAND}}]
                """.replace("{{BASE_IMAGE}}", ImageWrapperBuildJobSpecificationTest.SOURCE_IMAGE_URI)
                .replace("{{BASE_IMAGE_COMMAND}}", "\"" + String.join("\",\"", sourceImageArgs) + "\"");

        var configData = new HashMap<String, String>();
        configData.put("Dockerfile", dockerfileTemplate);

        return new ConfigMapBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .withData(configData)
                .build();
    }
}