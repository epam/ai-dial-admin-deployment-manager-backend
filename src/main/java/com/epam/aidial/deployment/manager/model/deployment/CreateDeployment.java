package com.epam.aidial.deployment.manager.model.deployment;

import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
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
    private Integer initialScale;
    private Integer minScale;
    private Integer maxScale;
    private Resources resources;
    private ProbeProperties probeProperties;
    private Integer containerPort;
    private String author;
    private List<String> allowedDomains;
}
