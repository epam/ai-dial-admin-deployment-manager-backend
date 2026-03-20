package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.config.ExportRequest;
import com.epam.aidial.deployment.manager.model.config.SelectedItemsExportRequest;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.web.dto.config.ExportComponentInfoDto;
import com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentTypeDto;
import com.epam.aidial.deployment.manager.web.dto.config.ExportConfigPreviewDto;
import com.epam.aidial.deployment.manager.web.dto.config.ExportRequestDto;
import com.epam.aidial.deployment.manager.web.dto.config.SelectedItemsExportRequestDto;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassMapping;

import java.util.stream.Stream;

import static com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentTypeDto.ADAPTER_DEPLOYMENT;
import static com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentTypeDto.ADAPTER_IMAGE_DEFINITION;
import static com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentTypeDto.APPLICATION_DEPLOYMENT;
import static com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentTypeDto.APPLICATION_IMAGE_DEFINITION;
import static com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentTypeDto.INFERENCE_DEPLOYMENT;
import static com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentTypeDto.INTERCEPTOR_DEPLOYMENT;
import static com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentTypeDto.INTERCEPTOR_IMAGE_DEFINITION;
import static com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentTypeDto.MCP_DEPLOYMENT;
import static com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentTypeDto.MCP_IMAGE_DEFINITION;
import static com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentTypeDto.NIM_DEPLOYMENT;
import static org.mapstruct.SubclassExhaustiveStrategy.RUNTIME_EXCEPTION;

@Mapper(componentModel = "spring")
public abstract class ExportConfigMapper {

    @SubclassMapping(source = SelectedItemsExportRequestDto.class, target = SelectedItemsExportRequest.class)
    @BeanMapping(subclassExhaustiveStrategy = RUNTIME_EXCEPTION)
    public abstract ExportRequest toExportRequest(ExportRequestDto dto);

    public ExportConfigPreviewDto toExportConfigPreviewDto(ExportConfig config) {
        var imageDefinitions = Stream.of(
                config.getMcpImageDefinitions().values().stream()
                        .map(d -> toComponentInfoDto(d, MCP_IMAGE_DEFINITION)),
                config.getAdapterImageDefinitions().values().stream()
                        .map(d -> toComponentInfoDto(d, ADAPTER_IMAGE_DEFINITION)),
                config.getInterceptorImageDefinitions().values().stream()
                        .map(d -> toComponentInfoDto(d, INTERCEPTOR_IMAGE_DEFINITION)),
                config.getApplicationImageDefinitions().values().stream()
                        .map(d -> toComponentInfoDto(d, APPLICATION_IMAGE_DEFINITION))
        ).flatMap(s -> s).toList();

        var deployments = Stream.of(
                config.getMcpDeployments().values().stream()
                        .map(d -> toComponentInfoDto(d, MCP_DEPLOYMENT)),
                config.getAdapterDeployments().values().stream()
                        .map(d -> toComponentInfoDto(d, ADAPTER_DEPLOYMENT)),
                config.getInterceptorDeployments().values().stream()
                        .map(d -> toComponentInfoDto(d, INTERCEPTOR_DEPLOYMENT)),
                config.getApplicationDeployments().values().stream()
                        .map(d -> toComponentInfoDto(d, APPLICATION_DEPLOYMENT)),
                config.getNimDeployments().values().stream()
                        .map(d -> toComponentInfoDto(d, NIM_DEPLOYMENT)),
                config.getInferenceDeployments().values().stream()
                        .map(d -> toComponentInfoDto(d, INFERENCE_DEPLOYMENT))
        ).flatMap(s -> s).toList();

        return new ExportConfigPreviewDto(
                config.getGlobalImageBuildDomainWhitelist(),
                imageDefinitions,
                deployments
        );
    }

    private ExportComponentInfoDto toComponentInfoDto(ImageDefinition imageDef,
                                                      ExportConfigComponentTypeDto type) {
        return new ExportComponentInfoDto(
                imageDef.getId().toString(),
                imageDef.getName(),
                imageDef.getVersion(),
                imageDef.getDescription(),
                type
        );
    }

    private ExportComponentInfoDto toComponentInfoDto(Deployment deployment,
                                                      ExportConfigComponentTypeDto type) {
        return new ExportComponentInfoDto(
                deployment.getId(),
                deployment.getDisplayName(),
                null,
                deployment.getDescription(),
                type
        );
    }
}
