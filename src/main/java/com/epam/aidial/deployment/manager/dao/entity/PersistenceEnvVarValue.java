package com.epam.aidial.deployment.manager.dao.entity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PersistenceSimpleEnvVarValue.class, name = "simple"),
        @JsonSubTypes.Type(value = PersistenceFileEnvVarValue.class, name = "file")
})
public interface PersistenceEnvVarValue {

}