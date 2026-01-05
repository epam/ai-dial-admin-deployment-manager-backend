package com.epam.aidial.deployment.manager.web.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DockerImageSourceDto.class, name = "docker"),
        @JsonSubTypes.Type(value = GitDockerfileImageSourceDto.class, name = "git")
})
public interface ImageSourceDto {

}
