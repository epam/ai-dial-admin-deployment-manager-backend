package com.epam.aidial.deployment.manager.service.pipeline.step;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.JobRunner;
import com.epam.aidial.deployment.manager.kubernetes.NewLogJobCallback;
import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.service.pipeline.specification.ImageBuildFromGitJobSpecificationFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@LogExecution
@RequiredArgsConstructor
public class BaseImageBuildStep {

    private final ImageDefinitionService imageDefinitionService;
    private final RegistryService registryService;
    private final ImageBuildFromGitJobSpecificationFactory imageBuildFromGitJobSpecificationFactory;
    private final DisposableResourceManager disposableResourceManager;

    private final JobRunner jobRunner;

    public String build(ImageDefinition imageDefinition, GitDockerfileImageSource gitDockerfileImageSource, ResourceLifecycleState resourceLifecycleState) {
        imageDefinitionService.addBuildLog(imageDefinition.getId(), "Image build is started");
        var baseImageJobName = "base-" + imageDefinition.getId();

        var baseImageName = registryService.fullImageName(baseImageJobName, imageDefinition.getVersion());
        disposableResourceManager.saveContainerRegistryResource(baseImageName, imageDefinition.getId(), resourceLifecycleState);

        var isJobSuccessful = jobRunner.run(
                imageBuildFromGitJobSpecificationFactory.create(baseImageJobName,
                        baseImageName,
                        gitDockerfileImageSource,
                        imageDefinition.getImageBuilder()),
                (NewLogJobCallback) logs -> imageDefinitionService.addBuildLogs(imageDefinition.getId(), logs),
                imageDefinition.getId(),
                List.of("init-container", "builder-container", "push-container"),
                imageDefinition.getAllowedDomains()
        );

        if (isJobSuccessful) {
            imageDefinitionService.addBuildLog(imageDefinition.getId(), "Image build is successfully finished");
        } else {
            throw new RuntimeException("Base image build has failed");
        }
        return baseImageName;
    }

}
