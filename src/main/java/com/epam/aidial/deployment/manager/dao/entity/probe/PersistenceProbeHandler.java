package com.epam.aidial.deployment.manager.dao.entity.probe;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "$type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PersistenceHttpGetProbe.class, name = "httpGet")
})
public interface PersistenceProbeHandler {
}
