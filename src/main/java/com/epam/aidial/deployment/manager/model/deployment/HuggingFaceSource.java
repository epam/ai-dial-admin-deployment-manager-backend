package com.epam.aidial.deployment.manager.model.deployment;

public record HuggingFaceSource(
        String modelName
) implements Source {
    public String getStorageUri() {
        return "hf://" + modelName;
    }
}
