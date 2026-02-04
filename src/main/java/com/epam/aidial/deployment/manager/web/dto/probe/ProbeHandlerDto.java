package com.epam.aidial.deployment.manager.web.dto.probe;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Marker interface for probe handler DTOs. Only httpGet is supported currently; more types may be
 * added later (e.g. tcpSocket, exec, grpc).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "$type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = HttpGetProbeDto.class, name = "httpGet")
})
public interface ProbeHandlerDto {
}
