package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import com.epam.aidial.deployment.manager.model.EnvVarMountType;
import com.epam.aidial.deployment.manager.model.McpTransport;
import com.epam.aidial.deployment.manager.model.PodInfo;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.deployment.AdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateAdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateMcpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateNimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeploymentHuggingFaceSource;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeploymentSource;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeploymentNgcRegistrySource;
import com.epam.aidial.deployment.manager.model.deployment.NimDeploymentSource;
import com.epam.aidial.deployment.manager.service.McpEndpointPathResolver;
import com.epam.aidial.deployment.manager.web.dto.DeploymentInfoDto;
import com.epam.aidial.deployment.manager.web.dto.DeploymentMetadataDto;
import com.epam.aidial.deployment.manager.web.dto.DeploymentStatusDto;
import com.epam.aidial.deployment.manager.web.dto.DeploymentTypeDto;
import com.epam.aidial.deployment.manager.web.dto.EnvVarDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.EnvVarMountTypeDto;
import com.epam.aidial.deployment.manager.web.dto.McpTransportDto;
import com.epam.aidial.deployment.manager.web.dto.PodInfoDto;
import com.epam.aidial.deployment.manager.web.dto.ResourcesDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.AdapterDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateAdapterDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateInferenceDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateInterceptorDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateMcpDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateNimDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.DeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.InferenceDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.InferenceDeploymentHuggingFaceSourceDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.InferenceDeploymentSourceDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.InterceptorDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.McpDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.NimDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.NimDeploymentNgcRegistrySourceDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.NimDeploymentSourceDto;
import com.epam.aidial.deployment.manager.web.dto.internal.AdapterDeploymentInternalDto;
import com.epam.aidial.deployment.manager.web.dto.internal.DeploymentInternalDto;
import com.epam.aidial.deployment.manager.web.dto.internal.InferenceDeploymentInternalDto;
import com.epam.aidial.deployment.manager.web.dto.internal.InterceptorDeploymentInternalDto;
import com.epam.aidial.deployment.manager.web.dto.internal.McpDeploymentInternalDto;
import com.epam.aidial.deployment.manager.web.dto.internal.NimDeploymentInternalDto;
import com.epam.aidial.deployment.manager.web.dto.value.EnvVarValueDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Mapper(
        componentModel = "spring",
        uses = {EnvVarValueDtoMapper.class},
        subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION
)
public abstract class DeploymentDtoMapper {

    @Autowired
    private EnvVarValueDtoMapper envVarValueDtoMapper;
    @Autowired
    private McpEndpointPathResolver mcpEndpointPathResolver;

    @Mapping(target = "id", source = "name")
    @SubclassMapping(source = CreateMcpDeploymentRequestDto.class, target = CreateMcpDeployment.class)
    @SubclassMapping(source = CreateAdapterDeploymentRequestDto.class, target = CreateAdapterDeployment.class)
    @SubclassMapping(source = CreateInterceptorDeploymentRequestDto.class, target = CreateInterceptorDeployment.class)
    @SubclassMapping(source = CreateNimDeploymentRequestDto.class, target = CreateNimDeployment.class)
    @SubclassMapping(source = CreateInferenceDeploymentRequestDto.class, target = CreateInferenceDeployment.class)
    public abstract CreateDeployment toCreateDeployment(CreateDeploymentRequestDto dto);

    @Mapping(target = "id", source = "name")
    @Mapping(target = "imageDefinitionId", ignore = true)
    public abstract CreateNimDeployment toCreateDeployment(CreateNimDeploymentRequestDto dto);

    @Mapping(target = "id", source = "name")
    @Mapping(target = "imageDefinitionId", ignore = true)
    public abstract CreateInferenceDeployment toCreateDeployment(CreateInferenceDeploymentRequestDto dto);

    @SubclassMapping(source = InferenceDeploymentHuggingFaceSourceDto.class, target = InferenceDeploymentHuggingFaceSource.class)
    protected abstract InferenceDeploymentSource toModel(InferenceDeploymentSourceDto dto);

