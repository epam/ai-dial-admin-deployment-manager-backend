package com.epam.aidial.deployment.manager.web.dto.probe;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class TcpSocketProbeDto implements ProbeHandlerDto {
    /**
     * Number of the port to access on the container.
     */
    @NotNull
    @Min(1) @Max(65535)
    private int port;
}
