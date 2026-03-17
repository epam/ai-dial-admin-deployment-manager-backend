package com.epam.aidial.deployment.manager.dao.entity;

public record PersistenceGenericRef(
        String url
) implements PersistenceExternalRegistryRef {
}
