package com.epam.aidial.deployment.manager.registry.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Remote transport configuration (streamable-http or sse with optional variables) — OpenAPI RemoteTransport.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteTransport {

    /**
     * Transport type: "streamable-http" or "sse".
     */
    private String type;

    /**
     * URL template for the transport (variables in {curly_braces} resolved from variables).
     */
    private String url;

    /**
     * Optional HTTP headers.
     */
    @Nullable
    private List<KeyValueInput> headers;

    /**
     * Configuration variables referenced in the URL template.
     */
    @Nullable
    private Map<String, Object> variables;
}
