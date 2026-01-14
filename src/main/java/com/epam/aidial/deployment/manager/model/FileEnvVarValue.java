package com.epam.aidial.deployment.manager.model;

public record FileEnvVarValue(
        String fileName,
        String fileContent
) implements EnvVarValue {
    @Override
    public String getValue() {
        return fileContent;
    }
}