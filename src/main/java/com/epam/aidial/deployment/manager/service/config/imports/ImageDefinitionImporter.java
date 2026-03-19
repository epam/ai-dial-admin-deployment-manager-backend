package com.epam.aidial.deployment.manager.service.config.imports;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.AdapterImageDefinition;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class ImageDefinitionImporter {

    private final ImageDefinitionService imageDefinitionService;

    public void importImageDefinitions(ExportConfig config, ConflictResolutionPolicy policy) {
        importMap(config.getMcpImageDefinitions(), policy);
        importMap(config.getAdapterImageDefinitions(), policy);
        importMap(config.getInterceptorImageDefinitions(), policy);
    }

    private void importMap(Map<String, ? extends ImageDefinition> map, ConflictResolutionPolicy policy) {
        if (map == null) {
            return;
        }
        for (Map.Entry<String, ? extends ImageDefinition> entry : map.entrySet()) {
            importOne(entry.getValue(), policy);
        }
    }

    private void importOne(ImageDefinition imported, ConflictResolutionPolicy policy) {
        String name = imported.getName();
        String version = imported.getVersion();
        ImageType type = imageTypeOf(imported);

        Optional<ImageDefinition> existing = imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(type, name, version);

        if (existing.isPresent()) {
            switch (policy) {
                case FAIL_IF_EXISTS -> throw new IllegalStateException(
                        "Image definition already exists: type=%s, name=%s, version=%s".formatted(type, name, version));
                case SKIP_IF_EXISTS -> log.debug("Skipping existing image definition: type={}, name={}, version={}", type, name, version);
                case OVERWRITE -> {
                    imported.setAuthor(existing.get().getAuthor());
                    imageDefinitionService.updateImageDefinition(existing.get().getId(), imported, true);
                    log.debug("Overwrote image definition: type={}, name={}, version={}", type, name, version);
                }
                default -> throw new IllegalArgumentException("Unknown conflict resolution policy '%s'".formatted(policy));
            }
        } else {
            imageDefinitionService.createImageDefinition(imported);
            log.debug("Created image definition: type={}, name={}, version={}", type, name, version);
        }
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
