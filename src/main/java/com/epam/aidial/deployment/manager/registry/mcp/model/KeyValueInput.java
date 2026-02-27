package com.epam.aidial.deployment.manager.registry.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

/**
 * Name/value input for headers or environment variables (OpenAPI KeyValueInput schema).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeyValueInput {

    /**
     * Name of the header or environment variable.
     */
    private String name;

    @Nullable
    private String value;
    @Nullable
    private String description;
    @Nullable
    private Boolean isRequired;
    @Nullable
    private String placeholder;
    @Nullable
    private Boolean isSecret;
}
