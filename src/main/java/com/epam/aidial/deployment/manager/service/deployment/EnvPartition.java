package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.model.EnvVarValue;

import java.util.Map;

public record EnvPartition(
        Map<String, EnvVarValue> sensitive,
        Map<String, EnvVarValue> nonSensitive,
        Map<String, EnvVarValue> sensitiveFile
) {
}