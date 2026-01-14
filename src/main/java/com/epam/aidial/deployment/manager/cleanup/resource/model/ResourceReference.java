package com.epam.aidial.deployment.manager.cleanup.resource.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = K8sResourceReference.class, name = "k8s"),
        @JsonSubTypes.Type(value = ContainerRegistryResourceReference.class, name = "container-registry")
})
public interface ResourceReference {

}
