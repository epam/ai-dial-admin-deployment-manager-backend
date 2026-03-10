package com.epam.aidial.deployment.manager.dao.entity.deployment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "$type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PersistenceInternalImageSource.class, name = "internal_image"),
        @JsonSubTypes.Type(value = PersistenceImageReferenceSource.class, name = "image_reference"),
        @JsonSubTypes.Type(value = PersistenceNgcRegistrySource.class, name = "ngc_registry"),
        @JsonSubTypes.Type(value = PersistenceHuggingFaceSource.class, name = "huggingface")
})
public interface PersistenceSource {
}
