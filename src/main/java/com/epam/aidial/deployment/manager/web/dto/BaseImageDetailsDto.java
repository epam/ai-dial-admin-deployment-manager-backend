package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotNull;

public record BaseImageDetailsDto(
        @NotNull String name,
        @NotNull String displayName,
        @NotNull String version,
        @NotNull ImageStatusDto status
) {
}
