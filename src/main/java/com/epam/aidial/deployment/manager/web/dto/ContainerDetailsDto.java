package com.epam.aidial.deployment.manager.web.dto;

import com.epam.aidial.deployment.manager.model.ContainerType;
import jakarta.validation.constraints.NotNull;

public record ContainerDetailsDto(
        @NotNull String name,
        @NotNull ContainerType type,
        String state,
        String stateReason
) {
}
