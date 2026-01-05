package com.epam.aidial.deployment.manager.model.deployment;

public record NimDeploymentNgcRegistrySource(
        String imageRef
) implements NimDeploymentSource {
}
