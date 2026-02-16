package com.epam.aidial.deployment.manager.mcpregistry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

/**
 * Repository metadata for the MCP server source code (OpenAPI Repository schema).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Repository {

    /**
     * Repository URL for browsing source code.
     */
    private String url;

    /**
     * Repository hosting service identifier (e.g. "github").
     */
    private String source;

    /**
     * Repository identifier from the hosting service.
     */
    @Nullable
    private String id;

    /**
     * Optional relative path from repository root to the server location (e.g. monorepo subfolder).
     */
    @Nullable
    private String subfolder;
}
