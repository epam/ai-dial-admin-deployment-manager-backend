package com.epam.aidial.deployment.manager.dao.entity.deployment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersistenceScaling {
    private int minReplicas;
    private int maxReplicas;
    private Integer scaleToZeroDelaySeconds;
    private PersistenceScalingStrategy strategy;
}
