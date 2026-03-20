package com.epam.aidial.deployment.manager.model.config;

import com.epam.aidial.deployment.manager.model.AdapterImageDefinition;
import com.epam.aidial.deployment.manager.model.ApplicationImageDefinition;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.deployment.AdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.ApplicationDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Root structure of the export config file. Uses domain model classes.
 * Export-excluded fields are omitted via JSON mix-ins when serializing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExportConfig {

    @Builder.Default
    private Map<String, McpImageDefinition> mcpImageDefinitions = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, AdapterImageDefinition> adapterImageDefinitions = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, InterceptorImageDefinition> interceptorImageDefinitions = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, ApplicationImageDefinition> applicationImageDefinitions = new LinkedHashMap<>();

    @Builder.Default
    private List<String> globalImageBuildDomainWhitelist = new ArrayList<>();

    @Builder.Default
    private Map<String, McpDeployment> mcpDeployments = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, AdapterDeployment> adapterDeployments = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, ApplicationDeployment> applicationDeployments = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, InterceptorDeployment> interceptorDeployments = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, NimDeployment> nimDeployments = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, InferenceDeployment> inferenceDeployments = new LinkedHashMap<>();
}
