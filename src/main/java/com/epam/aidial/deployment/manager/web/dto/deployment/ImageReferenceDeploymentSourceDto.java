package com.epam.aidial.deployment.manager.web.dto.deployment;

import org.jetbrains.annotations.NotNull;

public record ImageReferenceDeploymentSourceDto(
        @NotNull String imageReference
) implements DeploymentSourceDto {
}
