package com.epam.aidial.deployment.manager.model.deployment;

public record InferenceDeploymentHuggingFaceSource(
        String modelName
) implements InferenceDeploymentSource {
    @Override
    public String getStorageUri() {
        return "hf://" + modelName;
    }
}
