package com.epam.aidial.deployment.manager.model;

public record SimpleEnvVarValue(
        String value
) implements EnvVarValue {
    @Override
    public String getValue() {
        return value;
    }
}