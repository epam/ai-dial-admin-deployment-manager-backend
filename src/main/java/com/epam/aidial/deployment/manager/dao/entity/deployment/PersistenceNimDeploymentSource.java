package com.epam.aidial.deployment.manager.dao.entity.deployment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PersistenceNimDeploymentNgcRegistrySource.class, name = "ngc_registry"),
})
public interface PersistenceNimDeploymentSource {
}
