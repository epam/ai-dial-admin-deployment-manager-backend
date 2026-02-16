package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
import com.epam.aidial.deployment.manager.model.GitSecretConfig;
import com.epam.aidial.deployment.manager.model.ImageBuilder;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.utils.GitCommandBuilder;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import com.epam.aidial.deployment.manager.utils.mapping.Mappers;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ImageBuildFromGitJobSpecification extends ImageBuildAndPushJobSpecification {

    private static final String GIT = "git";
    private static final String WORKSPACE_PATH = "/workspace";

    private final GitService gitService;
    private final GitDockerfileImageSource gitDockerfileImageSource;

    public ImageBuildFromGitJobSpecification(RegistryService registryService,
                                             ManifestGenerator manifestGenerator,
                                             GitService gitService,
                                             AppProperties appConfig,
                                             String namespace,
                                             String dockerConfigPath,
                                             String buildId,
                                             String targetImage,
                                             GitDockerfileImageSource gitDockerfileImageSource,
                                             ImageBuilder imageBuilder) {
        super(registryService,
                manifestGenerator,
                appConfig,
                namespace,
                dockerConfigPath,
                buildId,
                targetImage,
                imageBuilder);
        this.gitService = gitService;
        this.gitDockerfileImageSource = gitDockerfileImageSource;
    }

    @Override
    public List<ConfigMap> getConfigMaps() {
        return List.of();
    }

    @Override
    public List<Secret> getSecrets() {
        List<Secret> secrets = new ArrayList<>();
        secrets.add(getRegistryAuthSecret());
        gitService.prepareGitSecret(gitDockerfileImageSource.getUrl(), getGitSecretName(), manifestGenerator)
                .ifPresent(config -> secrets.add(config.getSecret()));
        return secrets;
    }

    @Override
    public Job getJob() {
        log.info("Target image: {}", targetImage);
        var config = createBaseJobConfig();
        var podSpec = getPodSpec(config);
        configureInitContainer(podSpec);
        var builderContainer = getBuilderContainer(podSpec);
        configureBuilderContainerArgs(builderContainer);
        configurePushContainer(podSpec);
        return config.data();
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

    private void configureBuilderContainerArgs(MappingChain<Container> builderContainer) {
        var dockerfile = StringUtils.isNotBlank(gitDockerfileImageSource.getBaseDirectory())
                ? WORKSPACE_PATH + "/" + gitDockerfileImageSource.getBaseDirectory()
                : WORKSPACE_PATH;
        var args = new ArrayList<>(List.of(
                "--local",
                "context=%s".formatted(WORKSPACE_PATH),
                "--local",
                "dockerfile=%s".formatted(dockerfile)
        ));
        builderContainer.get(Mappers.CONTAINER_ARGS_FIELD)
                .data()
                .addAll(args);
    }
}
