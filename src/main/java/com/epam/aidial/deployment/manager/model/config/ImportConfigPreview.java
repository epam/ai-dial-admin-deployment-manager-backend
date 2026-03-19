package com.epam.aidial.deployment.manager.model.config;

import com.epam.aidial.deployment.manager.model.AdapterImageDefinition;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.deployment.AdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImportConfigPreview {

    @Builder.Default
    private List<ImportComponent<McpImageDefinition>> mcpImageDefinitions = new ArrayList<>();
    @Builder.Default
    private List<ImportComponent<AdapterImageDefinition>> adapterImageDefinitions = new ArrayList<>();
    @Builder.Default
    private List<ImportComponent<InterceptorImageDefinition>> interceptorImageDefinitions = new ArrayList<>();

    @Builder.Default
    private List<ImportComponent<McpDeployment>> mcpDeployments = new ArrayList<>();
    @Builder.Default
    private List<ImportComponent<AdapterDeployment>> adapterDeployments = new ArrayList<>();
    @Builder.Default
    private List<ImportComponent<InterceptorDeployment>> interceptorDeployments = new ArrayList<>();
    @Builder.Default
    private List<ImportComponent<NimDeployment>> nimDeployments = new ArrayList<>();
    @Builder.Default
    private List<ImportComponent<InferenceDeployment>> inferenceDeployments = new ArrayList<>();

    private ImportComponent<List<String>> globalImageBuildDomainWhitelist;

    @Builder.Default
    private List<ImportValidationError> validationErrors = new ArrayList<>();
}
