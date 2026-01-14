package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DuplicateDeploymentRequestDto(
        @NotNull String sourceDeploymentName,
        @Pattern(regexp = "^[a-z0-9-]+$", message = "New deployment ID must contain only lowercase Latin letters, numbers, and hyphens")
        @NotNull @Size(max = 36) String newDeploymentName,
        @NotNull String newDeploymentDisplayName
) {
}
