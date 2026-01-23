package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.epam.aidial.deployment.manager.web.dto.ScalingDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class InferenceDeploymentDto extends DeploymentDto {
    @NotNull
    private String modelFormat;
    @NotNull @Valid
    private InferenceDeploymentSourceDto source;
    @Nullable
    private String command;
    @Nullable
    private String args;
    @Nullable @Valid
    private ScalingDto scaling;
}
