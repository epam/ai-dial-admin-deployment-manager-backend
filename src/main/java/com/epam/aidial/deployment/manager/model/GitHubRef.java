package com.epam.aidial.deployment.manager.model;

public record GitHubRef(
        String repo
) implements ExternalRegistryRef {
}
