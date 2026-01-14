package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ImageDefinitionViewDto(
        @NotNull String name,
        @NotNull UUID selectedId,
        @NotNull List<ImageDefinitionViewElementDto> availableVersions
) {
}
