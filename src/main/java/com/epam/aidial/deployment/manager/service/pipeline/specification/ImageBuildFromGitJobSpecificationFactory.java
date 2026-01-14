package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
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
public class ImageBuildFromGitJobSpecificationFactory {

    private final RegistryService registryService;
    private final ManifestGenerator manifestGenerator;
    private final GitService gitService;
    private final AppProperties appConfig;

    @Value("${app.build-namespace}")
    private final String namespace;
    @Value("${app.docker-config-path}")
    private final String dockerConfigPath;

    public ImageBuildFromGitJobSpecification create(String jobId, String imageName, GitDockerfileImageSource imageSource) {
        return new ImageBuildFromGitJobSpecification(
                registryService,
                manifestGenerator,
                gitService,
                appConfig,

                namespace,
                dockerConfigPath,

                jobId,
                imageName,
                imageSource
        );
    }

}
