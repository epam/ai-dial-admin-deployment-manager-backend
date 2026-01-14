package com.epam.aidial.deployment.manager.service.pipeline.step;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.JobRunner;
import com.epam.aidial.deployment.manager.kubernetes.NewLogJobCallback;
import com.epam.aidial.deployment.manager.model.DistroInfo;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.pipeline.specification.ImageAnalyzerJobSpecificationFactory;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@LogExecution
@RequiredArgsConstructor
public class ImageAnalysisStep {

    @Value("${app.image-build-timeout-sec}")
    private final int imageBuildTimeoutSec;

    private final ImageDefinitionService imageDefinitionService;
    private final ImageAnalyzerJobSpecificationFactory imageAnalyzerJobSpecificationFactory;

    private final JobRunner jobRunner;

    public DistroInfo analyse(ImageDefinition imageDefinition, String imageName) {
        imageDefinitionService.addBuildLog(imageDefinition.getId(), "Image distro analyser is started");
        var analyserImageJobName = "analyser-" + imageDefinition.getId();

        var callback = new NewLogJobCallback() {

            private static final String ID_PREFIX = "ID: ";
            private static final String VERSION_PREFIX = "VERSION: ";

            private String id;
            private String version;

            @Override
            public void onNewLog(List<String> logs) {
                imageDefinitionService.addBuildLogs(imageDefinition.getId(), logs);
                for (var log : logs) {
                    if (log.startsWith(ID_PREFIX)) {
                        id = log.substring(ID_PREFIX.length());
                    } else if (log.startsWith(VERSION_PREFIX)) {
                        version = log.substring(VERSION_PREFIX.length());
                    }
                }
            }

            public DistroInfo getDistroInfo() {
                if (StringUtils.isEmpty(id)) {
                    throw new RuntimeException("Distro id is not found");
                }
                if (StringUtils.isEmpty(version)) {
                    throw new RuntimeException("Distro version is not found");
                }
                return new DistroInfo(id, version);
            }
        };

        var isJobSuccessful = jobRunner.run(
                imageAnalyzerJobSpecificationFactory.create(analyserImageJobName, imageName),
                callback,
                imageBuildTimeoutSec,
                imageDefinition.getId(),
                List.of("builder-container"),
                imageDefinition.getAllowedDomains()
        );
        if (isJobSuccessful) {
            imageDefinitionService.addBuildLog(imageDefinition.getId(), "Image distro analysis is successfully finished");
            return callback.getDistroInfo();
        } else {
            throw new RuntimeException("Image distro analyzation has failed");
        }
    }

}
