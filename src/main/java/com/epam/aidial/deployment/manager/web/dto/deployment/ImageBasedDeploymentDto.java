package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.epam.aidial.deployment.manager.web.dto.ImageTypeDto;
import com.epam.aidial.deployment.manager.web.dto.ScalingDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
    @NotNull
    private UUID imageDefinitionId;
    @NotNull
    private ImageTypeDto imageDefinitionType;
    @NotNull
    private String imageDefinitionName;
    @NotNull
    private String imageDefinitionVersion;
    @Nullable
    @Valid
    private ScalingDto scaling;
}

