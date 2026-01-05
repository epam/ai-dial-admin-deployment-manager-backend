package com.epam.aidial.deployment.manager.dao.entity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PersistenceSimpleEnvVar.class, name = "simple"),
        @JsonSubTypes.Type(value = PersistenceSensitiveEnvVar.class, name = "sensitive"),
        @JsonSubTypes.Type(value = PersistenceSensitiveFileEnvVar.class, name = "sensitive-file")
})
public interface PersistenceEnvVar {

    String getName();

    PersistenceEnvVarValue getValue();

}
