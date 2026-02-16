package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.model.ImageBuilder;
import com.epam.aidial.deployment.manager.service.JobSpecification;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import com.epam.aidial.deployment.manager.utils.mapping.Mappers;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public abstract class ImageBuildAndPushJobSpecification implements JobSpecification {

    private static final String REGISTRY = "registry";
    private static final String TARGET_IMAGE_ENV_VAR_NAME = "TARGET_IMAGE";
    private static final String SECRET_VOLUME_NAME = "secret-volume";

    private final RegistryService registryService;
    protected final ManifestGenerator manifestGenerator;
    protected final AppProperties appConfig;
    private final String namespace;
    private final String dockerConfigPath;
    protected final String buildId;
    protected final String targetImage;
    private final ImageBuilder imageBuilder;

    @Override
    public String getJobId() {
        return buildId;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    protected Secret getRegistryAuthSecret() {
        var authSecretName = K8sNamingUtils.generateName(REGISTRY, buildId);
        return manifestGenerator.dialRegistryAuthSecretConfig(authSecretName);
    }

    protected MappingChain<Job> createBaseJobConfig() {
        var config = new MappingChain<>(appConfig.cloneBuilderJobConfig());
        config.get(Mappers.JOB_METADATA_FIELD)
                .data()
                .setName(K8sNamingUtils.generateName(buildId));
        return config;
    }

    protected MappingChain<PodSpec> getPodSpec(MappingChain<Job> job) {
        return job.get(Mappers.JOB_SPEC_FIELD)
                .get(Mappers.JOB_TEMPLATE_FIELD)
                .get(Mappers.JOB_TEMPLATE_SPEC_FIELD);
    }

    protected MappingChain<Container> getBuilderContainer(MappingChain<PodSpec> podSpec) {
        if (imageBuilder == ImageBuilder.BUILDKIT) {
            log.info("Buildkit root is in use for image build");
            return getBuilderRootContainerChain(podSpec);
        }
        log.info("Buildkit rootless is in use for image build");
        return getBuilderRootlessContainerChain(podSpec);
    }

    private MappingChain<Container> getBuilderRootContainerChain(MappingChain<PodSpec> podSpec) {
        return podSpec.getList(Mappers.POD_CONTAINERS_FIELD, Mappers.CONTAINER_NAME)
                .getOrDefault(appConfig.getBuilderRootContainerConfig().getName(), appConfig::cloneBuilderRootContainerConfig);
    }

    private MappingChain<Container> getBuilderRootlessContainerChain(MappingChain<PodSpec> podSpec) {
        return podSpec.getList(Mappers.POD_CONTAINERS_FIELD, Mappers.CONTAINER_NAME)
                .getOrDefault(appConfig.getBuilderRootlessContainerConfig().getName(), appConfig::cloneBuilderRootlessContainerConfig);
    }

    protected void configurePushContainer(MappingChain<PodSpec> podSpec) {
        var pushContainer = getPushContainer(podSpec);
        configurePushContainerEnvVars(pushContainer);
        configureRegistryAuth(podSpec, pushContainer);
    }

    private MappingChain<Container> getPushContainer(MappingChain<PodSpec> podSpec) {
        return podSpec.getList(Mappers.POD_CONTAINERS_FIELD, Mappers.CONTAINER_NAME)
                .getOrDefault(appConfig.getPushContainerConfig().getName(), appConfig::clonePushContainerConfig);
    }

    private void configurePushContainerEnvVars(MappingChain<Container> container) {
        container.get(Mappers.CONTAINER_ENV_FIELD).data()
                .add(new EnvVarBuilder().withName(TARGET_IMAGE_ENV_VAR_NAME).withValue(targetImage).build());
    }

    private void configureRegistryAuth(MappingChain<PodSpec> podSpec, MappingChain<Container> container) {
        if (registryService.getAuthScheme() == DockerAuthScheme.BASIC) {
            var secretName = K8sNamingUtils.generateName(REGISTRY, buildId);
            podSpec.getList(Mappers.POD_VOLUMES_FIELD, Mappers.VOLUME_NAME)
                    .get(SECRET_VOLUME_NAME)
                    .data()
                    .setSecret(new SecretVolumeSourceBuilder().withSecretName(secretName).build());
            var secretVolumeMount = container.getList(Mappers.CONTAINER_VOLUME_MOUNTS_FIELD, Mappers.VOLUME_MOUNT_PATH)
                    .get(dockerConfigPath)
                    .data();
            secretVolumeMount.setName(SECRET_VOLUME_NAME);
            secretVolumeMount.setSubPath(ManifestGenerator.DOCKER_CONFIG_KEY);
        }
    }
}
