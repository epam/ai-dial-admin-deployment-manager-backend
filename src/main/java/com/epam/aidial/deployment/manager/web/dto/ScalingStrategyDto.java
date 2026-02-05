package com.epam.aidial.deployment.manager.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScalingStrategyDto {
    @NotNull
    @JsonProperty("$type")
    private ScalingStrategyTypeDto type;
    private int threshold;
}
