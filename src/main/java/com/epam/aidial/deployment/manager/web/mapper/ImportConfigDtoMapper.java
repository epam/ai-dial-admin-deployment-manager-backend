package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.config.ImportAction;
import com.epam.aidial.deployment.manager.model.config.ImportComponent;
import com.epam.aidial.deployment.manager.model.config.ImportConfigPreview;
import com.epam.aidial.deployment.manager.web.dto.AdapterImageDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.ApplicationImageDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.InterceptorImageDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.McpImageDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.config.ImportActionDto;
import com.epam.aidial.deployment.manager.web.dto.config.ImportComponentDto;
import com.epam.aidial.deployment.manager.web.dto.config.ImportConfigPreviewDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.AdapterDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.ApplicationDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.InferenceDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.InterceptorDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.McpDeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.NimDeploymentDto;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.function.Function;

@Mapper(componentModel = "spring")
public abstract class ImportConfigDtoMapper {

    @Autowired
    private ImageDefinitionDtoMapper imageDefinitionDtoMapper;
    @Autowired
    private DeploymentDtoMapper deploymentDtoMapper;

    public abstract ImportActionDto toActionDto(ImportAction action);

    public ImportConfigPreviewDto toImportConfigPreviewDto(ImportConfigPreview preview) {
        return new ImportConfigPreviewDto(
                mapList(preview.getMcpImageDefinitions(),
                        def -> (McpImageDefinitionDto) imageDefinitionDtoMapper.toImageDefinitionDto(def)),
                mapList(preview.getAdapterImageDefinitions(),
                        def -> (AdapterImageDefinitionDto) imageDefinitionDtoMapper.toImageDefinitionDto(def)),
                mapList(preview.getInterceptorImageDefinitions(),
                        def -> (InterceptorImageDefinitionDto) imageDefinitionDtoMapper.toImageDefinitionDto(def)),
                mapList(preview.getApplicationImageDefinitions(),
                        def -> (ApplicationImageDefinitionDto) imageDefinitionDtoMapper.toImageDefinitionDto(def)),
                mapList(preview.getMcpDeployments(),
                        dep -> (McpDeploymentDto) deploymentDtoMapper.toDeploymentDto(dep)),
                mapList(preview.getAdapterDeployments(),
                        dep -> (AdapterDeploymentDto) deploymentDtoMapper.toDeploymentDto(dep)),
                mapList(preview.getInterceptorDeployments(),
                        dep -> (InterceptorDeploymentDto) deploymentDtoMapper.toDeploymentDto(dep)),
                mapList(preview.getApplicationDeployments(),
                        dep -> (ApplicationDeploymentDto) deploymentDtoMapper.toDeploymentDto(dep)),
                mapList(preview.getNimDeployments(),
                        dep -> (NimDeploymentDto) deploymentDtoMapper.toDeploymentDto(dep)),
                mapList(preview.getInferenceDeployments(),
                        dep -> (InferenceDeploymentDto) deploymentDtoMapper.toDeploymentDto(dep)),
                toComponentDto(preview.getGlobalImageBuildDomainWhitelist(), c -> c),
                preview.getValidationErrors()
        );
    }

    private <T, D> List<ImportComponentDto<D>> mapList(List<ImportComponent<T>> components, Function<T, D> fn) {
        if (components == null) {
            return List.of();
        }
        return components.stream()
                .map(c -> toComponentDto(c, fn))
                .toList();
    }

    private <T, D> ImportComponentDto<D> toComponentDto(ImportComponent<T> component, Function<T, D> fn) {
        if (component == null) {
            return null;
        }
        return new ImportComponentDto<>(
                toActionDto(component.getAction()),
                component.getPrev() != null ? fn.apply(component.getPrev()) : null,
                component.getNext() != null ? fn.apply(component.getNext()) : null
        );
    }
}
