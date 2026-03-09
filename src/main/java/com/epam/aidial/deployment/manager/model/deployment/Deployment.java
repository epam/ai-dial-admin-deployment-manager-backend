package com.epam.aidial.deployment.manager.model.deployment;

import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.Scaling;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "$type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = McpDeployment.class, name = "mcp"),
        @JsonSubTypes.Type(value = AdapterDeployment.class, name = "adapter"),
        @JsonSubTypes.Type(value = InterceptorDeployment.class, name = "interceptor"),
        @JsonSubTypes.Type(value = NimDeployment.class, name = "nim"),
        @JsonSubTypes.Type(value = InferenceDeployment.class, name = "inference")
})
public abstract class Deployment {
    private String id;
    private Source source;
    private String displayName;
    private String description;
    private List<EnvVar> envs;
    private DeploymentMetadata metadata;
    private Scaling scaling;
    private Resources resources;
    private ProbeProperties probeProperties;
    private DeploymentStatus status;
    private String url;
    private Integer containerPort;
    private Instant createdAt;
    private Instant updatedAt;
    private String author;
    private List<String> allowedDomains;
    private List<String> topics;
}