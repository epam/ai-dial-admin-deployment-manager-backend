package com.epam.aidial.deployment.manager.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeploymentInfoDto(
        @NotNull String name,
        @Nullable UUID imageDefinitionId,
        @Nullable ImageTypeDto imageDefinitionType,
        @Nullable String imageDefinitionName,
        @Nullable String imageDefinitionVersion,
        @JsonProperty("$type")
        @NotNull DeploymentTypeDto type,
        @NotNull String displayName,
        @Nullable String description,
        @Nullable String author,
        @Nullable Integer initialScale,
        @Nullable Integer minScale,
        @Nullable Integer maxScale,
        @Nullable ResourcesDto resources,
        @NotNull DeploymentStatusDto status,
        @Nullable String url,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        @Nullable List<String> topics
) {
}
