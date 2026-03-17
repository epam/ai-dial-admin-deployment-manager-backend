package com.epam.aidial.deployment.manager.service.config.previews;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.AdapterImageDefinition;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.config.ImportAction;
import com.epam.aidial.deployment.manager.model.config.ImportComponent;
import com.epam.aidial.deployment.manager.model.config.ImportConfigPreview;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class ImageDefinitionImportPreviewer {

    private final ImageDefinitionService imageDefinitionService;

    public void previewImageDefinitions(ExportConfig config, ConflictResolutionPolicy policy, ImportConfigPreview preview) {
        preview.getMcpImageDefinitions().addAll(previewMap(config.getMcpImageDefinitions(), policy));
        preview.getAdapterImageDefinitions().addAll(previewMap(config.getAdapterImageDefinitions(), policy));
        preview.getInterceptorImageDefinitions().addAll(previewMap(config.getInterceptorImageDefinitions(), policy));
    }

    private <T extends ImageDefinition> List<ImportComponent<T>> previewMap(Map<String, T> map, ConflictResolutionPolicy policy) {
        if (map == null) {
            return List.of();
        }
        return map.values().stream()
                .map(imported -> previewOne(imported, policy))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private <T extends ImageDefinition> ImportComponent<T> previewOne(T imported, ConflictResolutionPolicy policy) {
        String name = imported.getName();
        String version = imported.getVersion();
        ImageType type = imageTypeOf(imported);

        Optional<ImageDefinition> existingOpt = imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(type, name, version);

        if (existingOpt.isEmpty()) {
            return new ImportComponent<>(ImportAction.CREATE, null, imported);
        }
        // Safe cast: service returns the same concrete subtype queried by ImageType
        T existing = (T) existingOpt.get();
        return switch (policy) {
            case FAIL_IF_EXISTS -> new ImportComponent<>(ImportAction.FAIL, existing, imported);
            case SKIP_IF_EXISTS -> new ImportComponent<>(ImportAction.SKIP, existing, null);
            case OVERWRITE -> new ImportComponent<>(ImportAction.UPDATE, existing, imported);
            default -> throw new IllegalArgumentException("Unknown conflict resolution policy '%s'".formatted(policy));
        };
    }

    private static ImageType imageTypeOf(ImageDefinition def) {
        return switch (def) {
            case McpImageDefinition ignored -> ImageType.MCP;
            case AdapterImageDefinition ignored -> ImageType.ADAPTER;
            case InterceptorImageDefinition ignored -> ImageType.INTERCEPTOR;
            default -> throw new IllegalArgumentException("Unsupported image definition type: " + def.getClass().getName());
        };
    }
}
