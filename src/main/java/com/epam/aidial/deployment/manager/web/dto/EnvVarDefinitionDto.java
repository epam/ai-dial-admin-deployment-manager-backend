package com.epam.aidial.deployment.manager.web.dto;

import com.epam.aidial.deployment.manager.web.dto.value.EnvVarValueDto;
import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;

public record EnvVarDefinitionDto(
        @NotNull String name,
        @Nullable EnvVarValueDto value,
        EnvVarMountTypeDto mountType,
        @Nullable String description
) {
}
