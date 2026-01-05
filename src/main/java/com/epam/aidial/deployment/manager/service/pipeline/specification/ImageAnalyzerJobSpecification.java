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
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ImageAnalyzerJobSpecification implements JobSpecification {

    private static final String TEMPLATE = "template";
    private static final String REGISTRY = "registry";
    private static final String DOCKER_CONFIG_PATH = "/config";
    private static final String DOCKER_CONFIG_FILE = DOCKER_CONFIG_PATH + "/config.json";

    private final RegistryService registryService;
    private final ManifestGenerator manifestGenerator;
    private final AppProperties appconfig;

    private final String namespace;

    private final String jobId;
    private final String imageUri;

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
        var configMapName = K8sNamingUtils.generateName(TEMPLATE, jobId);
        var dockerConfigMap = createTemplateConfigmap(configMapName);
        return List.of(dockerConfigMap);
    }

    @Override
    public List<Secret> getSecrets() {
        var authSecretName = K8sNamingUtils.generateName(REGISTRY, jobId);
        var dialRegistryAuthSecret = manifestGenerator.dialRegistryAuthSecretConfig(authSecretName);
        return List.of(dialRegistryAuthSecret);
    }

    @Override
    public Job getJob() {
        var config = new MappingChain<>(this.appconfig.cloneAnalyzerJobConfig());
        config.get(Mappers.JOB_METADATA_FIELD)
                .data()
                .setName(K8sNamingUtils.generateName(jobId));
        var podSpec = config.get(Mappers.JOB_SPEC_FIELD)
                .get(Mappers.JOB_TEMPLATE_FIELD)
                .get(Mappers.JOB_TEMPLATE_SPEC_FIELD);

        var builder = podSpec.getList(Mappers.POD_CONTAINERS_FIELD, Mappers.CONTAINER_NAME)
                .getOrDefault(appconfig.getAnalyzerContainerConfig().getName(), appconfig::cloneAnalyzerContainerConfig);
        builder.get(Mappers.CONTAINER_ARGS_FIELD)
                .data()
                .addAll(List.of(
                        imageUri,
                        "-o", "template",
                        "-t", "/templates/distro.tmpl"
                ));

        var configmapName = K8sNamingUtils.generateName(TEMPLATE, jobId);
        var templateVolumeName = "template-volume";
        podSpec.getList(Mappers.POD_VOLUMES_FIELD, Mappers.VOLUME_NAME)
                .get(templateVolumeName)
                .data()
                .setConfigMap(new ConfigMapVolumeSourceBuilder().withName(configmapName).build());
        var configVolumeMount = builder.getList(Mappers.CONTAINER_VOLUME_MOUNTS_FIELD, Mappers.VOLUME_MOUNT_PATH)
                .get("/templates/distro.tmpl")
                .data();
        configVolumeMount.setName(templateVolumeName);
        configVolumeMount.setSubPath("distro.tmpl");

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

    private ConfigMap createTemplateConfigmap(String name) {
        var fileName = "distro.tmpl";

        var syftOutputTemplate = """
                ################# RESULT #################
                ID: {{ .distro.id }}
                VERSION: {{ .distro.versionID }}
                ##########################################
                """;

        var configData = new HashMap<String, String>();
        configData.put(fileName, syftOutputTemplate);

        return new ConfigMapBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .withData(configData)
                .build();
    }

}
