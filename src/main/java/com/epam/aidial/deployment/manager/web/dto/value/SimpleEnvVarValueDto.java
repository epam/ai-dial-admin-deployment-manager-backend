package com.epam.aidial.deployment.manager.web.dto.value;

import org.jetbrains.annotations.Nullable;

public record SimpleEnvVarValueDto(
        @Nullable String value
) implements EnvVarValueDto {
}