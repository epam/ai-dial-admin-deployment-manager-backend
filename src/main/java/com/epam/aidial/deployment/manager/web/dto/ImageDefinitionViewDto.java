package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ImageDefinitionViewDto(
        @NotNull String displayName,
        @NotNull String selectedName,
        @NotNull List<ImageDefinitionViewElementDto> availableVersions
) {
}
