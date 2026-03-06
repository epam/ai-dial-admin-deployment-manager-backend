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
        int imageDefCount = countImageDefinitions(config);
        int deploymentCount = countDeployments(config);
        log.info("Config import started: {} image definitions, {} deployments (resolutionPolicy={})",
                imageDefCount, deploymentCount, resolutionPolicy);
        imageDefinitionImporter.importImageDefinitions(config, resolutionPolicy);
        deploymentImporter.importDeployments(config, resolutionPolicy);
        globalDomainWhitelistImporter.importGlobalDomainWhitelist(config.getGlobalImageBuildDomainWhitelist(), resolutionPolicy);
    }

    private static int countImageDefinitions(ExportConfig config) {
        return config.getMcpImageDefinitions().size()
                + config.getAdapterImageDefinitions().size()
                + config.getInterceptorImageDefinitions().size();
    }

    private static int countDeployments(ExportConfig config) {
        return config.getMcpDeployments().size()
                + config.getAdapterDeployments().size()
                + config.getInterceptorDeployments().size()
                + config.getNimDeployments().size()
                + config.getInferenceDeployments().size();
    }
}
