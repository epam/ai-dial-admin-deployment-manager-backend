package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.epam.aidial.deployment.manager.web.dto.DeploymentMetadataDto;
import com.epam.aidial.deployment.manager.web.dto.ResourcesDto;
import com.epam.aidial.deployment.manager.web.dto.ScalingDto;
import com.epam.aidial.deployment.manager.web.dto.probe.ProbePropertiesDto;
import com.epam.aidial.deployment.manager.web.validation.ValidDomainList;
import com.epam.aidial.deployment.manager.web.validation.ValidResources;
import com.epam.aidial.deployment.manager.web.validation.ValidTopics;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
        @JsonSubTypes.Type(value = CreateMcpDeploymentRequestDto.class, name = "mcp"),
        @JsonSubTypes.Type(value = CreateAdapterDeploymentRequestDto.class, name = "adapter"),
        @JsonSubTypes.Type(value = CreateApplicationDeploymentRequestDto.class, name = "application"),
        @JsonSubTypes.Type(value = CreateInterceptorDeploymentRequestDto.class, name = "interceptor"),
        @JsonSubTypes.Type(value = CreateNimDeploymentRequestDto.class, name = "nim"),
        @JsonSubTypes.Type(value = CreateInferenceDeploymentRequestDto.class, name = "inference"),
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class CreateDeploymentRequestDto {
    @NotNull
    @Size(min = 2, max = 36, message = "Deployment ID must be between 2 and 36 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Deployment ID must contain only lowercase Latin letters, numbers, and hyphens")
    private String name;
    @NotNull
    @Size(min = 2, max = 255, message = "Display name must be between 2 and 255 characters")
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
    @ValidResources
    private ResourcesDto resources;
    @Nullable
    @Valid
    private ProbePropertiesDto probeProperties;
    @Nullable
    @Min(1) @Max(65535)
    private Integer containerPort;
    @Nullable
    private String author;
    @Nullable
    @ValidDomainList
    private List<String> allowedDomains = new ArrayList<>();
    @Nullable
    @ValidTopics
    private List<String> topics;
    @Nullable
    private String command;
    @Nullable
    private String args;
    @Nullable
    private String nodePool;

    /**
     * Tracks whether the {@code nodePool} field was explicitly present in the inbound JSON
     * (with any value, including {@code null}). Distinguishes "field omitted → run create-time
     * cascade" from "explicit null → store null verbatim" (FR-013, FR-018). Not serialized.
     */
    @JsonIgnore
    private transient boolean nodePoolFieldPresent;

    public void setNodePool(String nodePool) {
        this.nodePool = nodePool;
        this.nodePoolFieldPresent = true;
    }
}
