package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotBlank;

public record GenericRefDto(
        @NotBlank String url
) implements ExternalRegistryRefDto {
}
