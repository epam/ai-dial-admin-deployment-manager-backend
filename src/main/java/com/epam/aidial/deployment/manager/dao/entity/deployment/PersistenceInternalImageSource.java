package com.epam.aidial.deployment.manager.dao.entity.deployment;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceImageType;

import java.util.UUID;

public record PersistenceInternalImageSource(
        UUID imageDefinitionId,
        PersistenceImageType imageDefinitionType,
        String imageDefinitionName,
        String imageDefinitionVersion
) implements PersistenceSource {
}
