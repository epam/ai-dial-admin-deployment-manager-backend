package com.epam.aidial.deployment.manager.web.dto.internal;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = McpDeploymentInternalDto.class, name = "mcp"),
        @JsonSubTypes.Type(value = AdapterDeploymentInternalDto.class, name = "adapter"),
        @JsonSubTypes.Type(value = InterceptorDeploymentInternalDto.class, name = "interceptor"),
        @JsonSubTypes.Type(value = NimDeploymentInternalDto.class, name = "nim"),
        @JsonSubTypes.Type(value = InferenceDeploymentInternalDto.class, name = "inference"),
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class DeploymentInternalDto {
    @NotNull
    private UUID id;
    @NotNull
    private String name;
    @Nullable
    private String url;
}
