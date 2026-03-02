package com.epam.aidial.deployment.manager.web.dto.deployment;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateInternalImageDeploymentSourceRequestDto(
        @NotNull UUID imageDefinitionId
) implements CreateDeploymentSourceRequestDto {
}
