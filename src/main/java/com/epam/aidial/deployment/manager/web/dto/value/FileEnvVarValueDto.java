package com.epam.aidial.deployment.manager.web.dto.value;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

public record FileEnvVarValueDto(
        @Nullable
        @Size(min = 1, max = 253, message = "File name must be between 1 and 253 characters")
        @Pattern(regexp = "^[-._a-zA-Z0-9]+$", message = "File name must contain only letters, numbers, dots (.), hyphens (-), and underscores (_)")
        String fileName,
        @Nullable String fileContent
) implements EnvVarValueDto {
}