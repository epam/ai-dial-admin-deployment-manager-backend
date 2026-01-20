package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
import com.epam.aidial.deployment.manager.model.GitSecretConfig;
import com.epam.aidial.deployment.manager.service.JobSpecification;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.utils.GitCommandBuilder;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import com.epam.aidial.deployment.manager.utils.PathUtils;
import com.epam.aidial.deployment.manager.utils.mapping.Mappers;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ImageBuildFromGitJobSpecification implements JobSpecification {

    private static final String REGISTRY = "registry";
    private static final String GIT = "git";
    private static final String WORKSPACE_PATH = "/workspace";

    private final RegistryService registryService;
    private final ManifestGenerator manifestGenerator;
    private final GitService gitService;
    private final AppProperties appConfig;

    private final String namespace;
    private final String dockerConfigPath;

    private final String buildId;
    private final String targetImage;
    private final GitDockerfileImageSource gitDockerfileImageSource;

    @Override
    public String getJobId() {
        return buildId;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public List<ConfigMap> getConfigMaps() {
        return List.of();
    }

    @Override
    public List<Secret> getSecrets() {
        List<Secret> secrets = new ArrayList<>();

        // Add registry secret if needed
        var authSecretName = K8sNamingUtils.generateName(REGISTRY, buildId);
        var dialRegistryAuthSecret = manifestGenerator.dialRegistryAuthSecretConfig(authSecretName);
        secrets.add(dialRegistryAuthSecret);

        // Add git secret if the repo needs authentication
        var gitSecretName = getGitSecretName();
        gitService.prepareGitSecret(gitDockerfileImageSource.getUrl(), gitSecretName, manifestGenerator)
                .ifPresent(config -> secrets.add(config.getSecret()));

        return secrets;
    }

    @Override
    public Job getJob() {
        log.info("Target image: {}", targetImage);

        var config = createBaseJobConfig();
        var podSpec = getPodSpec(config);

        configureInitContainer(podSpec);
        configureBuilderContainer(podSpec, targetImage);
        configurePushContainer(podSpec, targetImage);

        return config.data();
    }

    private MappingChain<Job> createBaseJobConfig() {
        var config = new MappingChain<>(this.appConfig.cloneBuilderJobConfig());
        config.get(Mappers.JOB_METADATA_FIELD)
                .data()
                .setName(K8sNamingUtils.generateName(buildId));
        return config;
    }

    private MappingChain<PodSpec> getPodSpec(MappingChain<Job> config) {
        return config.get(Mappers.JOB_SPEC_FIELD)
                .get(Mappers.JOB_TEMPLATE_FIELD)
                .get(Mappers.JOB_TEMPLATE_SPEC_FIELD);
    }

    private String getGitSecretName() {
        return K8sNamingUtils.generateName(GIT, buildId);
    }

    private void configureInitContainer(MappingChain<PodSpec> podSpec) {
        var initContainerChain = getInitContainerChain(podSpec);
        var gitSecretName = getGitSecretName();
        gitService.prepareGitSecret(
                gitDockerfileImageSource.getUrl(),
                gitSecretName,
                manifestGenerator
        ).ifPresent(gitSecretConfig ->
                configureGitCredentialsForInitContainer(podSpec, initContainerChain, gitSecretConfig));

        setInitContainerArgs(initContainerChain);
    }

    private MappingChain<Container> getInitContainerChain(MappingChain<PodSpec> podSpec) {
        return podSpec.getList(Mappers.POD_INIT_CONTAINERS_FIELD, Mappers.CONTAINER_NAME)
                .getOrDefault(appConfig.getInitBuilderContainerConfig().getName(), appConfig::cloneInitBuilderContainerConfig);
    }

    private void configureGitCredentialsForInitContainer(
            MappingChain<PodSpec> podSpec,
            MappingChain<Container> initContainerChain,
            GitSecretConfig gitSecretConfig) {

        configureGitSecretVolume(podSpec, gitSecretConfig, gitSecretConfig.getSecretName());
        configureGitSecretVolumeMounts(initContainerChain, gitSecretConfig);
    }

    private void setInitContainerArgs(MappingChain<Container> initContainerChain) {
        var cloneCommand = GitCommandBuilder.buildGitCloneCommand(gitDockerfileImageSource);

        // Override the entrypoint to use sh instead of git
        initContainerChain.get(Mappers.CONTAINER_COMMAND_FIELD)
                .data()
                .addAll(List.of("sh", "-c"));

        // Set the command string as args
        initContainerChain.get(Mappers.CONTAINER_ARGS_FIELD)
                .data()
                .add(cloneCommand);
    }

    private void configureBuilderContainer(MappingChain<PodSpec> podSpec, String targetImage) {
        var builder = getBuilderContainerChain(podSpec);
        configureBuilderContainerArgs(builder, targetImage);
    }

    private MappingChain<Container> getBuilderContainerChain(MappingChain<PodSpec> podSpec) {
        return podSpec.getList(Mappers.POD_CONTAINERS_FIELD, Mappers.CONTAINER_NAME)
                .getOrDefault(appConfig.getBuilderContainerConfig().getName(), appConfig::cloneBuilderContainerConfig);
    }

    private void configureBuilderContainerArgs(MappingChain<Container> builder, String targetImage) {
        var args = new ArrayList<>(List.of(
                "--destination=%s".formatted(targetImage),
                "--context=%s".formatted(WORKSPACE_PATH),
                "--no-push",
                "--tar-path=/image-build/image-tarball.tar"
        ));

        if (StringUtils.isNotBlank(gitDockerfileImageSource.getBaseDirectory())) {
            var baseDirectory = PathUtils.normalizeBaseDirectory(gitDockerfileImageSource.getBaseDirectory());
            args.add("--context-sub-path=%s".formatted(baseDirectory));
        }

        builder.get(Mappers.CONTAINER_ARGS_FIELD)
                .data()
                .addAll(args);
    }

    private void configurePushContainer(MappingChain<PodSpec> podSpec, String targetImage) {
        var builder = getPushContainerChain(podSpec);
        configurePushContainerEnvVars(builder, targetImage);
        configureRegistryAuth(podSpec, builder);
    }

    private MappingChain<Container> getPushContainerChain(MappingChain<PodSpec> podSpec) {
        return podSpec.getList(Mappers.POD_CONTAINERS_FIELD, Mappers.CONTAINER_NAME)
                .getOrDefault(appConfig.getPushContainerConfig().getName(), appConfig::clonePushContainerConfig);
    }

    private void configurePushContainerEnvVars(MappingChain<Container> pushContainerChain, String targetImage) {
        pushContainerChain.get(Mappers.CONTAINER_ENV_FIELD)
                .data()
                .add(new EnvVarBuilder().withName("TARGET_IMAGE").withValue(targetImage).build());
    }

    private void configureRegistryAuth(MappingChain<PodSpec> podSpec, MappingChain<Container> builder) {
        if (registryService.getAuthScheme() == DockerAuthScheme.BASIC) {
            var secretName = K8sNamingUtils.generateName(REGISTRY, buildId);
            var secretVolumeName = "secret-volume";
            podSpec.getList(Mappers.POD_VOLUMES_FIELD, Mappers.VOLUME_NAME)
                    .get(secretVolumeName)
                    .data()
                    .setSecret(new SecretVolumeSourceBuilder().withSecretName(secretName).build());
            var secretVolumeMount = builder.getList(Mappers.CONTAINER_VOLUME_MOUNTS_FIELD, Mappers.VOLUME_MOUNT_PATH)
                    .get(dockerConfigPath)
                    .data();
            secretVolumeMount.setName(secretVolumeName);
            secretVolumeMount.setSubPath(ManifestGenerator.DOCKER_CONFIG_KEY);
        }
    }

    private void configureGitSecretVolume(MappingChain<PodSpec> podSpec, GitSecretConfig gitSecretConfig, String gitSecretName) {
        var gitSecretVolume = podSpec.getList(Mappers.POD_VOLUMES_FIELD, Mappers.VOLUME_NAME)
                .get(gitSecretConfig.getVolumeName())
                .data();
        gitSecretVolume.setSecret(new SecretVolumeSourceBuilder()
                .withSecretName(gitSecretName)
                .withDefaultMode(gitSecretConfig.getDefaultMode())
                .build());
    }

    private void configureGitSecretVolumeMounts(MappingChain<Container> initContainerChain, GitSecretConfig gitSecretConfig) {
        for (var volumeMountConfig : gitSecretConfig.getVolumeMounts()) {
            var volumeMount = initContainerChain.getList(Mappers.CONTAINER_VOLUME_MOUNTS_FIELD, Mappers.VOLUME_MOUNT_PATH)
                    .get(volumeMountConfig.getMountPath())
                    .data();
            volumeMount.setName(gitSecretConfig.getVolumeName());
            volumeMount.setSubPath(volumeMountConfig.getSubPath());
            volumeMount.setReadOnly(volumeMountConfig.isReadOnly());
        }
    }

}
