package com.epam.aidial.deployment.manager.model.deployment;

import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.Resources;
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
    private String imageDefinitionId;
    private String displayName;
    private String description;
    private DeploymentMetadata metadata;
    private Integer initialScale;
    private Integer minScale;
    private Integer maxScale;
    private Resources resources;
    private Integer containerPort;
    private String author;
    private List<String> allowedDomains;
}
