package com.epam.aidial.deployment.manager.web.dto.deployment;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class ImageBasedDeploymentDto extends DeploymentDto {
    @NotNull
    private UUID imageDefinitionId;
    @NotNull
    private String imageDefinitionName;
    @NotNull
    private String imageDefinitionVersion;
}

