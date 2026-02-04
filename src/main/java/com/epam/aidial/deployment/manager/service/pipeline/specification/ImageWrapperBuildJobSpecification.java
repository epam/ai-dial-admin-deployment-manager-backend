package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.docker.DockerRegistryClient;
import com.epam.aidial.deployment.manager.model.DistroInfo;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.ImageBuilder;
import com.epam.aidial.deployment.manager.service.JobSpecification;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.epam.aidial.deployment.manager.utils.mapping.Mappers;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ImageWrapperBuildJobSpecification implements JobSpecification {

    private static final String WRAPPER_DOCKERFILE_TEMPLATE = "/build/mcp_wrapper.Dockerfile";
    private static final String DOCKERFILE = "dockerfile";
    private static final String REGISTRY = "registry";
    private static final List<String> BUILDKIT_ARGS = List.of(
            "--local", "context=/sources",
            "--local", "dockerfile=/templates/Dockerfile"
    );

    private final DockerRegistryClient registryClient;
    private final RegistryService registryService;
    private final ManifestGenerator manifestGenerator;
    private final AppProperties appConfig;

    private final String namespace;
    private final String dockerConfigPath;

    private final String buildId;
    private final String targetImage;
    private final DockerImageSource dockerImageSource;
    private final DistroInfo distroInfo;

    private final String mcpProxyAlpineImageName;
    private final String mcpProxyDebianImageName;
    private final ImageBuilder imageBuilder;

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
        var configMapName = K8sNamingUtils.generateName(DOCKERFILE, buildId);
        var entrypoint = extractEntrypoint(dockerImageSource);
        var dockerConfigMap = createDockerfileConfigmap(configMapName, dockerImageSource.getImageUri(), entrypoint);

        return List.of(dockerConfigMap);
    }

    @Override
    public List<Secret> getSecrets() {
        var authSecretName = K8sNamingUtils.generateName(REGISTRY, buildId);
        var dialRegistryAuthSecret = manifestGenerator.dialRegistryAuthSecretConfig(authSecretName);
        return List.of(dialRegistryAuthSecret);
    }

    @Override
    public Job getJob() {
        log.info("Target image: {}", targetImage);

        var config = new MappingChain<>(this.appConfig.cloneBuilderJobConfig());
        config.get(Mappers.JOB_METADATA_FIELD)
                .data()
                .setName(K8sNamingUtils.generateName(buildId));
        var podSpec = config.get(Mappers.JOB_SPEC_FIELD)
                .get(Mappers.JOB_TEMPLATE_FIELD)
                .get(Mappers.JOB_TEMPLATE_SPEC_FIELD);

        var builder = createBuilder(podSpec);

        var configmapName = K8sNamingUtils.generateName(DOCKERFILE, buildId);
        var configmapVolumeName = "dockerfile-volume";
        podSpec.getList(Mappers.POD_VOLUMES_FIELD, Mappers.VOLUME_NAME)
                .get(configmapVolumeName)
                .data()
                .setConfigMap(new ConfigMapVolumeSourceBuilder().withName(configmapName).build());
        var configVolumeMount = builder.getList(Mappers.CONTAINER_VOLUME_MOUNTS_FIELD, Mappers.VOLUME_MOUNT_PATH)
                .get("/templates/Dockerfile")
                .data();
        configVolumeMount.setName(configmapVolumeName);
        configVolumeMount.setSubPath("Dockerfile");

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

        return config.data();
    }

    private MappingChain<Container> createBuilder(MappingChain<PodSpec> podSpec) {
        switch (imageBuilder) {
            case BUILDKIT -> {
                log.info("Buildkit is in use for image build");
                return createBuildkitContainer(podSpec);
            }
            case BUILDKIT_ROOTLESS -> {
                log.info("Buildkit rootless is in use for image build");
                return createBuildkitRootlessContainer(podSpec);
            }
            default -> {
                log.info("Kaniko is in use for image wrapper");
                return createKanikoContainer(podSpec);
            }
        }
    }

    private MappingChain<Container> createBuildkitRootlessContainer(MappingChain<PodSpec> podSpec) {
        var builder = podSpec.getList(Mappers.POD_CONTAINERS_FIELD, Mappers.CONTAINER_NAME)
                .getOrDefault(appConfig.getBuilderRootlessContainerConfig().getName(), appConfig::cloneBuilderRootlessContainerConfig);
        builder.get(Mappers.CONTAINER_ARGS_FIELD)
                .data()
                .addAll(BUILDKIT_ARGS);
        return builder;
    }

    private MappingChain<Container> createBuildkitContainer(MappingChain<PodSpec> podSpec) {
        var builder = podSpec.getList(Mappers.POD_CONTAINERS_FIELD, Mappers.CONTAINER_NAME)
                .getOrDefault(appConfig.getBuilderRootContainerConfig().getName(), appConfig::cloneBuilderRootContainerConfig);
        builder.get(Mappers.CONTAINER_ARGS_FIELD)
                .data()
                .addAll(BUILDKIT_ARGS);
        return builder;
    }

    private MappingChain<Container> createKanikoContainer(MappingChain<PodSpec> podSpec) {
        var builder = podSpec.getList(Mappers.POD_CONTAINERS_FIELD, Mappers.CONTAINER_NAME)
                .getOrDefault(appConfig.getBuilderContainerConfig().getName(), appConfig::cloneBuilderContainerConfig);
        builder.get(Mappers.CONTAINER_ARGS_FIELD)
                .data()
                .addAll(List.of(
                        "--destination=%s".formatted(targetImage),
                        "--context=/sources",
                        "--dockerfile=/templates/Dockerfile"
                ));
        return builder;
    }

    private ConfigMap createDockerfileConfigmap(String name, String sourceImageName, List<String> sourceImageArgs) {
        var fileName = "Dockerfile";

        var dockerfileTemplate = ResourceUtils.readResource(WRAPPER_DOCKERFILE_TEMPLATE)
                .replace("{{MCP_PROXY_HOLDER_IMAGE}}", getMcpProxyHolderImage())
                .replace("{{BASE_IMAGE}}", sourceImageName)
                .replace("{{BASE_IMAGE_COMMAND}}", "\"" + String.join("\",\"", sourceImageArgs) + "\"");

        var configData = new HashMap<String, String>();
        configData.put(fileName, dockerfileTemplate);

        return new ConfigMapBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .withData(configData)
                .build();
    }

    private String getMcpProxyHolderImage() {
        var id = distroInfo.id().toLowerCase();
        if (id.equals("alpine")) {
            return mcpProxyAlpineImageName;
        } else if (id.equals("debian")) {
            return mcpProxyDebianImageName;
        }
        throw new IllegalArgumentException("Unsupported distro id: %s".formatted(id));
    }

    private List<String> extractEntrypoint(DockerImageSource dockerImageSource) {
        List<String> entrypoint;
        if (dockerImageSource.getEntrypoint() != null) {
            entrypoint = dockerImageSource.getEntrypoint();
        } else {
            var imageEntrypoint = registryClient.getEntrypoint(dockerImageSource.getImageUri());
            entrypoint = new ArrayList<>();
            entrypoint.addAll(ListUtils.emptyIfNull(imageEntrypoint.getEntrypoint()));
            entrypoint.addAll(ListUtils.emptyIfNull(imageEntrypoint.getCmd()));
        }
        return entrypoint;
    }

}
