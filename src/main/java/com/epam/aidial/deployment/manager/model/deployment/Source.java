package com.epam.aidial.deployment.manager.model.deployment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "$type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = InternalImageSource.class, name = "internal_image"),
        @JsonSubTypes.Type(value = ImageReferenceSource.class, name = "image_reference"),
        @JsonSubTypes.Type(value = NgcRegistrySource.class, name = "ngc_registry"),
        @JsonSubTypes.Type(value = HuggingFaceSource.class, name = "huggingface")
})
public interface Source {
}
