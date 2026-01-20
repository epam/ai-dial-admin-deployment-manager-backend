package com.epam.aidial.deployment.manager.web.dto.deployment;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class ImageBasedDeploymentDto extends DeploymentDto {
    @NotNull
    private String imageDefinitionName;
    @NotNull
    private String imageDefinitionDisplayName;
    @NotNull
    private String imageDefinitionVersion;
}

