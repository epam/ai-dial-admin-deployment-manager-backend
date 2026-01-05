package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.Valid;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record DeploymentMetadataDto(
        @Nullable List<@Valid EnvVarDefinitionDto> envs
) {
}
