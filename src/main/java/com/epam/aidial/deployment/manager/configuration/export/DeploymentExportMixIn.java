package com.epam.aidial.deployment.manager.configuration.export;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.UUID;

/**
 * Mix-in for Deployment export. Fields which have getters marked with @JsonIgnore will be excluded from export.
 */
public abstract class DeploymentExportMixIn {

    @JsonIgnore
    abstract String getUrl();

    @JsonIgnore
    abstract Object getStatus();

    @JsonIgnore
    abstract String getServiceName();

    @JsonIgnore
    abstract String getAuthor();

    @JsonIgnore
    abstract Instant getCreatedAt();

    @JsonIgnore
    abstract Instant getUpdatedAt();

    @JsonIgnore
    abstract UUID getImageDefinitionId();

    // FR-021: nodePoolId is environment-specific configuration; export must NOT include it, and
    // import flows ignore any incoming value (silently dropped on read).
    @JsonIgnore
    abstract String getNodePoolId();
}
