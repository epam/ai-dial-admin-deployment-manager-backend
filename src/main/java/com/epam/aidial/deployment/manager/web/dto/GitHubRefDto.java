package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotBlank;

public record GitHubRefDto(
        @NotBlank String repo
) implements ExternalRegistryRefDto {
}
