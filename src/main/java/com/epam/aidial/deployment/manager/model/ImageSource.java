package com.epam.aidial.deployment.manager.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DockerImageSource.class, name = "docker"),
        @JsonSubTypes.Type(value = GitDockerfileImageSource.class, name = "git")
})
public abstract class ImageSource {

}
