package com.epam.aidial.deployment.manager.dao.entity;

import java.util.List;

public record PersistenceDeploymentMetadata(List<PersistenceEnvVarDefinition> envs) {
}
