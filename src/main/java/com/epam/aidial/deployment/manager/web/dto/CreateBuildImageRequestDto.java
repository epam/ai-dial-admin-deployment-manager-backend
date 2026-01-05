package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record CreateBuildImageRequestDto(
        @NotNull UUID imageDefinitionId,
        @Nullable String author
) {
}
