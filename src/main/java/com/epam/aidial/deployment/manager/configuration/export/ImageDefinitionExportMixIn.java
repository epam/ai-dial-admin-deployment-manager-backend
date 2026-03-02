package com.epam.aidial.deployment.manager.configuration.export;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mix-in for Image Definition export. Fields which have getters marked with @JsonIgnore will be excluded from export.
 */
public abstract class ImageDefinitionExportMixIn {

    @JsonIgnore
    abstract UUID getId();

    @JsonIgnore
    abstract String getImageName();

    @JsonIgnore
    abstract Object getBuildStatus();

    @JsonIgnore
    abstract List<String> getBuildLogs();

    @JsonIgnore
    abstract Instant getBuiltAt();

    @JsonIgnore
    abstract String getAuthor();

    @JsonIgnore
    abstract Instant getCreatedAt();

    @JsonIgnore
    abstract Instant getUpdatedAt();
}