    @SubclassMapping(source = NimDeploymentNgcRegistrySourceDto.class, target = NimDeploymentNgcRegistrySource.class)
    protected abstract NimDeploymentSource toModel(NimDeploymentSourceDto dto);

    @Mapping(target = "name", source = "id")
    @Mapping(target = "url", source = "model", qualifiedByName = "constructFullUrl")
    @Mapping(target = "metadata", source = "model", qualifiedByName = "toMetadata")
    @SubclassMapping(source = McpDeployment.class, target = McpDeploymentDto.class)
    @SubclassMapping(source = AdapterDeployment.class, target = AdapterDeploymentDto.class)
    @SubclassMapping(source = InterceptorDeployment.class, target = InterceptorDeploymentDto.class)
    @SubclassMapping(source = NimDeployment.class, target = NimDeploymentDto.class)
    @SubclassMapping(source = InferenceDeployment.class, target = InferenceDeploymentDto.class)
    public abstract DeploymentDto toDeploymentDto(Deployment model);

    @SubclassMapping(source = InferenceDeploymentHuggingFaceSource.class, target = InferenceDeploymentHuggingFaceSourceDto.class)
    protected abstract InferenceDeploymentSourceDto toDto(InferenceDeploymentSource model);

    @SubclassMapping(source = NimDeploymentNgcRegistrySource.class, target = NimDeploymentNgcRegistrySourceDto.class)
    protected abstract NimDeploymentSourceDto toDto(NimDeploymentSource model);

    @Mapping(target = "url", source = "model", qualifiedByName = "constructFullUrl")
    @SubclassMapping(source = McpDeployment.class, target = McpDeploymentInternalDto.class)
    @SubclassMapping(source = AdapterDeployment.class, target = AdapterDeploymentInternalDto.class)
    @SubclassMapping(source = InterceptorDeployment.class, target = InterceptorDeploymentInternalDto.class)
    @SubclassMapping(source = NimDeployment.class, target = NimDeploymentInternalDto.class)
    @SubclassMapping(source = InferenceDeployment.class, target = InferenceDeploymentInternalDto.class)
    public abstract DeploymentInternalDto toDeploymentInternalDto(Deployment model);

    @AfterMapping
    protected void setMcpTransport(@MappingTarget DeploymentInternalDto dto, Deployment model) {
        if (model instanceof McpDeployment mcpDeployment && dto instanceof McpDeploymentInternalDto mcpDto) {
            mcpDto.setTransport(toMcpTransportDtoWithDefault(mcpDeployment.getTransport()));
        }
    }

    @Named("toMetadata")
    protected DeploymentMetadataDto toMetadata(Deployment deployment) {
        var envDefs = deployment.getMetadata().getEnvs();
        if (envDefs == null) {
            return new DeploymentMetadataDto(new ArrayList<>());
        }
        var envValuesMap = toEnvValuesMap(deployment.getEnvs());
        var envDefDtos = envDefs.stream()
                .map(envDef -> toEnvVarDefinitionDto(envDef, envValuesMap.get(envDef.getName())))
                .toList();
        return new DeploymentMetadataDto(envDefDtos);
    }

    private Map<String, EnvVar> toEnvValuesMap(List<EnvVar> envValues) {
        return ListUtils.emptyIfNull(envValues).stream()
                .collect(Collectors.toMap(EnvVar::getName, envVar -> envVar));
    }

    private EnvVarDefinitionDto toEnvVarDefinitionDto(EnvVarDefinition envDef, EnvVar envValue) {
        return new EnvVarDefinitionDto(envDef.getName(),
                getValueIfPresent(envValue),
                toEnvVarMountTypeDto(envDef.getMountType()),
                envDef.getDescription());
    }

    private EnvVarValueDto getValueIfPresent(EnvVar envVar) {
        if (envVar == null) {
            return null;
        }
        return envVarValueDtoMapper.toDto(envVar.getValue());
    }

