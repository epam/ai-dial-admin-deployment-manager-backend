package com.epam.aidial.deployment.manager.model;

public record McpRegistryRef(
        String packageName
) implements ExternalRegistryRef {
}
