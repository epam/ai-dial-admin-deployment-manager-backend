package com.epam.aidial.deployment.manager.model;

public record McpRegistryRef(
        String packageName,
        String version
) implements ExternalRegistryRef {
}
