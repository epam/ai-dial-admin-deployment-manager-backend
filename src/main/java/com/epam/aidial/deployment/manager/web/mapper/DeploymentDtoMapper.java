package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import com.epam.aidial.deployment.manager.model.EnvVarMountType;
import com.epam.aidial.deployment.manager.model.ImageType;
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
import com.epam.aidial.deployment.manager.model.deployment.HuggingFaceSource;
import com.epam.aidial.deployment.manager.model.deployment.ImageReferenceSource;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InternalImageSource;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NgcRegistrySource;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.service.McpEndpointPathResolver;
import com.epam.aidial.deployment.manager.web.dto.DeploymentInfoDto;
import com.epam.aidial.deployment.manager.web.dto.DeploymentMetadataDto;
import com.epam.aidial.deployment.manager.web.dto.DeploymentStatusDto;
import com.epam.aidial.deployment.manager.web.dto.DeploymentTypeDto;
import com.epam.aidial.deployment.manager.web.dto.EnvVarDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.EnvVarMountTypeDto;
import com.epam.aidial.deployment.manager.web.dto.ImageTypeDto;
import com.epam.aidial.deployment.manager.web.dto.McpTransportDto;
import com.epam.aidial.deployment.manager.web.dto.PodInfoDto;
import com.epam.aidial.deployment.manager.web.dto.ResourcesDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.AdapterDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateAdapterDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateImageBasedDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateImageReferenceDeploymentSourceRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateInferenceDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateInterceptorDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateInternalImageDeploymentSourceRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateMcpDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateNimDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.DeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.DeploymentSourceDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.ImageBasedDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.ImageReferenceDeploymentSourceDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.InferenceDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.InferenceDeploymentHuggingFaceSourceDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.InferenceDeploymentSourceDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.InterceptorDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.InternalImageDeploymentSourceDto;
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
import com.epam.aidial.deployment.manager.web.utils.CommandLineUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Mapper(
        componentModel = "spring",
        uses = {EnvVarValueDtoMapper.class, ProbePropertiesDtoMapper.class},
        subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION
)
public abstract class DeploymentDtoMapper {

    @Autowired
    private EnvVarValueDtoMapper envVarValueDtoMapper;
    @Autowired
    private McpEndpointPathResolver mcpEndpointPathResolver;

    @Mapping(target = "id", source = "name")
    @Mapping(target = "source", ignore = true)
    @SubclassMapping(source = CreateMcpDeploymentRequestDto.class, target = CreateMcpDeployment.class)
    @SubclassMapping(source = CreateAdapterDeploymentRequestDto.class, target = CreateAdapterDeployment.class)
    @SubclassMapping(source = CreateInterceptorDeploymentRequestDto.class, target = CreateInterceptorDeployment.class)
    @SubclassMapping(source = CreateNimDeploymentRequestDto.class, target = CreateNimDeployment.class)
    @SubclassMapping(source = CreateInferenceDeploymentRequestDto.class, target = CreateInferenceDeployment.class)
    public abstract CreateDeployment toCreateDeployment(CreateDeploymentRequestDto dto);

    @Mapping(target = "name", source = "id")
    @Mapping(target = "url", source = "model", qualifiedByName = "constructFullUrl")
    @Mapping(target = "metadata", source = "model", qualifiedByName = "toMetadata")
    @Mapping(target = "source", ignore = true)
    @SubclassMapping(source = McpDeployment.class, target = McpDeploymentDto.class)
    @SubclassMapping(source = AdapterDeployment.class, target = AdapterDeploymentDto.class)
    @SubclassMapping(source = InterceptorDeployment.class, target = InterceptorDeploymentDto.class)
    @SubclassMapping(source = NimDeployment.class, target = NimDeploymentDto.class)
    @SubclassMapping(source = InferenceDeployment.class, target = InferenceDeploymentDto.class)
    public abstract DeploymentDto toDeploymentDto(Deployment model);

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

    @AfterMapping
    protected void setCreateImageSource(@MappingTarget CreateDeployment model, CreateImageBasedDeploymentRequestDto dto) {
        applyCreateImageSource(model, dto);
    }

    @AfterMapping
    protected void setCreateNimSource(@MappingTarget CreateNimDeployment model, CreateNimDeploymentRequestDto dto) {
        switch (dto.getSource()) {
            case NimDeploymentNgcRegistrySourceDto(String imageRef) ->
                    model.setSource(new NgcRegistrySource(imageRef));
            default -> throw new IllegalArgumentException(
                    "Unsupported NIM deployment source type: " + dto.getSource().getClass().getName());
        }
    }

    @AfterMapping
    protected void setCreateInferenceSource(@MappingTarget CreateInferenceDeployment model, CreateInferenceDeploymentRequestDto dto) {
        switch (dto.getSource()) {
            case InferenceDeploymentHuggingFaceSourceDto(String modelName) ->
                    model.setSource(new HuggingFaceSource(modelName));
            default -> throw new IllegalArgumentException(
                    "Unsupported Inference deployment source type: " + dto.getSource().getClass().getName());
        }
    }

    @AfterMapping
    protected void setImageSource(@MappingTarget ImageBasedDeploymentDto dto, Deployment model) {
        dto.setSource(toDeploymentSourceDto(model));
    }

    @AfterMapping
    protected void setNimDtoSource(@MappingTarget NimDeploymentDto dto, NimDeployment model) {
        if (model.getSource() instanceof NgcRegistrySource ngcSource) {
            dto.setSource(new NimDeploymentNgcRegistrySourceDto(ngcSource.imageRef()));
        }
    }

    @AfterMapping
    protected void setInferenceDtoSource(@MappingTarget InferenceDeploymentDto dto, InferenceDeployment model) {
        if (model.getSource() instanceof HuggingFaceSource(String modelName)) {
            dto.setSource(new InferenceDeploymentHuggingFaceSourceDto(modelName));
        }
    }

    @Named("toDeploymentSourceDto")
    protected DeploymentSourceDto toDeploymentSourceDto(Deployment model) {
        return switch (model.getSource()) {
            case ImageReferenceSource(String imageReference) ->
                    new ImageReferenceDeploymentSourceDto(imageReference);
            case InternalImageSource(UUID imageDefinitionId, var type, String imageDefinitionName, String imageDefinitionVersion) ->
                    new InternalImageDeploymentSourceDto(imageDefinitionId, imageDefinitionName, imageDefinitionVersion);
            case null -> null;
            default -> null;
        };
    }

    private static void applyCreateImageSource(CreateDeployment model, CreateImageBasedDeploymentRequestDto dto) {
        switch (dto.getSource()) {
            case CreateInternalImageDeploymentSourceRequestDto(UUID imageDefinitionId, var typeDto, String name, String version) ->
                    model.setSource(new InternalImageSource(imageDefinitionId,
                            typeDto != null ? ImageType.valueOf(typeDto.name()) : null, name, version));
            case CreateImageReferenceDeploymentSourceRequestDto(String imageReference) ->
                    model.setSource(new ImageReferenceSource(imageReference));
            default -> throw new IllegalArgumentException(
                    "Unsupported deployment source type: " + dto.getSource().getClass().getName());
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

    public abstract ImageType toImageType(ImageTypeDto dto);

    public abstract ImageTypeDto toImageTypeDto(ImageType type);

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
    @Mapping(target = "source", source = "model", qualifiedByName = "toDeploymentSourceDto")
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
            return CommandLineUtils.parseCommandline(str);
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

        return list.stream()
                .map(CommandLineUtils::quoteArgument)
                .collect(Collectors.joining(" "));
    }

}
