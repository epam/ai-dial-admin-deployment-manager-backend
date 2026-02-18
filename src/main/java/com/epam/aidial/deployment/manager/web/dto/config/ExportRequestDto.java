package com.epam.aidial.deployment.manager.web.dto.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 * Request for config export. More implementation types will be added in the future.
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "$type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SelectedItemsExportRequestDto.class, name = "custom")
})
public abstract class ExportRequestDto {
    private boolean addSecrets;
    private boolean addGlobalImageBuildDomainWhitelist;
}
