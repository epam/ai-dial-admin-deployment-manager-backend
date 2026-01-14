package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.epam.aidial.deployment.manager.web.dto.DeploymentMetadataDto;
import com.epam.aidial.deployment.manager.web.dto.ResourcesDto;
import com.epam.aidial.deployment.manager.web.validation.ValidResources;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
        @JsonSubTypes.Type(value = CreateMcpDeploymentRequestDto.class, name = "mcp"),
        @JsonSubTypes.Type(value = CreateAdapterDeploymentRequestDto.class, name = "adapter"),
        @JsonSubTypes.Type(value = CreateInterceptorDeploymentRequestDto.class, name = "interceptor"),
        @JsonSubTypes.Type(value = CreateNimDeploymentRequestDto.class, name = "nim"),
        @JsonSubTypes.Type(value = CreateInferenceDeploymentRequestDto.class, name = "inference"),
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class CreateDeploymentRequestDto {
    @NotNull
    private String name;
    @Nullable
    private String description;
    @NotNull
    @Valid
    private DeploymentMetadataDto metadata;
    @Nullable
    private Integer initialScale;
    @Nullable
    private Integer minScale;
    @Nullable
    private Integer maxScale;
    @Nullable
    @ValidResources
    private ResourcesDto resources;
    @Nullable
    @Min(1) @Max(65535)
    private Integer containerPort;
    @Nullable
    private String author;
    @Nullable
    private List<String> allowedDomains = new ArrayList<>();
}
