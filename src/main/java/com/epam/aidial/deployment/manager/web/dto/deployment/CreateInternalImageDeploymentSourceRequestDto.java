package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.epam.aidial.deployment.manager.web.dto.ImageTypeDto;
import jakarta.annotation.Nullable;

import java.util.UUID;

public record CreateInternalImageDeploymentSourceRequestDto(
        @Nullable UUID imageDefinitionId,
        @Nullable ImageTypeDto imageDefinitionType,
        @Nullable String imageDefinitionName,
        @Nullable String imageDefinitionVersion
) implements CreateDeploymentSourceRequestDto {
}
