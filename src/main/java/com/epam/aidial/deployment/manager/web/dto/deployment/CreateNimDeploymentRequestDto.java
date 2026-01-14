package com.epam.aidial.deployment.manager.web.dto.deployment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class CreateNimDeploymentRequestDto extends CreateDeploymentRequestDto {
    @NotNull @Valid
    private NimDeploymentSourceDto source;
    @Nullable
    @Min(1) @Max(65535)
    private Integer containerGrpcPort;
}
