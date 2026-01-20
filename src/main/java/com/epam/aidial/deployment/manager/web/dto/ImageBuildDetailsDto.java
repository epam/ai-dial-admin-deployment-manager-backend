package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

public record ImageBuildDetailsDto(
        @NotNull String imageDefinitionName,
        @NotNull ImageStatusDto status,
        @Nullable String imageName,
        @NotNull Instant builtAt,
        @Nullable @Size(min = 1) List<@NotNull String> logs
) {
}
