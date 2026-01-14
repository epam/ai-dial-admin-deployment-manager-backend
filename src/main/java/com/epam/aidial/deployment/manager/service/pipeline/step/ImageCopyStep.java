package com.epam.aidial.deployment.manager.service.pipeline.step;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.JobRunner;
import com.epam.aidial.deployment.manager.kubernetes.NewLogJobCallback;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.service.pipeline.specification.ImageCopyJobSpecificationFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@LogExecution
@RequiredArgsConstructor
public class ImageCopyStep {

    @Value("${app.image-build-timeout-sec}")
    private final int imageBuildTimeoutSec;

    private final RegistryService registryService;
    private final ImageDefinitionService imageDefinitionService;
    private final ImageCopyJobSpecificationFactory imageCopyJobSpecificationFactory;
    private final DisposableResourceManager disposableResourceManager;

    private final JobRunner jobRunner;

    public String copy(ImageDefinition imageDefinition, String sourceImageName) {
        imageDefinitionService.addBuildLog(imageDefinition.getId(), "Image copying has started");
        var jobId = "copy-" + imageDefinition.getId();
        var targetImageName = registryService.fullImageName(jobId, imageDefinition.getVersion());

        disposableResourceManager.saveContainerRegistryResource(targetImageName, imageDefinition.getId(), ResourceLifecycleState.STABLE);

        var isJobSuccessful = jobRunner.run(
                imageCopyJobSpecificationFactory.create(jobId, sourceImageName, targetImageName),
                (NewLogJobCallback) logs -> imageDefinitionService.addBuildLogs(imageDefinition.getId(), logs),
                imageBuildTimeoutSec,
                imageDefinition.getId(),
                List.of("builder-container"),
                imageDefinition.getAllowedDomains()
        );

        if (isJobSuccessful) {
            imageDefinitionService.addBuildLog(imageDefinition.getId(), "Image is successfully copied into internal registry");
        } else {
            throw new RuntimeException("Image copying has failed");
        }

        return targetImageName;
    }

}
