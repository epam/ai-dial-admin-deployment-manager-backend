package com.epam.aidial.deployment.manager.web.dto;

import com.epam.aidial.deployment.manager.web.validation.ValidSemanticVersion;
import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record ImageDefinitionViewElementDto(
        @NotNull UUID id,
        @NotNull @ValidSemanticVersion String version,
        @Nullable ImageStatusDto status,
        @Nullable String description,
        @NotNull List<String> topics
) {
}
