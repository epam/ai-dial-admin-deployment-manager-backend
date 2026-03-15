package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.epam.aidial.deployment.manager.web.dto.ExternalRegistryRefDto;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ImageReferenceDeploymentSourceDto(
        @NotNull String imageReference,
        @Nullable @Valid ExternalRegistryRefDto externalRegistryRef
) implements DeploymentSourceDto {
}
