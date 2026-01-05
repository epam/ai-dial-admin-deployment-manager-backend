package com.epam.aidial.deployment.manager.web.controller.internal;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.web.dto.internal.DeploymentInternalDto;
import com.epam.aidial.deployment.manager.web.mapper.DeploymentDtoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@LogExecution
@RestController
@RequestMapping("/api/internal/v1/deployments")
@RequiredArgsConstructor
public class DeploymentInternalController {

    private final DeploymentService deploymentService;
    private final DeploymentDtoMapper dtoMapper;

    @GetMapping(path = "/{id}",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public DeploymentInternalDto getDeploymentById(@PathVariable UUID id) {
        return deploymentService.getDeployment(id, false)
                .map(this::toDeploymentInternalDto)
                .orElseThrow(() -> new EntityNotFoundException("Deploy not found by id: %s".formatted(id)));
    }

    private DeploymentInternalDto toDeploymentInternalDto(Deployment deployment) {
        return dtoMapper.toDeploymentInternalDto(deployment);
    }

}
