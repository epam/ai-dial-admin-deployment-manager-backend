package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
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
public class ImageCopyJobSpecificationFactory {

    private final RegistryService registryService;
    private final ManifestGenerator manifestGenerator;
    private final AppProperties appConfig;

    @Value("${app.build-namespace}")
    private final String namespace;

    public JobSpecification create(String jobId, String sourceImageName, String targetImageName) {
        return new ImageCopyJobSpecification(
                registryService,
                manifestGenerator,
                appConfig,

                namespace,

                jobId,
                sourceImageName,
                targetImageName
        );
    }

}
