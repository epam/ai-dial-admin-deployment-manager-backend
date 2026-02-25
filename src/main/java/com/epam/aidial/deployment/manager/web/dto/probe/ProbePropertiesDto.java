package com.epam.aidial.deployment.manager.web.dto.probe;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
    @Min(0) @Max(6000)
    private Integer initialDelaySeconds;
    /**
     * How often (in seconds) to perform the probe.
     */
    @Nullable
    @Min(1) @Max(600)
    private Integer periodSeconds;
    /**
     * Number of seconds after which the probe attempt times out.
     */
    @Nullable
    @Min(1) @Max(12000)
    private Integer timeoutSeconds;
    /**
     * Minimum consecutive failures for the probe to be considered failed.
     */
    @Nullable
    @Min(1) @Max(100)
    private Integer failureThreshold;
    /**
     * Probe handler.
     */
    @NotNull
    @Valid
    private ProbeHandlerDto probe;
}
