package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.service.JobSpecification;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import com.epam.aidial.deployment.manager.utils.mapping.Mappers;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ImageCopyJobSpecification implements JobSpecification {

    private static final String REGISTRY = "registry";
    private static final String DOCKER_CONFIG_PATH = "/config";
    private static final String DOCKER_CONFIG_FILE = DOCKER_CONFIG_PATH + "/config.json";

    private final RegistryService registryService;
    private final ManifestGenerator manifestGenerator;
    private final AppProperties appConfig;

    private final String namespace;

    private final String jobId;
    private final String sourceImageName;
    private final String targetImageName;

    @Override
    public String getJobId() {
        return jobId;
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
        var authSecretName = K8sNamingUtils.generateName(REGISTRY, jobId);
        var dialRegistryAuthSecret = manifestGenerator.dialRegistryAuthSecretConfig(authSecretName);
        return List.of(dialRegistryAuthSecret);
    }

    @Override
    public Job getJob() {
        var config = new MappingChain<>(this.appConfig.cloneCopyImageJobConfig());
        config.get(Mappers.JOB_METADATA_FIELD)
                .data()
                .setName(K8sNamingUtils.generateMcpPrefixedName(jobId));
        var podSpec = config.get(Mappers.JOB_SPEC_FIELD)
                .get(Mappers.JOB_TEMPLATE_FIELD)
                .get(Mappers.JOB_TEMPLATE_SPEC_FIELD);

        var builder = podSpec.getList(Mappers.POD_CONTAINERS_FIELD, Mappers.CONTAINER_NAME)
                .getOrDefault(appConfig.getAnalyzerContainerConfig().getName(), appConfig::cloneCopyImageContainerConfig);
        builder.get(Mappers.CONTAINER_ARGS_FIELD)
                .data()
                .addAll(List.of(
                        "copy",
                        "docker://" + sourceImageName,
                        "docker://" + targetImageName,
                        "--authfile", DOCKER_CONFIG_FILE
                ));

        if (registryService.getAuthScheme() == DockerAuthScheme.BASIC) {
            builder.getList(Mappers.CONTAINER_ENV_FIELD, Mappers.ENV_VAR_NAME)
                    .get("DOCKER_CONFIG")
                    .data()
                    .setValue(DOCKER_CONFIG_PATH);

            var secretName = K8sNamingUtils.generateName(REGISTRY, jobId);
            var secretVolumeName = "secret-volume";
            podSpec.getList(Mappers.POD_VOLUMES_FIELD, Mappers.VOLUME_NAME)
                    .get(secretVolumeName)
                    .data()
                    .setSecret(new SecretVolumeSourceBuilder().withSecretName(secretName).build());
            var secretVolumeMount = builder.getList(Mappers.CONTAINER_VOLUME_MOUNTS_FIELD, Mappers.VOLUME_MOUNT_PATH)
                    .get(DOCKER_CONFIG_FILE)
                    .data();
            secretVolumeMount.setName(secretVolumeName);
            secretVolumeMount.setSubPath(ManifestGenerator.DOCKER_CONFIG_KEY);
        }

        return config.data();
    }

}
