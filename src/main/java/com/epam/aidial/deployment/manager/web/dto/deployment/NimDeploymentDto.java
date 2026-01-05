package com.epam.aidial.deployment.manager.web.dto.deployment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class NimDeploymentDto extends DeploymentDto {
    @NotNull @Valid
    private NimDeploymentSourceDto source;
    @Nullable
    private Integer containerGrpcPort;
}
