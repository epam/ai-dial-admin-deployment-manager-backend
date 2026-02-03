package com.epam.aidial.deployment.manager.web.dto.probe;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProbePropertiesDto {
    /**
     * When true, the startup probe is applied to the deployment's container(s).
     */
    private boolean enabled;
    /**
     * Number of seconds after the container has started before the probe is initiated.
     */
    @Nullable
    private Integer initialDelaySeconds;
    /**
     * How often (in seconds) to perform the probe.
     */
    @Nullable
    private Integer periodSeconds;
    /**
     * Number of seconds after which the probe times out.
     */
    @Nullable
    private Integer timeoutSeconds;
    /**
     * Minimum consecutive failures for the probe to be considered failed.
     */
    @Nullable
    private Integer failureThreshold;
    /**
     * Probe handler. One of httpGet, tcpSocket, exec, or grpc (by type) must be set when enabled.
     */
    @Nullable
    @Valid
    private ProbeHandlerDto probe;
}
