package com.epam.aidial.deployment.manager.service.config.imports;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityAlreadyExistsException;
import com.epam.aidial.deployment.manager.mapper.DeploymentMapper;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
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

    public void importDeployments(ExportConfig config, ConflictResolutionPolicy policy) {
        importMap(config.getMcpDeployments(), policy);
        importMap(config.getAdapterDeployments(), policy);
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
