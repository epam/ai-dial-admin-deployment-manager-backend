package com.epam.aidial.deployment.manager.model;

public enum DeploymentStatus {
    NOT_DEPLOYED,
    PENDING,
    RUNNING,
    CRASHED,
    STOPPED,
    STOPPING,
    ;

    public boolean isInactive() {
        return this == NOT_DEPLOYED || this == STOPPED;
    }

    public boolean isActive() {
        return this == PENDING || this == RUNNING || this == CRASHED || this == STOPPING;
    }

    public boolean isIntermediate() {
        return this == PENDING || this == STOPPING;
    }

}
