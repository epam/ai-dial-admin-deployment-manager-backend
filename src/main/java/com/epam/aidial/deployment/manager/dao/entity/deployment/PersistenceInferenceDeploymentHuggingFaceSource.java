package com.epam.aidial.deployment.manager.dao.entity.deployment;

public record PersistenceInferenceDeploymentHuggingFaceSource(
        String modelName
) implements PersistenceInferenceDeploymentSource {
}
