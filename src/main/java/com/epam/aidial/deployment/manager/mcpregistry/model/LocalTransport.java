package com.epam.aidial.deployment.manager.mcpregistry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Transport configuration for a package: stdio, streamable-http, or sse (OpenAPI LocalTransport).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocalTransport {

    /**
     * Transport type: stdio, streamable-http, or sse.
     */
    private LocalTransportType type;

    /**
     * URL for streamable-http or sse transports.
     */
    @Nullable
    private String url;

    /**
     * Optional HTTP headers (for streamable-http / sse).
     */
    @Nullable
    private List<KeyValueInput> headers;
}
