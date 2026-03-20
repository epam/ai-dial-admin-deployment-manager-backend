package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.epam.aidial.deployment.manager.web.dto.DeploymentMetadataDto;
import com.epam.aidial.deployment.manager.web.dto.DeploymentStatusDto;
import com.epam.aidial.deployment.manager.web.dto.ResourcesDto;
import com.epam.aidial.deployment.manager.web.dto.ScalingDto;
import com.epam.aidial.deployment.manager.web.dto.probe.ProbePropertiesDto;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "$type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = McpDeploymentDto.class, name = "mcp"),
        @JsonSubTypes.Type(value = AdapterDeploymentDto.class, name = "adapter"),
        @JsonSubTypes.Type(value = ApplicationDeploymentDto.class, name = "application"),
        @JsonSubTypes.Type(value = InterceptorDeploymentDto.class, name = "interceptor"),
        @JsonSubTypes.Type(value = NimDeploymentDto.class, name = "nim"),
        @JsonSubTypes.Type(value = InferenceDeploymentDto.class, name = "inference"),
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class DeploymentDto {
    @NotNull
    private String name;
    @NotNull
    private String displayName;
    @Nullable
    private String description;
    @NotNull
    @Valid
    private DeploymentMetadataDto metadata;
    @Nullable
    @Valid
    private ScalingDto scaling;
    @Nullable
    private ResourcesDto resources;
    @Nullable
    private ProbePropertiesDto probeProperties;
    @NotNull
    private DeploymentStatusDto status;
    @Nullable
    private String url;
    @Nullable
    private Integer containerPort;
    @NotNull
    private Instant createdAt;
    @NotNull
    private Instant updatedAt;
    @Nullable
    private String author;
    @NotNull
    private List<String> allowedDomains;
    @Nullable
    private List<String> topics;
    @Nullable
    private String command;
    @Nullable
    private String args;
}
