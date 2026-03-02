package com.epam.aidial.deployment.manager.service.config;

import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.service.config.imports.DeploymentImporter;
import com.epam.aidial.deployment.manager.service.config.imports.GlobalDomainWhitelistImporter;
import com.epam.aidial.deployment.manager.service.config.imports.ImageDefinitionImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConfigImporterTest {

    @Mock
    private ImageDefinitionImporter imageDefinitionImporter;
    @Mock
    private DeploymentImporter deploymentImporter;
    @Mock
    private GlobalDomainWhitelistImporter globalDomainWhitelistImporter;

    @InjectMocks
    private ConfigImporter configImporter;

    @Test
    void importConfig_callsImageDefinitionThenDeploymentThenWhitelist() {
        ExportConfig config = ExportConfig.builder().build();
        ConflictResolutionPolicy policy = ConflictResolutionPolicy.OVERWRITE;

        configImporter.importConfig(config, policy);

        InOrder order = inOrder(imageDefinitionImporter, deploymentImporter, globalDomainWhitelistImporter);
        order.verify(imageDefinitionImporter).importImageDefinitions(same(config), same(policy));
        order.verify(deploymentImporter).importDeployments(same(config), same(policy));
        order.verify(globalDomainWhitelistImporter).importGlobalDomainWhitelist(eq(config.getGlobalImageBuildDomainWhitelist()), same(policy));
    }

    @Test
    void importConfig_passesResolutionPolicyToAllImporters() {
        ExportConfig config = ExportConfig.builder().build();
        ConflictResolutionPolicy policy = ConflictResolutionPolicy.SKIP_IF_EXISTS;

        configImporter.importConfig(config, policy);

        verify(imageDefinitionImporter).importImageDefinitions(config, policy);
        verify(deploymentImporter).importDeployments(config, policy);
        verify(globalDomainWhitelistImporter).importGlobalDomainWhitelist(config.getGlobalImageBuildDomainWhitelist(), policy);
    }
}
