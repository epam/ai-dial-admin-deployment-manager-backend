package com.epam.aidial.deployment.manager.web.dto.config;

import java.util.List;

public record ExportConfigPreviewDto(
        List<String> globalImageBuildDomainWhitelist,
        List<ExportComponentInfoDto> imageDefinitions,
        List<ExportComponentInfoDto> deployments
) {}
