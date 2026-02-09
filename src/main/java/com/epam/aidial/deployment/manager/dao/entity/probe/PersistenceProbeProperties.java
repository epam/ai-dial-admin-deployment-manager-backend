package com.epam.aidial.deployment.manager.dao.entity.probe;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersistenceProbeProperties {
    private boolean enabled;
    private Integer initialDelaySeconds;
    private Integer periodSeconds;
    private Integer timeoutSeconds;
    private Integer failureThreshold;
    private PersistenceProbeHandler probe;
}
