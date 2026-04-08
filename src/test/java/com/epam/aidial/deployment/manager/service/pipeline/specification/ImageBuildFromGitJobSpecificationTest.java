package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
import com.epam.aidial.deployment.manager.model.ImageBuilder;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageBuildFromGitJobSpecificationTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String BUILD_ID = "test-build-id";
    private static final String IMAGE_NAME = BUILD_ID + ":1.0.0";
    private static final String DOCKER_CONFIG_PATH = "/kaniko/.docker/config.json";
    private static final String GIT_URL = "https://github.com/test/repo.git";
    private static final String GIT_BRANCH = "main";
    private static final String BUILDER_CONTAINER_NAME = "builder-container";
    private static final String PUSH_CONTAINER_NAME = "push-container";

    @Mock
    private RegistryService registryService;
    @Mock
    private ManifestGenerator manifestGenerator;
    @Mock
    private GitService gitService;
    @Mock
    private AppProperties appConfig;

    private GitDockerfileImageSource gitDockerfileImageSource;
    private ImageBuildFromGitJobSpecification jobSpecification;

    @BeforeEach
    void setUp() {
        gitDockerfileImageSource = GitDockerfileImageSource.builder()
                .url(GIT_URL)
                .branchName(GIT_BRANCH)
                .build();

        jobSpecification = createJobSpecification(gitDockerfileImageSource, ImageBuilder.BUILDKIT_ROOTLESS);
    }

    private ImageBuildFromGitJobSpecification createJobSpecification(
            GitDockerfileImageSource imageSource,
            ImageBuilder imageBuilder) {
        return new ImageBuildFromGitJobSpecification(
                registryService,
                manifestGenerator,
                gitService,
                appConfig,
                NAMESPACE,
                DOCKER_CONFIG_PATH,
                BUILD_ID,
                IMAGE_NAME,
                imageSource,
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
    void getConfigMaps_shouldReturnEmptyList() {
        // When
        List<ConfigMap> configMaps = jobSpecification.getConfigMaps();

        // Then
        assertThat(configMaps).isNotNull();
        assertThat(configMaps).isEmpty();
    }

    @Test
    void getSecrets_shouldReturnRegistryAuthSecret() {
        // Given
        String expectedSecretName = K8sNamingUtils.generateName("registry", BUILD_ID);
        Secret mockSecret = new Secret();
        when(manifestGenerator.dialRegistryAuthSecretConfig(expectedSecretName)).thenReturn(mockSecret);

        String gitSecretName = K8sNamingUtils.generateName("git", BUILD_ID);
        when(gitService.prepareGitSecret(GIT_URL, gitSecretName, manifestGenerator)).thenReturn(Optional.empty());

        // When
        List<Secret> secrets = jobSpecification.getSecrets();

        // Then
        assertThat(secrets).isNotNull();
        assertThat(secrets).hasSize(1);
        assertThat(secrets.getFirst()).isEqualTo(mockSecret);

        // Verify the correct secret name was used
        verify(manifestGenerator).dialRegistryAuthSecretConfig(expectedSecretName);
    }

    @Test
    void getJob_shouldReturnJobWithCorrectConfiguration() {
        // Given
        prepareConfiguration();
        when(appConfig.getBuilderRootlessContainerConfig())
                .thenReturn(new ContainerBuilder().withName(BUILDER_CONTAINER_NAME).build());

        String gitSecretName = K8sNamingUtils.generateName("git", BUILD_ID);
        when(gitService.prepareGitSecret(GIT_URL, gitSecretName, manifestGenerator)).thenReturn(Optional.empty());

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

        // Verify init container exists
        assertThat(job.getSpec().getTemplate().getSpec().getInitContainers()).isNotNull();
        assertThat(job.getSpec().getTemplate().getSpec().getInitContainers()).hasSize(1);
        Container initContainer = job.getSpec().getTemplate().getSpec().getInitContainers().getFirst();
        assertThat(initContainer.getName()).isEqualTo("git-clone");

        // Verify the container arguments contain the expected values
        Container buildContainer = getContainerByName(job, BUILDER_CONTAINER_NAME);
        List<String> args = buildContainer.getArgs();
        assertThat(args).isNotNull();
        assertThat(args.stream().anyMatch(arg -> arg.equals("context=/workspace"))).isTrue();
    }

    @Test
    void getJob_shouldReturnJobWithCorrectBuildkitRootConfiguration() {
        // Given
        prepareConfiguration();
        when(appConfig.getBuilderRootContainerConfig())
                .thenReturn(new ContainerBuilder().withName(BUILDER_CONTAINER_NAME).build());

        String gitSecretName = K8sNamingUtils.generateName("git", BUILD_ID);
        when(gitService.prepareGitSecret(GIT_URL, gitSecretName, manifestGenerator)).thenReturn(Optional.empty());

        // When
        Job job = createJobSpecification(gitDockerfileImageSource, ImageBuilder.BUILDKIT).getJob();

        // Verify the container arguments contain the expected values
        Container buildContainer = getContainerByName(job, BUILDER_CONTAINER_NAME);
        List<String> args = buildContainer.getArgs();
        assertThat(args).isNotNull();
        assertThat(args.stream().anyMatch(arg -> arg.equals("dockerfile=/workspace"))).isTrue();
        assertThat(args.stream().anyMatch(arg -> arg.equals("context=/workspace"))).isTrue();
        assertThat(args.stream().anyMatch(arg -> arg.equals("--no-push"))).isFalse();
    }

    @Test
    void getJob_shouldReturnJobWithCorrectBuildkitRootlessConfigurationWhenBaseDirectoryIsProvided() {
        // Given
        prepareConfiguration();
        when(appConfig.getBuilderRootlessContainerConfig())
                .thenReturn(new ContainerBuilder().withName(BUILDER_CONTAINER_NAME).build());

        String gitSecretName = K8sNamingUtils.generateName("git", BUILD_ID);
        when(gitService.prepareGitSecret(GIT_URL, gitSecretName, manifestGenerator)).thenReturn(Optional.empty());

        String baseDirectory = "src/app";
        gitDockerfileImageSource = GitDockerfileImageSource.builder()
                .url(GIT_URL)
                .branchName(GIT_BRANCH)
                .baseDirectory(baseDirectory)
                .build();

        Job job = createJobSpecification(gitDockerfileImageSource, ImageBuilder.BUILDKIT_ROOTLESS).getJob();

        // Verify the container arguments contain the expected values
        Container buildContainer = getContainerByName(job, BUILDER_CONTAINER_NAME);
        List<String> args = buildContainer.getArgs();
        assertThat(args).isNotNull();
        assertThat(args.stream().anyMatch(arg -> arg.equals("dockerfile=/workspace/src/app"))).isTrue();
        assertThat(args.stream().anyMatch(arg -> arg.equals("context=/workspace/src/app"))).isTrue();
        assertThat(args.stream().anyMatch(arg -> arg.equals("--no-push"))).isFalse();
    }

    @Test
    void getJob_shouldUseCorrectContextWhenBaseDirectoryIsProvided() {
        // Given
        String baseDirectory = "src/app";
        gitDockerfileImageSource = GitDockerfileImageSource.builder()
                .url(GIT_URL)
                .branchName(GIT_BRANCH)
                .baseDirectory(baseDirectory)
                .build();

        prepareConfiguration();
        when(appConfig.getBuilderRootlessContainerConfig())
                .thenReturn(new ContainerBuilder().withName(BUILDER_CONTAINER_NAME).build());

        String gitSecretName = K8sNamingUtils.generateName("git", BUILD_ID);
        when(gitService.prepareGitSecret(GIT_URL, gitSecretName, manifestGenerator)).thenReturn(Optional.empty());

        // When
        Job job = createJobSpecification(gitDockerfileImageSource, ImageBuilder.BUILDKIT_ROOTLESS).getJob();

        // Then
        Container buildContainer = getContainerByName(job, BUILDER_CONTAINER_NAME);
        List<String> args = buildContainer.getArgs();

        assertThat(args).contains("context=/workspace/src/app");
        assertThat(args).contains("dockerfile=/workspace/src/app");
    }

    @Test
    void getJob_shouldConfigureSecretVolumeWhenBasicAuthIsUsed() {
        // Given
        prepareConfiguration();
        when(registryService.getAuthScheme()).thenReturn(DockerAuthScheme.BASIC);
        when(appConfig.cloneBuilderJobConfig()).thenReturn(createDefaultJobWithVolumes());
        when(appConfig.getBuilderRootlessContainerConfig())
                .thenReturn(new ContainerBuilder().withName(BUILDER_CONTAINER_NAME).build());

        String gitSecretName = K8sNamingUtils.generateName("git", BUILD_ID);
        when(gitService.prepareGitSecret(GIT_URL, gitSecretName, manifestGenerator)).thenReturn(Optional.empty());

        // When
        Job job = createJobSpecification(gitDockerfileImageSource, ImageBuilder.BUILDKIT_ROOTLESS).getJob();

        // Then
        assertThat(job.getSpec().getTemplate().getSpec().getVolumes()).isNotNull();
        assertThat(job.getSpec().getTemplate().getSpec().getVolumes()).isNotEmpty();

        Container buildContainer = getContainerByName(job, BUILDER_CONTAINER_NAME);
        assertThat(buildContainer.getVolumeMounts()).isNotNull();
        assertThat(buildContainer.getVolumeMounts().stream()
                .anyMatch(vm -> vm.getMountPath().equals(DOCKER_CONFIG_PATH))).isFalse();

        assertThat(buildContainer.getVolumeMounts().stream()
                .anyMatch(vm -> vm.getSubPath() != null && vm.getSubPath().equals(ManifestGenerator.DOCKER_CONFIG_KEY))).isFalse();

        Container pushContainer = getContainerByName(job, PUSH_CONTAINER_NAME);
        assertThat(pushContainer.getVolumeMounts()).isNotNull();
        assertThat(pushContainer.getVolumeMounts().stream()
                .anyMatch(vm -> vm.getMountPath().equals(DOCKER_CONFIG_PATH))).isTrue();

        assertThat(pushContainer.getVolumeMounts().stream()
                .anyMatch(vm -> vm.getSubPath() != null && vm.getSubPath().equals(ManifestGenerator.DOCKER_CONFIG_KEY))).isTrue();
    }

    private Container getContainerByName(Job job, String containerName) {
        return job.getSpec().getTemplate().getSpec().getContainers().stream()
                .filter(container -> containerName.equals(container.getName())).findFirst().get();
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

        Volume secretVolume = new VolumeBuilder()
                .withName("secret-volume")
                .build();

        PodSpec podSpec = new PodSpecBuilder()
                .withContainers(builderContainer, pushContainer)
                .withVolumes(secretVolume)
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

    private void prepareConfiguration() {
        Container initContainerConfig = new ContainerBuilder().withName("git-clone").build();
        Container pushContainerConfig = new ContainerBuilder().withName(PUSH_CONTAINER_NAME).build();
        when(appConfig.cloneBuilderJobConfig()).thenReturn(createDefaultJob());
        when(appConfig.getPushContainerConfig()).thenReturn(pushContainerConfig);
        when(appConfig.getInitBuilderContainerConfig()).thenReturn(initContainerConfig);
        when(appConfig.cloneInitBuilderContainerConfig()).thenReturn(new ContainerBuilder(initContainerConfig).build());
    }
}
