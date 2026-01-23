package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Scaling {
    private int minReplicas;
    private int maxReplicas;
    @Nullable
    private Integer scaleToZeroDelaySeconds;
    @Nullable
    private ScalingStrategy strategy;
}
