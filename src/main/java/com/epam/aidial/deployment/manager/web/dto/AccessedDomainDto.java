package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for one accessed domain with Cilium verdict (allowed/blocked).
 */
public record AccessedDomainDto(
        @NotBlank String domain,
        @NotNull String verdict
) {
}
