package com.epam.aidial.deployment.manager.web.dto.value;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SimpleEnvVarValueDto.class, name = "simple"),
        @JsonSubTypes.Type(value = FileEnvVarValueDto.class, name = "file")
})
public interface EnvVarValueDto {

}