    public abstract EnvVarMountTypeDto toEnvVarMountTypeDto(EnvVarMountType mountType);

    public abstract DeploymentStatusDto toDeploymentStatusDto(DeploymentStatus status);

    @Named("toDeploymentTypeDto")
    protected DeploymentTypeDto toDeploymentTypeDto(Deployment deployment) {
        if (deployment instanceof McpDeployment) {
            return DeploymentTypeDto.MCP;
        } else if (deployment instanceof AdapterDeployment) {
            return DeploymentTypeDto.ADAPTER;
        } else if (deployment instanceof InterceptorDeployment) {
            return DeploymentTypeDto.INTERCEPTOR;
        } else if (deployment instanceof NimDeployment) {
            return DeploymentTypeDto.NIM;
        } else if (deployment instanceof InferenceDeployment) {
            return DeploymentTypeDto.INFERENCE;
        }
        throw new IllegalArgumentException("Unknown deployment type: " + deployment.getClass().getName());
    }

    @Mapping(target = "name", source = "id")
    @Mapping(target = "url", source = "model", qualifiedByName = "constructFullUrl")
    @Mapping(target = "type", source = "model", qualifiedByName = "toDeploymentTypeDto")
    public abstract DeploymentInfoDto toDeploymentInfoDto(Deployment model);

    public abstract List<PodInfoDto> toPodInfoDto(List<PodInfo> model);

    protected Resources toResources(ResourcesDto dto) {
        if (dto == null) {
            return new Resources();
        }
        var limits = MapUtils.emptyIfNull(dto.limits());
        var requests = MapUtils.emptyIfNull(dto.requests());
        return new Resources(limits, requests);
    }

    protected ResourcesDto toResourcesDto(Resources model) {
        if (model.getLimits().isEmpty() && model.getRequests().isEmpty()) {
            return null;
        }
        var limits = model.getLimits().isEmpty() ? null : model.getLimits();
        var requests = model.getRequests().isEmpty() ? null : model.getRequests();
        return new ResourcesDto(limits, requests);
    }

    @Named("constructFullUrl")
    protected String constructFullUrl(Deployment deployment) {
        if (deployment.getUrl() == null) {
            return null;
        }

        if (deployment instanceof McpDeployment mcpDeployment) {
            var endpointPath = mcpEndpointPathResolver.resolveEndpointPath(mcpDeployment);
            return deployment.getUrl() + endpointPath;
        }

        // For non-MCP deployments, return the base URL
        return deployment.getUrl();
    }

    @Named("toMcpTransportDtoWithDefault")
    protected McpTransportDto toMcpTransportDtoWithDefault(McpTransport transport) {
        if (transport == null) {
            return McpTransportDto.HTTP_STREAMING;
        }
        return switch (transport) {
            case SSE -> McpTransportDto.SSE;
            case HTTP_STREAMING -> McpTransportDto.HTTP_STREAMING;
        };
    }

    protected List<String> stringToList(String str) {
        if (StringUtils.isBlank(str)) {
            return null;
        }

        try {
            // Parse the string; this automatically handles quoted tokens (e.g., "foo bar")
            CommandLine commandLine = CommandLine.parse(str);

            return Arrays.asList(commandLine.toStrings());
        } catch (IllegalArgumentException e) {
            var errorMessage = "Cannot parse command/arguments: '%s'".formatted(str);
            log.warn(errorMessage, e);
            throw new IllegalArgumentException(errorMessage, e);
        }
    }

    protected String listToString(List<String> list) {
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }

        // 1. Create CommandLine using the first element as the executable
        CommandLine commandLine = new CommandLine(list.getFirst());

        // 2. Add the rest of the list as arguments
        if (list.size() > 1) {
            String[] args = list.subList(1, list.size()).toArray(new String[0]);
            commandLine.addArguments(args);
        }

        // 3. toStrings() automatically quotes the executable and arguments as needed
        String[] parts = commandLine.toStrings();
        return String.join(" ", parts);
    }

}
