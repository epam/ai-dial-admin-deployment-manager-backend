package com.epam.aidial.deployment.manager.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = McpRegistryRef.class, name = "mcp-registry"),
        @JsonSubTypes.Type(value = GitHubRef.class, name = "github"),
        @JsonSubTypes.Type(value = GenericRef.class, name = "generic")
})
public interface ExternalRegistryRef {
}
