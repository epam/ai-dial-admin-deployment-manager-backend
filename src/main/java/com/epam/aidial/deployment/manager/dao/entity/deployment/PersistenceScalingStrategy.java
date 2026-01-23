package com.epam.aidial.deployment.manager.dao.entity.deployment;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceScalingStrategyType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersistenceScalingStrategy {
    @JsonProperty("$type")
    private PersistenceScalingStrategyType type;
    private double threshold;
}
