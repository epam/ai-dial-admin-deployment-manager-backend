package com.epam.aidial.deployment.manager.dao.entity;

public record PersistenceMcpRegistryRef(
        String packageName
) implements PersistenceExternalRegistryRef {
}
