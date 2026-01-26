package com.epam.aidial.deployment.manager.web.dto;

import com.epam.aidial.deployment.manager.web.dto.value.EnvVarValueDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

public record EnvVarDefinitionDto(
        @NotNull
        @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
        @Pattern(regexp = "^[-._a-zA-Z0-9]+$", message = "Name must contain only alphanumeric characters, dots, hyphens, and underscores")
        String name,
        @Nullable @Valid EnvVarValueDto value,
        EnvVarMountTypeDto mountType,
        @Nullable String description
) {
}
