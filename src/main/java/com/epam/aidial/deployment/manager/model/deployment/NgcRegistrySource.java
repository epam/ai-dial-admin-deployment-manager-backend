package com.epam.aidial.deployment.manager.model.deployment;

public record NgcRegistrySource(
        String imageRef
) implements Source {
}
