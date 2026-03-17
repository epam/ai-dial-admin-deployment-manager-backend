package com.epam.aidial.deployment.manager.dao.entity;

public record PersistenceGitHubRef(
        String repo
) implements PersistenceExternalRegistryRef {
}
