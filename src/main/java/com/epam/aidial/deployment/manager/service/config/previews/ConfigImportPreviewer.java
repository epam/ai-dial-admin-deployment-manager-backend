package com.epam.aidial.deployment.manager.service.config.previews;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.config.ImportComponent;
import com.epam.aidial.deployment.manager.model.config.ImportConfigPreview;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class ConfigImportPreviewer {

    private final GlobalDomainWhitelistImportPreviewer globalDomainWhitelistImportPreviewer;
    private final ImageDefinitionImportPreviewer imageDefinitionImportPreviewer;
    private final DeploymentImportPreviewer deploymentImportPreviewer;

    public ImportConfigPreview previewImport(ExportConfig config, ConflictResolutionPolicy policy) {
        ImportConfigPreview preview = ImportConfigPreview.builder().build();
        imageDefinitionImportPreviewer.previewImageDefinitions(config, policy, preview);
        deploymentImportPreviewer.previewDeployments(config, policy, preview);
        ImportComponent<List<String>> whitelistComponent =
                globalDomainWhitelistImportPreviewer.previewGlobalDomainWhitelist(
                        config.getGlobalImageBuildDomainWhitelist(), policy);
        preview.setGlobalImageBuildDomainWhitelist(whitelistComponent);
        return preview;
    }
}
