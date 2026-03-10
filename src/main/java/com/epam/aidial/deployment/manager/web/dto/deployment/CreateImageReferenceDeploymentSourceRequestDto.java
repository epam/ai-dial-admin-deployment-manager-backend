package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.epam.aidial.deployment.manager.web.validation.ValidDockerImageName;
import jakarta.validation.constraints.NotNull;

public record CreateImageReferenceDeploymentSourceRequestDto(
        @NotNull
        @ValidDockerImageName
        String imageReference
) implements CreateDeploymentSourceRequestDto {
}
