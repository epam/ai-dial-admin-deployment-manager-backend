package com.epam.aidial.deployment.manager.model;

import org.jetbrains.annotations.Nullable;

public record McpRegistryRef(
        String packageName,
        @Nullable String version
) implements ExternalRegistryRef {
}
