package com.epam.aidial.deployment.manager.dao.entity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PersistenceDockerImageSource.class, name = "docker"),
        @JsonSubTypes.Type(value = PersistenceGitDockerfileImageSource.class, name = "git")
})
public interface PersistenceImageSource {

}
