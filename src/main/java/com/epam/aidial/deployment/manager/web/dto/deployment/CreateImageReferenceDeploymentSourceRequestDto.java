package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.epam.aidial.deployment.manager.web.dto.ExternalRegistryRefDto;
import com.epam.aidial.deployment.manager.web.validation.ValidDockerImageName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;

public record CreateImageReferenceDeploymentSourceRequestDto(
        @NotNull
        @ValidDockerImageName
        String imageReference,
        @Nullable @Valid ExternalRegistryRefDto externalRegistryRef
) implements CreateDeploymentSourceRequestDto {
}
