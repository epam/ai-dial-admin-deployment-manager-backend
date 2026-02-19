package com.epam.aidial.deployment.manager.mcpregistry.web.dto;

import com.epam.aidial.deployment.manager.mcpregistry.model.ServerDetail;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerResponseDto {

    /**
     * Server detail (name, description, version, packages, etc.).
     */
    private ServerDetail server;

    /**
     * Registry-managed metadata (e.g. publishedAt, isLatest).
     */
    @Nullable
    @JsonProperty("_meta")
    private Map<String, Object> meta;
}
