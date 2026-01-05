package com.epam.aidial.deployment.manager.dao.entity;

public record PersistenceEnvVarDefinition(
        String name,
        PersistenceEnvVarMountType mountType,
        String description
) {
}
