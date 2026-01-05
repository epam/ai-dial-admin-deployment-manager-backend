package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.epam.aidial.deployment.manager.web.validation.ValidHuggingFaceModelName;
import jakarta.validation.constraints.NotNull;

public record InferenceDeploymentHuggingFaceSourceDto(
        @NotNull @ValidHuggingFaceModelName
        String modelName
) implements InferenceDeploymentSourceDto {
}
