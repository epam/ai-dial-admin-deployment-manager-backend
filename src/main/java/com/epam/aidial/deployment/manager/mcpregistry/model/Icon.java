package com.epam.aidial.deployment.manager.mcpregistry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Optional sized icon for the MCP server (OpenAPI Icon schema).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Icon {

    /**
     * URI pointing to the icon resource (HTTPS).
     */
    private String src;

    /**
     * Optional MIME type override (image/png, image/jpeg, image/svg+xml, image/webp).
     */
    @Nullable
    private String mimeType;

    /**
     * Optional sizes (e.g. "48x48", "96x96", "any").
     */
    @Nullable
    private List<String> sizes;

    /**
     * Optional theme: "light" or "dark".
     */
    @Nullable
    private String theme;
}
