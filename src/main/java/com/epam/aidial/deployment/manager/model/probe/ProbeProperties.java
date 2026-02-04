package com.epam.aidial.deployment.manager.model.probe;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProbeProperties {
    private boolean enabled;
    @Nullable
    private Integer initialDelaySeconds;
    @Nullable
    private Integer periodSeconds;
    @Nullable
    private Integer timeoutSeconds;
    @Nullable
    private Integer failureThreshold;
    @Nullable
    private ProbeHandler probe;
}
