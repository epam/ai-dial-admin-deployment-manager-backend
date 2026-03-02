package com.epam.aidial.deployment.manager.registry.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerListMetadata {

    /**
     * Pagination cursor for the next page of results.
     */
    @Nullable
    private String nextCursor;

    /**
     * Number of items in current page.
     */
    @Nullable
    private Integer count;
}
