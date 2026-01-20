package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DeploymentImageChangeRequestDto(
        @NotNull String imageDefinitionName,
        @NotNull @NotEmpty List<String> deployments
) {
}
