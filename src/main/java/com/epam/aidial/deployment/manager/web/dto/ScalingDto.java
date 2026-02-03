package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScalingDto {
    @Min(0)
    private int minReplicas;
    @Min(1)
    private int maxReplicas;
    @Nullable @Min(1)
    private Integer scaleToZeroDelaySeconds;
    @NotNull @Valid
    private ScalingStrategyDto strategy;
}
