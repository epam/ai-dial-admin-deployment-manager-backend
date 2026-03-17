package com.epam.aidial.deployment.manager.service.config.previews;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.config.ImportAction;
import com.epam.aidial.deployment.manager.model.config.ImportComponent;
import com.epam.aidial.deployment.manager.model.config.ImportConfigPreview;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
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
public class DeploymentImportPreviewer {

    private final DeploymentService deploymentService;

    public void previewDeployments(ExportConfig config, ConflictResolutionPolicy policy, ImportConfigPreview preview) {
        preview.getMcpDeployments().addAll(previewMap(config.getMcpDeployments(), policy));
        preview.getAdapterDeployments().addAll(previewMap(config.getAdapterDeployments(), policy));
        preview.getInterceptorDeployments().addAll(previewMap(config.getInterceptorDeployments(), policy));
        preview.getNimDeployments().addAll(previewMap(config.getNimDeployments(), policy));
        preview.getInferenceDeployments().addAll(previewMap(config.getInferenceDeployments(), policy));
    }

    private <T extends Deployment> List<ImportComponent<T>> previewMap(Map<String, T> map, ConflictResolutionPolicy policy) {
        if (map == null) {
            return List.of();
        }
        return map.values().stream()
                .map(imported -> previewOne(imported, policy))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private <T extends Deployment> ImportComponent<T> previewOne(T imported, ConflictResolutionPolicy policy) {
        String id = imported.getId();
        Optional<Deployment> existingOpt = deploymentService.getDeployment(id, false);

        if (existingOpt.isEmpty()) {
            return new ImportComponent<>(ImportAction.CREATE, null, imported);
        }
        // Safe cast: service returns the deployment of the same concrete type identified by id
        T existing = (T) existingOpt.get();
        return switch (policy) {
            case FAIL_IF_EXISTS -> new ImportComponent<>(ImportAction.FAIL, existing, imported);
            case SKIP_IF_EXISTS -> new ImportComponent<>(ImportAction.SKIP, existing, null);
            case OVERWRITE -> new ImportComponent<>(ImportAction.UPDATE, existing, imported);
            default -> throw new IllegalArgumentException("Unknown conflict resolution policy '%s'".formatted(policy));
        };
    }
}
