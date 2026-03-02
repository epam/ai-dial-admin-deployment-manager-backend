package com.epam.aidial.deployment.manager.web.dto.deployment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class ImageBasedDeploymentDto extends DeploymentDto {
    @Nullable
    private UUID imageDefinitionId;
    @Nullable
    private String imageDefinitionName;
    @Nullable
    private String imageDefinitionVersion;
    @Nullable
    private String imageReference;
}

