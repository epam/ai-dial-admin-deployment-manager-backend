package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.epam.aidial.deployment.manager.web.dto.ImageTypeDto;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.AssertTrue;
import org.apache.commons.lang3.StringUtils;

import java.util.UUID;

public record CreateInternalImageDeploymentSourceRequestDto(
        @Nullable UUID imageDefinitionId,
        @Nullable ImageTypeDto imageDefinitionType,
        @Nullable String imageDefinitionName,
        @Nullable String imageDefinitionVersion
) implements CreateDeploymentSourceRequestDto {

    @AssertTrue(message = "Either imageDefinitionId or (imageDefinitionType, imageDefinitionName, imageDefinitionVersion) must be set")
    public boolean isValidImageReference() {
        boolean hasId = imageDefinitionId != null;
        boolean hasTypeNameVersion = imageDefinitionType != null
                && StringUtils.isNotBlank(imageDefinitionName)
                && StringUtils.isNotBlank(imageDefinitionVersion);
        return hasId || hasTypeNameVersion;
    }
}
