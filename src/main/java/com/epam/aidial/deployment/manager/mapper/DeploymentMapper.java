package com.epam.aidial.deployment.manager.mapper;

import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.deployment.AdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.ApplicationDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateAdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateApplicationDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateMcpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateNimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import org.apache.commons.collections4.ListUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public abstract class DeploymentMapper {

    @Mapping(target = "url", ignore = true)
    @Mapping(target = "envs", ignore = true)
    @Mapping(target = "serviceName", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", constant = "NOT_DEPLOYED")
    @SubclassMapping(source = CreateMcpDeployment.class, target = McpDeployment.class)
    @SubclassMapping(source = CreateAdapterDeployment.class, target = AdapterDeployment.class)
    @SubclassMapping(source = CreateApplicationDeployment.class, target = ApplicationDeployment.class)
    @SubclassMapping(source = CreateInterceptorDeployment.class, target = InterceptorDeployment.class)
    @SubclassMapping(source = CreateNimDeployment.class, target = NimDeployment.class)
    @SubclassMapping(source = CreateInferenceDeployment.class, target = InferenceDeployment.class)
    public abstract Deployment toDeployment(CreateDeployment createDeployment);

    public Deployment toDeployment(CreateDeployment createDeployment, List<EnvVar> envVars) {
        var deployment = toDeployment(createDeployment);
        deployment.setEnvs(envVars);
        // Removing values from metadata to avoid saving sensitive values to DB
        Optional.ofNullable(deployment.getMetadata().getEnvs()).orElse(new ArrayList<>())
                .forEach(envVarDef -> envVarDef.setValue(null));
        return deployment;
    }

    @Mapping(target = "nodePoolIdFieldPresent", ignore = true)
    @SubclassMapping(source = McpDeployment.class, target = CreateMcpDeployment.class)
    @SubclassMapping(source = AdapterDeployment.class, target = CreateAdapterDeployment.class)
    @SubclassMapping(source = ApplicationDeployment.class, target = CreateApplicationDeployment.class)
    @SubclassMapping(source = InterceptorDeployment.class, target = CreateInterceptorDeployment.class)
    @SubclassMapping(source = NimDeployment.class, target = CreateNimDeployment.class)
    @SubclassMapping(source = InferenceDeployment.class, target = CreateInferenceDeployment.class)
    public abstract CreateDeployment toCreateDeployment(Deployment deployment);

    public CreateDeployment toCreateCloneDeployment(Deployment etalonDeployment, String newDeploymentId, String newDeploymentDisplayName) {
        var createDeployment = toCreateDeployment(etalonDeployment);
        createDeployment.setId(newDeploymentId);
        createDeployment.setDisplayName(newDeploymentDisplayName);
        // FR-020: duplicate copies the source's nodePool verbatim; bypass the create-time cascade.
        createDeployment.setNodePoolIdFieldPresent(true);

        // For sensitive envs, the value is already resolved from K8s secrets
        var envsMap = toEnvValuesMap(etalonDeployment.getEnvs());
        Optional.ofNullable(etalonDeployment.getMetadata().getEnvs()).orElse(new ArrayList<>())
                .forEach(envVarDef -> {
                    var envVar = envsMap.get(envVarDef.getName());
                    envVarDef.setValue(envVar.getValue());
                });
        createDeployment.setMetadata(etalonDeployment.getMetadata());

        return createDeployment;
    }

    private Map<String, EnvVar> toEnvValuesMap(List<EnvVar> envValues) {
        return ListUtils.emptyIfNull(envValues).stream()
                .collect(Collectors.toMap(EnvVar::getName, envVar -> envVar));
    }

}
