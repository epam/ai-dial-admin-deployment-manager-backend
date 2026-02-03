package com.epam.aidial.deployment.manager.dao.entity.probe;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "$type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PersistenceHttpGetProbe.class, name = "httpGet"),
        @JsonSubTypes.Type(value = PersistenceTcpSocketProbe.class, name = "tcpSocket"),
        @JsonSubTypes.Type(value = PersistenceExecProbe.class, name = "exec"),
        @JsonSubTypes.Type(value = PersistenceGrpcProbe.class, name = "grpc")
})
public interface PersistenceProbeHandler {
}
