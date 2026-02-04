package com.epam.aidial.deployment.manager.model.probe;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "$type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = HttpGetProbe.class, name = "httpGet")
})
public interface ProbeHandler {
}
