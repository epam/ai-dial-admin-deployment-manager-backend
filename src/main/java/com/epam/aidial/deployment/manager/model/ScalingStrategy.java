package com.epam.aidial.deployment.manager.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScalingStrategy {
    @JsonProperty("$type")
    private ScalingStrategyType type;
    private int threshold;
}
