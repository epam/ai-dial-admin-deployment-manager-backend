package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.docker.DockerRegistryClient;
import com.epam.aidial.deployment.manager.model.DistroInfo;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.ImageBuilder;
import com.epam.aidial.deployment.manager.model.ImageEntrypoint;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
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

import static org.assertj.core.api.Assertions.assertThat;
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
    private static final String BUILDER_CONTAINER_NAME = "builder-container";
    private static final String PUSH_CONTAINER_NAME = "push-container";

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

        jobSpecification = createJobSpecification(ImageBuilder.BUILDKIT_ROOTLESS);
    }

    private ImageWrapperBuildJobSpecification createJobSpecification(ImageBuilder imageBuilder) {
        return new ImageWrapperBuildJobSpecification(
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
                MCP_PROXY_DEBIAN_IMAGE_URI,
                imageBuilder
        );
    }

    @Test
    void getJobId_shouldReturnBuildId() {
        // When
        String jobId = jobSpecification.getJobId();

        // Then
        assertThat(jobId).isEqualTo(BUILD_ID);
    }

    @Test
    void getNamespace_shouldReturnNamespace() {
        // When
        String namespace = jobSpecification.getNamespace();

        // Then
        assertThat(namespace).isEqualTo(NAMESPACE);
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
        assertThat(configMaps).isNotNull();
        assertThat(configMaps).hasSize(1);
        assertThat(configMaps.getFirst().getMetadata().getName()).isEqualTo(expectedConfigMap.getMetadata().getName());
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
        assertThat(secrets).isNotNull();
        assertThat(secrets).hasSize(1);
        assertThat(secrets.getFirst()).isEqualTo(mockSecret);

        verify(manifestGenerator).dialRegistryAuthSecretConfig(expectedSecretName);
    }

    @Test
    void getJob_shouldReturnJobWithCorrectConfiguration() {
        // Given
        when(appConfig.cloneBuilderJobConfig()).thenReturn(createDefaultJob());
        when(appConfig.getBuilderRootlessContainerConfig())
                .thenReturn(new ContainerBuilder().withName(BUILDER_CONTAINER_NAME).build());
        when(appConfig.getPushContainerConfig())
                .thenReturn(new ContainerBuilder().withName(PUSH_CONTAINER_NAME).build());

        // When
        Job job = jobSpecification.getJob();

        // Then
        assertThat(job).isNotNull();
        assertThat(job.getMetadata()).isNotNull();
        assertThat(job.getSpec()).isNotNull();
        assertThat(job.getSpec().getTemplate()).isNotNull();
        assertThat(job.getSpec().getTemplate().getSpec()).isNotNull();
        assertThat(job.getSpec().getTemplate().getSpec().getContainers()).isNotNull();
        assertThat(job.getSpec().getTemplate().getSpec().getContainers()).hasSize(2);

        Container buildContainer = getContainerByName(job, BUILDER_CONTAINER_NAME);
        List<String> buildArgs = buildContainer.getArgs();
        assertThat(buildArgs).isNotNull();
        assertThat(buildArgs.stream().anyMatch(arg -> arg.equals("dockerfile=/templates"))).isTrue();
        assertThat(buildArgs.stream().anyMatch(arg -> arg.equals("context=/templates"))).isTrue();
        Container pushContainer = getContainerByName(job, PUSH_CONTAINER_NAME);
        List<EnvVar> pushEnvVars = pushContainer.getEnv();
        assertThat(pushEnvVars).isNotNull();
        assertThat(pushEnvVars.stream()
                .anyMatch(envVar -> envVar.getName().equals("TARGET_IMAGE") && envVar.getValue().equals(SOURCE_IMAGE_URI))).isTrue();
    }

    @Test
    void getJob_shouldReturnJobWithCorrectConfigurationForRoot() {
        // Given
        when(appConfig.cloneBuilderJobConfig()).thenReturn(createDefaultJob());
        when(appConfig.getBuilderRootContainerConfig())
                .thenReturn(new ContainerBuilder().withName(BUILDER_CONTAINER_NAME).build());
        when(appConfig.getPushContainerConfig())
                .thenReturn(new ContainerBuilder().withName(PUSH_CONTAINER_NAME).build());

        // When
        Job job = createJobSpecification(ImageBuilder.BUILDKIT).getJob();

        // Then
        assertThat(job).isNotNull();
        assertThat(job.getMetadata()).isNotNull();
        assertThat(job.getSpec()).isNotNull();
        assertThat(job.getSpec().getTemplate()).isNotNull();
        assertThat(job.getSpec().getTemplate().getSpec()).isNotNull();
        assertThat(job.getSpec().getTemplate().getSpec().getContainers()).isNotNull();
        assertThat(job.getSpec().getTemplate().getSpec().getContainers()).hasSize(2);

        Container buildContainer = getContainerByName(job, BUILDER_CONTAINER_NAME);
        List<String> buildArgs = buildContainer.getArgs();
        assertThat(buildArgs).isNotNull();
        assertThat(buildArgs.stream().anyMatch(arg -> arg.equals("dockerfile=/templates"))).isTrue();
        assertThat(buildArgs.stream().anyMatch(arg -> arg.equals("context=/templates"))).isTrue();
        Container pushContainer = getContainerByName(job, PUSH_CONTAINER_NAME);
        List<EnvVar> pushEnvVars = pushContainer.getEnv();
        assertThat(pushEnvVars).isNotNull();
        assertThat(pushEnvVars.stream()
                .anyMatch(envVar -> envVar.getName().equals("TARGET_IMAGE") && envVar.getValue().equals(SOURCE_IMAGE_URI))).isTrue();
    }

    @Test
    void getJob_shouldConfigureSecretVolumeWhenBasicAuthIsUsed() {
        // Given
        when(registryService.getAuthScheme()).thenReturn(DockerAuthScheme.BASIC);
        when(appConfig.cloneBuilderJobConfig()).thenReturn(createDefaultJobWithVolumes());
        when(appConfig.getBuilderRootlessContainerConfig())
                .thenReturn(new ContainerBuilder().withName(BUILDER_CONTAINER_NAME).build());
        when(appConfig.getPushContainerConfig())
                .thenReturn(new ContainerBuilder().withName(PUSH_CONTAINER_NAME).build());

        // When
        Job job = jobSpecification.getJob();

        // Then
        assertThat(job.getSpec().getTemplate().getSpec().getVolumes()).isNotNull();
        assertThat(job.getSpec().getTemplate().getSpec().getVolumes()).isNotEmpty();

        Container pushContainer = getContainerByName(job, PUSH_CONTAINER_NAME);
        assertThat(pushContainer.getVolumeMounts()).isNotNull();
        assertThat(pushContainer.getVolumeMounts().stream()
                .anyMatch(vm -> vm.getMountPath().equals(DOCKER_CONFIG_PATH))).isTrue();

        assertThat(pushContainer.getVolumeMounts().stream()
                .anyMatch(vm -> vm.getSubPath() != null && vm.getSubPath().equals(ManifestGenerator.DOCKER_CONFIG_KEY))).isTrue();
    }

    private Job createDefaultJob() {
        Container builderContainer = new ContainerBuilder()
                .withName(BUILDER_CONTAINER_NAME)
                .withArgs(new ArrayList<>())
                .build();

        Container pushContainer = new ContainerBuilder()
                .withName(PUSH_CONTAINER_NAME)
                .withArgs(new ArrayList<>())
                .build();

        PodSpec podSpec = new PodSpecBuilder()
                .withContainers(builderContainer, pushContainer)
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
                .withName(BUILDER_CONTAINER_NAME)
                .withArgs(new ArrayList<>())
                .build();

        Container pushContainer = new ContainerBuilder()
                .withName(PUSH_CONTAINER_NAME)
                .withArgs(new ArrayList<>())
                .build();

        PodSpec podSpec = new PodSpecBuilder()
                .withContainers(builderContainer, pushContainer)
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

    private Container getContainerByName(Job job, String containerName) {
        return job.getSpec().getTemplate().getSpec().getContainers().stream()
                .filter(container -> containerName.equals(container.getName())).findFirst().get();
    }

}