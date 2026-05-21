package com.epam.aidial.deployment.manager.web.dto;

import com.epam.aidial.deployment.manager.model.CiliumVerdict;
import jakarta.validation.constraints.NotNull;

public record DomainEntryDto(
        @NotNull String domain,
        @NotNull CiliumVerdict verdict
) {
}
