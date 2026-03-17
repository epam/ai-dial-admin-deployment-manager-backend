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

/**
 * Builds import preview components for deployments by comparing incoming
 * (deserialized from export ZIP) entities against existing DB state.
 *
 * <p><b>Known limitation — {@code next} differs from actual import result.</b>
 * The {@code next} value in each {@link ImportComponent} is the raw deserialized
 * object. Fields excluded by export mixins are {@code null} in {@code next}, whereas
 * the real import pipeline populates them:
 * <ul>
 *   <li>{@code DeploymentExportMixIn}-excluded: {@code url}, {@code status},
 *       {@code serviceName}, {@code author}, {@code createdAt}, {@code updatedAt}.</li>
 *   <li>{@code InternalImageSourceExportMixIn}-excluded: {@code imageDefinitionId}
 *       — resolved from DB by (type, name, version) during real import.</li>
 *   <li>{@code SensitiveEnvVarExportMixIn}-excluded: {@code k8sSecretName},
 *       {@code k8sSecretKey} — provisioned as new K8s secrets during real import.</li>
 * </ul>
 * Additionally, during real import:
 * <ul>
 *   <li><b>CREATE</b>: {@code status} → {@code NOT_DEPLOYED}, {@code author} →
 *       current user, env var values nullified in DB after K8s secret provisioning.</li>
 *   <li><b>UPDATE</b>: {@code status} derived from existing (e.g. STOPPED → NOT_DEPLOYED),
 *       {@code url}/{@code serviceName} preserved from existing entity, may trigger
 *       K8s rolling update or Cilium policy changes.</li>
 * </ul>
 */
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
