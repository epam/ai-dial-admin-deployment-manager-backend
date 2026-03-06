package com.epam.aidial.deployment.manager.registry.mcp.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServersRequestDto {

    /**
     * Pagination cursor for retrieving next set of results.
     */
    @Nullable
    private String cursor;

    /**
     * Maximum number of items to return.
     */
    @Nullable
    private Integer limit;

    /**
     * Search servers by name (substring match).
     */
    @Nullable
    private String search;

    /**
     * Filter servers updated since timestamp (RFC3339 datetime).
     */
    @Nullable
    private String updatedSince;

    /**
     * Filter by version ('latest' for latest version, or an exact version like '1.2.3').
     */
    @Nullable
    private String version;
}
