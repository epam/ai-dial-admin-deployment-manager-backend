package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;

public record CreateBuildImageRequestDto(
        @NotNull String imageDefinitionName,
        @Nullable String author
) {
}
