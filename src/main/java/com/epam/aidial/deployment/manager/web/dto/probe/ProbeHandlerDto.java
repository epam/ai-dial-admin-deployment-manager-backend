package com.epam.aidial.deployment.manager.web.dto.probe;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Marker interface for probe handler DTOs.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "$type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = HttpGetProbeDto.class, name = "httpGet"),
        @JsonSubTypes.Type(value = TcpSocketProbeDto.class, name = "tcpSocket")
})
public interface ProbeHandlerDto {
}
