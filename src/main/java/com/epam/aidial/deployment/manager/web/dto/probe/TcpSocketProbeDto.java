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
public class TcpSocketProbeDto implements ProbeHandlerDto {
    /**
     * Number or name of the port to access on the container.
     * If portName is set, port is ignored for the probe.
     */
    @Nullable
    private Integer port;
    /**
     * Name of the port to access on the container.
     */
    @Nullable
    private String portName;
    /**
     * Host name to connect to. Defaults to the pod IP.
     */
    @Nullable
    private String host;
}
