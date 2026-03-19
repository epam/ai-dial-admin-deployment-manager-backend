package com.epam.aidial.deployment.manager.web.dto.config;

import com.epam.aidial.deployment.manager.web.dto.AdapterImageDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.InterceptorImageDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.McpImageDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.AdapterDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.InferenceDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.InterceptorDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.McpDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.NimDeploymentDto;

import java.util.List;

public record ImportConfigPreviewDto(
        List<ImportComponentDto<McpImageDefinitionDto>> mcpImageDefinitions,
        List<ImportComponentDto<AdapterImageDefinitionDto>> adapterImageDefinitions,
        List<ImportComponentDto<InterceptorImageDefinitionDto>> interceptorImageDefinitions,
        List<ImportComponentDto<McpDeploymentDto>> mcpDeployments,
        List<ImportComponentDto<AdapterDeploymentDto>> adapterDeployments,
        List<ImportComponentDto<InterceptorDeploymentDto>> interceptorDeployments,
        List<ImportComponentDto<NimDeploymentDto>> nimDeployments,
        List<ImportComponentDto<InferenceDeploymentDto>> inferenceDeployments,
        ImportComponentDto<List<String>> globalImageBuildDomainWhitelist
) {}
