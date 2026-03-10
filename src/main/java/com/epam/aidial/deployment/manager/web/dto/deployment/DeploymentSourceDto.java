package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = InternalImageDeploymentSourceDto.class, name = "internal_image"),
        @JsonSubTypes.Type(value = ImageReferenceDeploymentSourceDto.class, name = "image_reference")
})
public interface DeploymentSourceDto {
}
