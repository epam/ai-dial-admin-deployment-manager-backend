package com.epam.aidial.deployment.manager.dao.entity.deployment;

public record PersistenceNgcRegistrySource(
        String imageRef
) implements PersistenceSource {
}
