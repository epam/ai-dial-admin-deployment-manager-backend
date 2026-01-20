package com.epam.aidial.deployment.manager.model.deployment;

import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.Resources;
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
public abstract class Deployment {
    private String id;
    private String imageDefinitionId;
    private String imageDefinitionDisplayName;
    private String imageDefinitionVersion;
    private String displayName;
    private String description;
    private List<EnvVar> envs;
    private DeploymentMetadata metadata;
    private Integer initialScale;
    private Integer minScale;
    private Integer maxScale;
    private Resources resources;
    private DeploymentStatus status;
    private String url;
    private Integer containerPort;
    private Instant createdAt;
    private Instant updatedAt;
    private String author;
    private List<String> allowedDomains;
}