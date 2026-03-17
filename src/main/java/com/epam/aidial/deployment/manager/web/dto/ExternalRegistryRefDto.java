package com.epam.aidial.deployment.manager.web.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = McpRegistryRefDto.class, name = "mcp-registry"),
        @JsonSubTypes.Type(value = GitHubRefDto.class, name = "github"),
        @JsonSubTypes.Type(value = GenericRefDto.class, name = "generic")
})
public interface ExternalRegistryRefDto {
}
