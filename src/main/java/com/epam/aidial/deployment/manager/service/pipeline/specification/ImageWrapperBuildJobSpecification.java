package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.docker.DockerRegistryClient;
import com.epam.aidial.deployment.manager.model.DistroInfo;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.ImageBuilder;
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
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
public class ImageWrapperBuildJobSpecification extends ImageBuildAndPushJobSpecification {

    private static final String WRAPPER_DOCKERFILE_TEMPLATE = "/build/mcp_wrapper.Dockerfile";
    private static final String DOCKERFILE = "dockerfile";
    private static final List<String> BUILDER_ARGS = List.of(
            "--local", "context=/templates",
            "--local", "dockerfile=/templates"
    );
    private static final String DOCKER_CONFIG_ENV_VAR_NAME = "DOCKER_CONFIG";
    private static final String DOCKER_CONFIG_ENV_VAR_VALUE = "/kaniko/.docker";

    private final DockerRegistryClient registryClient;
    private final DockerImageSource dockerImageSource;
    private final DistroInfo distroInfo;
    private final String mcpProxyAlpineImageName;
    private final String mcpProxyDebianImageName;

    public ImageWrapperBuildJobSpecification(
            DockerRegistryClient registryClient,
            RegistryService registryService,
            ManifestGenerator manifestGenerator,
            AppProperties appConfig,
            String namespace,
            String dockerConfigPath,
            String buildId,
            String targetImage,
            DockerImageSource dockerImageSource,
            DistroInfo distroInfo,
            String mcpProxyAlpineImageName,
            String mcpProxyDebianImageName,
            ImageBuilder imageBuilder
    ) {
        super(registryService,
                manifestGenerator,
                appConfig,
                namespace,
                dockerConfigPath,
                buildId,
                targetImage,
                imageBuilder);
        this.registryClient = registryClient;
        this.dockerImageSource = dockerImageSource;
        this.distroInfo = distroInfo;
        this.mcpProxyAlpineImageName = mcpProxyAlpineImageName;
        this.mcpProxyDebianImageName = mcpProxyDebianImageName;
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
        return List.of(getRegistryAuthSecret());
    }

    @Override
    public Job getJob() {
        log.info("Target image: {}", targetImage);
        var config = createBaseJobConfig();
        var podSpec = getPodSpec(config);
        var builderContainer = getBuilderContainer(podSpec);
        configureBuilderContainerArgs(builderContainer);
        configureBuilderContainerVolume(podSpec, builderContainer);
        addEnvVar(builderContainer, DOCKER_CONFIG_ENV_VAR_NAME, DOCKER_CONFIG_ENV_VAR_VALUE);
        configurePushContainer(podSpec);
        configureRegistryAuth(podSpec, builderContainer);
        return config.data();
    }

    private void configureBuilderContainerVolume(MappingChain<PodSpec> podSpec, MappingChain<Container> builderContainer) {
        var configmapName = K8sNamingUtils.generateName(DOCKERFILE, buildId);
        var configmapVolumeName = "dockerfile-volume";
        podSpec.getList(Mappers.POD_VOLUMES_FIELD, Mappers.VOLUME_NAME)
                .get(configmapVolumeName)
                .data()
                .setConfigMap(new ConfigMapVolumeSourceBuilder().withName(configmapName).build());
        var configVolumeMount = builderContainer.getList(Mappers.CONTAINER_VOLUME_MOUNTS_FIELD, Mappers.VOLUME_MOUNT_PATH)
                .get("/templates/Dockerfile")
                .data();
        configVolumeMount.setName(configmapVolumeName);
        configVolumeMount.setSubPath("Dockerfile");
    }

    private void configureBuilderContainerArgs(MappingChain<Container> builderContainer) {
        builderContainer.get(Mappers.CONTAINER_ARGS_FIELD)
                .data()
                .addAll(BUILDER_ARGS);
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
