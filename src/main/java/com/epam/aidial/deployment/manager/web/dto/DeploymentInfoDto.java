package com.epam.aidial.deployment.manager.web.dto;

import com.epam.aidial.deployment.manager.web.dto.deployment.DeploymentSourceDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

public record DeploymentInfoDto(
        @NotNull String name,
        @Nullable DeploymentSourceDto source,
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
        @NotNull Instant updatedAt
) {
}
