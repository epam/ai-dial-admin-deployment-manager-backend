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
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class ImageDefinitionImporter {

    @Qualifier("exportJsonMapper")
    private final JsonMapper exportJsonMapper;
    private final ImageDefinitionService imageDefinitionService;

    @Transactional
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
                    ImageDefinition toUpdate = mergeForOverwrite(imported, existing.get());
                    imageDefinitionService.updateImageDefinition(existing.get().getId(), toUpdate, true);
                    log.debug("Overwrote image definition: type={}, name={}, version={}", type, name, version);
                }
                default -> throw new IllegalArgumentException("Unknown conflict resolution policy '%s'".formatted(policy));
            }
        } else {
            ImageDefinition toCreate = cloneImageDefinition(imported);
            imageDefinitionService.createImageDefinition(toCreate);
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

    private ImageDefinition mergeForOverwrite(ImageDefinition imported, ImageDefinition existing) {
        ImageDefinition copy = cloneImageDefinition(imported);
        copy.setAuthor(existing.getAuthor());
        return copy;
    }

    private ImageDefinition cloneImageDefinition(ImageDefinition source) {
        try {
            Class<? extends ImageDefinition> clazz = source.getClass();
            return exportJsonMapper.readValue(exportJsonMapper.writeValueAsBytes(source), clazz);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to clone image definition '%s' for import".formatted(source.getId()), e);
        }
    }
}
