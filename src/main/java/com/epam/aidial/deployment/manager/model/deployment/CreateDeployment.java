package com.epam.aidial.deployment.manager.model.deployment;

import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.Scaling;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class CreateDeployment {
    private String id;
    private Source source;
    private String displayName;
    private String description;
    private DeploymentMetadata metadata;
    private Scaling scaling;
    private Resources resources;
    private ProbeProperties probeProperties;
    private Integer containerPort;
    private String author;
    private List<String> allowedDomains;
    private List<String> topics;
    private List<String> command;
    private List<String> args;
    private String nodePoolId;

    /**
     * Tracks whether the {@code nodePoolId} field was explicitly present in the inbound payload
     * (with any value, including {@code null}). Distinguishes "field omitted → run create-time
     * cascade" from "explicit null → store null verbatim" (FR-013, FR-018). Not serialized.
     */
    @JsonIgnore
    private transient boolean nodePoolIdFieldPresent;

    public void setNodePoolId(String nodePoolId) {
        this.nodePoolId = nodePoolId;
        this.nodePoolIdFieldPresent = true;
    }
}
