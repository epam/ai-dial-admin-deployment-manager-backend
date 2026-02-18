package com.epam.aidial.deployment.manager.service.config;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.service.config.imports.DeploymentImporter;
import com.epam.aidial.deployment.manager.service.config.imports.GlobalDomainWhitelistImporter;
import com.epam.aidial.deployment.manager.service.config.imports.ImageDefinitionImporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports {@link ExportConfig} into the current environment. Order: (1) image definitions,
 * (2) deployments, (3) global domain whitelist.
 */
@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class ConfigImporter {

    private final ImageDefinitionImporter imageDefinitionImporter;
    private final DeploymentImporter deploymentImporter;
    private final GlobalDomainWhitelistImporter globalDomainWhitelistImporter;

    @Transactional
    public void importConfig(ExportConfig config, ConflictResolutionPolicy resolutionPolicy) {
        log.debug("Importing config; resolutionPolicy={}", resolutionPolicy);
        imageDefinitionImporter.importImageDefinitions(config, resolutionPolicy);
        deploymentImporter.importDeployments(config, resolutionPolicy);
        globalDomainWhitelistImporter.importGlobalDomainWhitelist(config.getGlobalDomainWhitelist(), resolutionPolicy);
    }
}
