package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record DeploymentImageChangeRequestDto(
        @NotNull UUID imageDefinitionId,
        @NotNull @NotEmpty List<UUID> deployments
) {
}
