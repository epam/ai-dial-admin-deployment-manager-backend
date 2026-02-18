package com.epam.aidial.deployment.manager.service.pipeline.step;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.JobRunner;
import com.epam.aidial.deployment.manager.kubernetes.NewLogJobCallback;
import com.epam.aidial.deployment.manager.model.DistroInfo;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.service.pipeline.specification.ImageWrapperBuildJobSpecificationFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@LogExecution
@RequiredArgsConstructor
public class WrapperImageBuildStep {

    @Value("${app.image-build-timeout-sec}")
    private final int imageBuildTimeoutSec;

    private final ImageDefinitionService imageDefinitionService;
    private final RegistryService registryService;
    private final ImageWrapperBuildJobSpecificationFactory imageWrapperBuildJobSpecificationFactory;
    private final DisposableResourceManager disposableResourceManager;

    private final JobRunner jobRunner;

    public String build(ImageDefinition imageDefinition, List<String> entrypoint, String baseImageName, DistroInfo distryInfo) {
        imageDefinitionService.addBuildLog(imageDefinition.getId(), "Wrapper image build is started");

        var wrapperImageJobName = "wrapper-" + imageDefinition.getId();
        var wrapperImageName = registryService.fullImageName(wrapperImageJobName, imageDefinition.getVersion());
        var dockerImageSource = new DockerImageSource(baseImageName, entrypoint);

        disposableResourceManager.saveContainerRegistryResource(wrapperImageName, imageDefinition.getId(), ResourceLifecycleState.STABLE);

        var isJobSuccessful = jobRunner.run(
                imageWrapperBuildJobSpecificationFactory.create(wrapperImageJobName,
                        wrapperImageName,
                        dockerImageSource,
                        distryInfo,
                        imageDefinition.getImageBuilder()),
                (NewLogJobCallback) logs -> imageDefinitionService.addBuildLogs(imageDefinition.getId(), logs),
                imageBuildTimeoutSec,
                imageDefinition.getId(),
                List.of("builder-container"),
                imageDefinition.getAllowedDomains()
        );

        if (isJobSuccessful) {
            imageDefinitionService.addBuildLog(imageDefinition.getId(), "Wrapper image is successfully built");
        } else {
            throw new RuntimeException("Wrapper image build has failed");
        }

        return wrapperImageName;
    }

}
