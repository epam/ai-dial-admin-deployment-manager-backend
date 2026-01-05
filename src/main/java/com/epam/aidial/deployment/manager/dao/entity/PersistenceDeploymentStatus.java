package com.epam.aidial.deployment.manager.dao.entity;

public enum PersistenceDeploymentStatus {
    NOT_DEPLOYED,
    PENDING,
    RUNNING,
    CRASHED,
    STOPPED,
    STOPPING,
}