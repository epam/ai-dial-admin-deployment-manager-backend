package com.epam.aidial.deployment.manager.dao.entity.deployment;

public record PersistenceImageReferenceSource(
        String imageReference
) implements PersistenceSource {
}
