package com.epam.aidial.deployment.manager.web.dto;

import jakarta.annotation.Nullable;

import java.util.Map;

public record ResourcesDto(
        @Nullable Map<String, String> limits,
        @Nullable Map<String, String> requests
) {
}
