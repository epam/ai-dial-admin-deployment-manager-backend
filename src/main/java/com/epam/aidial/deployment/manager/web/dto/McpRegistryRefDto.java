package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotBlank;

public record McpRegistryRefDto(
        @NotBlank String packageName
) implements ExternalRegistryRefDto {
}
