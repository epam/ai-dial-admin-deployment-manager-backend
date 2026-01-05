package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record DeploymentInfoDto(
        @NotNull UUID id,
        @NotNull UUID imageDefinitionId,
        @NotNull String imageDefinitionName,
        @NotNull String imageDefinitionVersion,
        @NotNull DeploymentTypeDto type,
        @NotNull String name,
        @Nullable String description,
        @Nullable String author,
        @Nullable Integer initialScale,
        @Nullable Integer minScale,
        @Nullable Integer maxScale,
        @Nullable ResourcesDto resources,
        @NotNull DeploymentStatusDto status,
        @Nullable String url,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt
) {
}
