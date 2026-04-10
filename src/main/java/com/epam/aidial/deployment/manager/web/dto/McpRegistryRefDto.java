package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.jetbrains.annotations.Nullable;

public record McpRegistryRefDto(
        @NotBlank String packageName,
        @Nullable @Pattern(regexp = ".*\\S.*", message = "must not be blank") String version
) implements ExternalRegistryRefDto {
}
