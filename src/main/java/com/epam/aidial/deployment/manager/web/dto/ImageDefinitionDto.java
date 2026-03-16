package com.epam.aidial.deployment.manager.web.dto;

import com.epam.aidial.deployment.manager.web.validation.ValidSemanticVersion;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = McpImageDefinitionDto.class, name = "mcp"),
        @JsonSubTypes.Type(value = AdapterImageDefinitionDto.class, name = "adapter"),
        @JsonSubTypes.Type(value = InterceptorImageDefinitionDto.class, name = "interceptor"),
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class ImageDefinitionDto {
    @NotNull
    private UUID id;
    @NotNull
    private String name;
    @NotNull
    @ValidSemanticVersion
    private String version;
    @Nullable
    private String description;
    @NotNull
    private ImageSourceDto source;
    @Nullable
    private String license;

    @NotNull
    private Instant createdAt;
    @NotNull
    private Instant updatedAt;
    @NotNull
    private List<String> topics;

    @Nullable
    private ImageStatusDto buildStatus;
    @Nullable
    private String author;

    @NotNull
    private List<String> allowedDomains;
    @NotNull
    private ImageBuilderDto imageBuilder;
}
