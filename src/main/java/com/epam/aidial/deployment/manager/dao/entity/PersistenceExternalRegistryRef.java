package com.epam.aidial.deployment.manager.dao.entity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PersistenceMcpRegistryRef.class, name = "mcp-registry"),
        @JsonSubTypes.Type(value = PersistenceGitHubRef.class, name = "github"),
        @JsonSubTypes.Type(value = PersistenceGenericRef.class, name = "generic")
})
public interface PersistenceExternalRegistryRef {
}
