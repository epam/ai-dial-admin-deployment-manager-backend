package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.docker.DockerRegistryClient;
import com.epam.aidial.deployment.manager.model.DistroInfo;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.ImageBuilder;
import com.epam.aidial.deployment.manager.service.JobSpecification;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class ImageWrapperBuildJobSpecificationFactory {

    private final DockerRegistryClient registryClient;
    private final RegistryService registryService;
    private final ManifestGenerator manifestGenerator;
    private final AppProperties appConfig;

    @Value("${app.build-namespace}")
    private final String namespace;
    @Value("${app.docker-config-path}")
    private final String dockerConfigPath;

    @Value("${app.build.mcp-proxy.images.alpine}")
    private final String mcpProxyAlpineImageName;
    @Value("${app.build.mcp-proxy.images.debian}")
    private final String mcpProxyDebianImageName;

    public JobSpecification create(String jobId, String wrapperImageName,
                                   DockerImageSource dockerImageSource,
                                   DistroInfo distroInfo,
                                   ImageBuilder imageBuilder) {
        return new ImageWrapperBuildJobSpecification(
                registryClient,
                registryService,
                manifestGenerator,
                appConfig,

                namespace,
                dockerConfigPath,

                jobId,
                wrapperImageName,
                dockerImageSource,
                distroInfo,

                mcpProxyAlpineImageName,
                mcpProxyDebianImageName,
                imageBuilder
        );
    }

}
