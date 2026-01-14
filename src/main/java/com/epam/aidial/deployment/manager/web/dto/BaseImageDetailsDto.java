package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BaseImageDetailsDto(
        @NotNull UUID id,
        @NotNull String name,
        @NotNull String version,
        @NotNull ImageStatusDto status
) {
}
