package com.epam.aidial.deployment.manager.dao.entity.deployment;

public record PersistenceNimDeploymentNgcRegistrySource(
        String imageRef
) implements PersistenceNimDeploymentSource {
}
