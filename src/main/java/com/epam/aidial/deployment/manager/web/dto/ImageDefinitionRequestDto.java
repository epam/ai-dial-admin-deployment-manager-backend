package com.epam.aidial.deployment.manager.web.dto;

import com.epam.aidial.deployment.manager.web.validation.ValidSemanticVersion;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
        @JsonSubTypes.Type(value = NimImageDefinitionRequestDto.class, name = "nim"),
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class ImageDefinitionRequestDto {
    @NotNull
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
    private String license;
    @Nullable
    private List<String> topics;
    @Nullable
    private String author;
    @Nullable
    private List<String> allowedDomains = new ArrayList<>();
}
