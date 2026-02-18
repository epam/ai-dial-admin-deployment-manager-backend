package com.epam.aidial.deployment.manager.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "$type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = McpImageDefinition.class, name = "mcp"),
        @JsonSubTypes.Type(value = AdapterImageDefinition.class, name = "adapter"),
        @JsonSubTypes.Type(value = InterceptorImageDefinition.class, name = "interceptor")
})
public abstract class ImageDefinition {
    private UUID id;
    private String name;
    private String description;
    private String version;
    private ImageSource source;
    private String license;
    private List<String> topics;

    @EqualsAndHashCode.Exclude
    private Instant createdAt;
    @EqualsAndHashCode.Exclude
    private Instant updatedAt;

    private String imageName;
    private ImageStatus buildStatus;
    private List<String> buildLogs;
    private Instant builtAt;
    private String author;

    private List<String> allowedDomains;
}
