package com.epam.aidial.deployment.manager.service.config.imports;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityAlreadyExistsException;
import com.epam.aidial.deployment.manager.mapper.DeploymentMapper;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.service.nodepool.NodePoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class DeploymentImporter {

    private final DeploymentService deploymentService;
    private final DeploymentMapper deploymentMapper;
    private final NodePoolService nodePoolService;

    public void importDeployments(ExportConfig config, ConflictResolutionPolicy policy) {
        importMap(config.getMcpDeployments(), policy);
        importMap(config.getAdapterDeployments(), policy);
        importMap(config.getApplicationDeployments(), policy);
        importMap(config.getInterceptorDeployments(), policy);
        importMap(config.getNimDeployments(), policy);
        importMap(config.getInferenceDeployments(), policy);
    }

    private void importMap(Map<String, ? extends Deployment> map, ConflictResolutionPolicy policy) {
        if (map == null) {
            return;
        }
        for (Map.Entry<String, ? extends Deployment> entry : map.entrySet()) {
            importOne(entry.getValue(), policy);
        }
    }

    private void importOne(Deployment imported, ConflictResolutionPolicy policy) {
        String id = imported.getId();
        var existing = deploymentService.getDeployment(id, false);

        if (existing.isPresent()) {
            switch (policy) {
                case FAIL_IF_EXISTS -> throw new EntityAlreadyExistsException("Deployment already exists: " + id);
                case SKIP_IF_EXISTS -> log.debug("Skipping existing deployment: {}", id);
                case OVERWRITE -> {
                    CreateDeployment createRequest = deploymentMapper.toCreateDeployment(imported);
                    // FR-021: imports run through the target environment's cascade. The export DTO does
                    // not carry nodePoolId, and updateDeployment is PUT-style without an internal cascade,
                    // so the importer applies the cascade explicitly before the update.
                    //
                    // The null guard is defensive: today DeploymentMapper.toCreateDeployment(Deployment)
                    // does not pass nodePoolId through from the domain object, so in production this
                    // branch is unreachable with a non-null id. If that mapper later starts passing the
                    // field through, the guard ensures the imported id wins over the cascade — making
                    // that change a deliberate decision rather than a silent regression of FR-021.
                    if (createRequest.getNodePoolId() == null) {
                        createRequest.setNodePoolId(nodePoolService.resolveForCreate(createRequest));
                    }
                    deploymentService.updateDeployment(id, createRequest);
                    log.debug("Overwrote deployment: {}", id);
                }
                default -> throw new IllegalArgumentException("Unknown conflict resolution policy '%s'".formatted(policy));
            }
        } else {
            CreateDeployment createRequest = deploymentMapper.toCreateDeployment(imported);
            deploymentService.createDeployment(createRequest);
            log.debug("Created deployment: {}", id);
        }
    }
}
