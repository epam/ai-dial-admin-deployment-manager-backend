package com.epam.aidial.deployment.manager.model.deployment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "$type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = InferenceDeploymentHuggingFaceSource.class, name = "huggingface")
})
public interface InferenceDeploymentSource {
    String getStorageUri();
}
