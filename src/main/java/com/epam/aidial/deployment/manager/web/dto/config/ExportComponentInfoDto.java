package com.epam.aidial.deployment.manager.web.dto.config;

public record ExportComponentInfoDto(
        String id,
        String displayName,
        String version,
        String description,
        ExportConfigComponentTypeDto type
) {}
