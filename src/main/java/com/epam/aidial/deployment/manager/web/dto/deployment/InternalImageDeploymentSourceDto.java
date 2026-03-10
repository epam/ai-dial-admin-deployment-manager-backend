package com.epam.aidial.deployment.manager.web.dto.deployment;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InternalImageDeploymentSourceDto(
        @NotNull UUID imageDefinitionId,
        @NotNull String imageDefinitionName,
        @NotNull String imageDefinitionVersion
) implements DeploymentSourceDto {
}
