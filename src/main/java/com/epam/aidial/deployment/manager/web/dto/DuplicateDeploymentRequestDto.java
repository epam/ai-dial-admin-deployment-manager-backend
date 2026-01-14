package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DuplicateDeploymentRequestDto(
        @NotNull UUID sourceDeploymentId,
        @NotNull String newDeploymentName
) {
}
