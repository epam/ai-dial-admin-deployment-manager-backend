package com.epam.aidial.deployment.manager.web.dto.probe;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = HttpGetProbeDto.class, name = "httpGet"),
        @JsonSubTypes.Type(value = TcpSocketProbeDto.class, name = "tcpSocket"),
        @JsonSubTypes.Type(value = ExecProbeDto.class, name = "exec"),
        @JsonSubTypes.Type(value = GrpcProbeDto.class, name = "grpc")
})
public interface ProbeHandlerDto {
}
