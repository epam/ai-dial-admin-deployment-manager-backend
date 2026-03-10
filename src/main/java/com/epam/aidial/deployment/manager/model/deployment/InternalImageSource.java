package com.epam.aidial.deployment.manager.model.deployment;

import com.epam.aidial.deployment.manager.model.ImageType;

import java.util.UUID;

public record InternalImageSource(
        UUID imageDefinitionId,
        ImageType imageDefinitionType,
        String imageDefinitionName,
        String imageDefinitionVersion
) implements Source {
}
