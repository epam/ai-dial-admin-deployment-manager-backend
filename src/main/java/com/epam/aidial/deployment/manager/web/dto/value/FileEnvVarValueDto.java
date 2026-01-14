package com.epam.aidial.deployment.manager.web.dto.value;

import org.jetbrains.annotations.Nullable;

public record FileEnvVarValueDto(
        @Nullable String fileName,
        @Nullable String fileContent
) implements EnvVarValueDto {
}