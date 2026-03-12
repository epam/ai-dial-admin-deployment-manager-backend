package com.epam.aidial.deployment.manager.web.dto;

import com.epam.aidial.deployment.manager.web.validation.ValidDomainList;
import com.epam.aidial.deployment.manager.web.validation.ValidSemanticVersion;
import com.epam.aidial.deployment.manager.web.validation.ValidTopics;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = McpImageDefinitionRequestDto.class, name = "mcp"),
        @JsonSubTypes.Type(value = AdapterImageDefinitionRequestDto.class, name = "adapter"),
        @JsonSubTypes.Type(value = InterceptorImageDefinitionRequestDto.class, name = "interceptor"),
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class ImageDefinitionRequestDto {
    @NotNull
    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    @Pattern(regexp = "^[A-Za-z0-9 _-]+$", message = "Name must not contain special symbols")
    private String name;
    @Nullable
    private String description;
    @NotNull
    @ValidSemanticVersion
    private String version;
    @NotNull
    @Valid
    private ImageSourceDto source;
    @Nullable
    @Size(max = 255, message = "License must not exceed 255 characters")
    private String license;
    @Nullable
    @ValidTopics
    private List<String> topics;
    @Nullable
    private String author;
    @Nullable
    @ValidDomainList
    private List<String> allowedDomains = new ArrayList<>();
    @NotNull
    private ImageBuilderDto imageBuilder;
}
