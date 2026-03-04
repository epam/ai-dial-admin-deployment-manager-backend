package com.epam.aidial.deployment.manager.dao.entity.deployment;

public record PersistenceHuggingFaceSource(
        String modelName
) implements PersistenceSource {
}
