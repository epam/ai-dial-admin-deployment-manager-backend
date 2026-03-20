package com.epam.aidial.deployment.manager.service.config;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.GlobalDomainWhitelistNotFoundException;
import com.epam.aidial.deployment.manager.model.AdapterImageDefinition;
import com.epam.aidial.deployment.manager.model.ApplicationImageDefinition;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.config.ExportConfigComponent;
import com.epam.aidial.deployment.manager.model.config.ExportConfigComponentType;
import com.epam.aidial.deployment.manager.model.config.SelectedItemsExportRequest;
import com.epam.aidial.deployment.manager.model.deployment.AdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.ApplicationDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InternalImageSource;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.service.GlobalDomainWhitelistService;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds {@link ExportConfig} from an {@link SelectedItemsExportRequest} by loading selected components
 * and their referenced dependencies.
 */
@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class ConfigExporter {

    private static final String IMAGE_DEFINITION_KEY_FORMAT = "%s(%s)";

    private final ImageDefinitionService imageDefinitionService;
    private final DeploymentService deploymentService;
    private final ExportSanitizer exportSanitizer;
    private final GlobalDomainWhitelistService globalDomainWhitelistService;

    public ExportConfig getConfig(SelectedItemsExportRequest request) {
        ExportConfig config = ExportConfig.builder().build();
        boolean addSecrets = request.isAddSecrets();

        if (request.isAddGlobalImageBuildDomainWhitelist()) {
            addGlobalImageBuildDomainWhitelist(config);
        }
        if (CollectionUtils.isEmpty(request.getComponents())) {
            return config;
        }

        for (ExportConfigComponent component : request.getComponents()) {
            ExportConfigComponentType type = component.getType();
            String name = component.getName();
            if (type == null || StringUtils.isBlank(name)) {
                log.debug("Skipping invalid component; type={}, name={}", type, name);
                continue;
            }
            switch (type) {
                case MCP_IMAGE_DEFINITION, INTERCEPTOR_IMAGE_DEFINITION, ADAPTER_IMAGE_DEFINITION ->
                        addImageDefinition(config, imageDefinitionService.getImageDefinition(UUID.fromString(name)));
                case MCP_DEPLOYMENT, ADAPTER_DEPLOYMENT, INTERCEPTOR_DEPLOYMENT, NIM_DEPLOYMENT, INFERENCE_DEPLOYMENT ->
                        addDeployment(config, deploymentService.getDeployment(name, addSecrets), addSecrets);
                default -> throw new IllegalArgumentException("Unsupported export component type: " + type);
            }
        }

        return config;
    }

    private void addGlobalImageBuildDomainWhitelist(ExportConfig config) {
        try {
            config.getGlobalImageBuildDomainWhitelist().addAll(globalDomainWhitelistService.getDomainWhitelist());
        } catch (Exception e) {
            log.warn("Could not load global image build domain whitelist for export: {}", e.getMessage());
            if (e instanceof GlobalDomainWhitelistNotFoundException) {
                return;
            }
            throw e;
        }
    }

    private void addImageDefinition(ExportConfig config, Optional<ImageDefinition> maybeImageDef) {
        if (maybeImageDef.isEmpty()) {
            return;
        }
        ImageDefinition imageDef = maybeImageDef.get();
        String key = exportKey(imageDef.getName(), imageDef.getVersion());
        switch (imageDef) {
            case McpImageDefinition mcp -> config.getMcpImageDefinitions().put(key, mcp);
            case AdapterImageDefinition a -> config.getAdapterImageDefinitions().put(key, a);
            case InterceptorImageDefinition i -> config.getInterceptorImageDefinitions().put(key, i);
            case ApplicationImageDefinition a -> config.getApplicationImageDefinitions().put(key, a);
            default -> throw new IllegalArgumentException("Unsupported image definition type: " + imageDef.getClass());
        }
    }

    private void addDeployment(ExportConfig config, Optional<Deployment> maybeDeployment, boolean addSecrets) {
        if (maybeDeployment.isEmpty()) {
            return;
        }

        Deployment deployment = maybeDeployment.get();
        addReferencedImageDefinition(config, deployment);

        Deployment sanitized = exportSanitizer.sanitizeDeploymentForExport(deployment, addSecrets);
        populateMetadataEnvsValuesFromDeploymentEnvs(sanitized);
        sanitized.setEnvs(null);

        String key = deployment.getId();

        switch (deployment) {
            case McpDeployment ignored -> config.getMcpDeployments().put(key, (McpDeployment) sanitized);
            case AdapterDeployment ignored -> config.getAdapterDeployments().put(key, (AdapterDeployment) sanitized);
            case InterceptorDeployment ignored -> config.getInterceptorDeployments().put(key, (InterceptorDeployment) sanitized);
            case NimDeployment ignored -> config.getNimDeployments().put(key, (NimDeployment) sanitized);
            case InferenceDeployment ignored -> config.getInferenceDeployments().put(key, (InferenceDeployment) sanitized);
            case ApplicationDeployment ignored -> config.getApplicationDeployments().put(key, (ApplicationDeployment) sanitized);
            default -> throw new IllegalArgumentException("Unsupported deployment type: " + deployment.getClass());
        }
    }

    private static void populateMetadataEnvsValuesFromDeploymentEnvs(Deployment deployment) {
        var metadata = deployment.getMetadata();
        if (metadata == null || metadata.getEnvs() == null) {
            return;
        }

        var deploymentEnvs = deployment.getEnvs();
        if (deploymentEnvs == null) {
            return;
        }

        var envByName = deploymentEnvs.stream()
                .collect(Collectors.toMap(EnvVar::getName, ev -> ev, (a, b) -> a));

        for (EnvVarDefinition envDef : metadata.getEnvs()) {
            if (envDef.getName() != null) {
                EnvVar env = envByName.get(envDef.getName());
                if (env != null) {
                    envDef.setValue(env.getValue());
                }
            }
        }
    }

    private void addReferencedImageDefinition(ExportConfig config, Deployment deployment) {
        if (!(deployment.getSource() instanceof InternalImageSource internalSource)) {
            return;
        }
        if (internalSource.imageDefinitionType() == null
                || internalSource.imageDefinitionName() == null
                || internalSource.imageDefinitionVersion() == null) {
            return;
        }
        var imageDefinition = imageDefinitionService
                .getImageDefinitionByTypeAndNameAndVersion(
                        internalSource.imageDefinitionType(),
                        internalSource.imageDefinitionName(),
                        internalSource.imageDefinitionVersion());
        addImageDefinition(config, imageDefinition);
    }

    private static String exportKey(String name, String version) {
        return IMAGE_DEFINITION_KEY_FORMAT.formatted(name, version);
    }
}
