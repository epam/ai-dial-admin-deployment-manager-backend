package com.epam.aidial.deployment.manager.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SimpleEnvVar.class, name = "simple"),
        @JsonSubTypes.Type(value = SensitiveEnvVar.class, name = "sensitive"),
        @JsonSubTypes.Type(value = SensitiveFileEnvVar.class, name = "sensitive-file")
})
public interface EnvVar {

    String getName();

    EnvVarValue getValue();

}
