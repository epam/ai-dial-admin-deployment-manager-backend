package com.epam.aidial.deployment.manager.configuration.export;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

/**
 * Mix-in for InternalImageSource export. Excludes imageDefinitionId from exported JSON.
 */
public abstract class InternalImageSourceExportMixIn {

    @JsonIgnore
    abstract UUID imageDefinitionId();
}
