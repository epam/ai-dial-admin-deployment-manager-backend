package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record PodInfoDto(
        @NotNull String name,
        @NotNull Instant createdAt
) {
}
