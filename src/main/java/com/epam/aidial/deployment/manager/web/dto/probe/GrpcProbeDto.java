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
public class GrpcProbeDto implements ProbeHandlerDto {
    /**
     * Port number of the gRPC service.
     */
    private Integer port;
    /**
     * Service is the name of the service to place in the gRPC HealthCheckRequest.
     */
    @Nullable
    private String service;
}
