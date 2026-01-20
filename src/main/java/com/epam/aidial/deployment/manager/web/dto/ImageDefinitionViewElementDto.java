package com.epam.aidial.deployment.manager.web.dto;

import com.epam.aidial.deployment.manager.web.validation.ValidSemanticVersion;
import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ImageDefinitionViewElementDto(
        @NotNull String name,
        @NotNull @ValidSemanticVersion String version,
        @Nullable ImageStatusDto status,
        @Nullable String description,
        @NotNull List<String> topics
) {
}
