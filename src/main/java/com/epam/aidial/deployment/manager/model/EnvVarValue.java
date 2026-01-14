package com.epam.aidial.deployment.manager.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SimpleEnvVarValue.class, name = "simple"),
        @JsonSubTypes.Type(value = FileEnvVarValue.class, name = "file")
})
public interface EnvVarValue {
    String getValue();
}