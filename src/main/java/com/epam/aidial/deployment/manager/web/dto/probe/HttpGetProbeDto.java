package com.epam.aidial.deployment.manager.web.dto.probe;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HttpGetProbeDto implements ProbeHandlerDto {
    /**
     * Path to access on the HTTP server.
     */
    private String path;
    /**
     * Number of the port to access on the container. Scheme defaults to HTTP, host to pod IP.
     */
    @Nullable
    private Integer port;
}
