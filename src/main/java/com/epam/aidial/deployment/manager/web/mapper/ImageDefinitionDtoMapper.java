package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.AdapterImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.web.dto.AdapterImageDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.AdapterImageDefinitionRequestDto;
import com.epam.aidial.deployment.manager.web.dto.BaseImageDetailsDto;
import com.epam.aidial.deployment.manager.web.dto.ImageDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.ImageDefinitionRequestDto;
import com.epam.aidial.deployment.manager.web.dto.InterceptorImageDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.InterceptorImageDefinitionRequestDto;
import com.epam.aidial.deployment.manager.web.dto.McpImageDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.McpImageDefinitionRequestDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(
        componentModel = "spring",
        uses = {ImageSourceDtoMapper.class, EnvVarValueDtoMapper.class},
        subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION
)
public interface ImageDefinitionDtoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "imageName", ignore = true)
    @Mapping(target = "buildStatus", ignore = true)
    @Mapping(target = "buildLogs", ignore = true)
    @Mapping(target = "builtAt", ignore = true)
    @SubclassMapping(source = McpImageDefinitionRequestDto.class, target = McpImageDefinition.class)
    @SubclassMapping(source = AdapterImageDefinitionRequestDto.class, target = AdapterImageDefinition.class)
    @SubclassMapping(source = InterceptorImageDefinitionRequestDto.class, target = InterceptorImageDefinition.class)
    ImageDefinition toImageDefinition(ImageDefinitionRequestDto requestDto);

    @SubclassMapping(source = McpImageDefinition.class, target = McpImageDefinitionDto.class)
    @SubclassMapping(source = AdapterImageDefinition.class, target = AdapterImageDefinitionDto.class)
    @SubclassMapping(source = InterceptorImageDefinition.class, target = InterceptorImageDefinitionDto.class)
    ImageDefinitionDto toImageDefinitionDto(ImageDefinition imageDefinition);

    @SubclassMapping(source = McpImageDefinition.class, target = McpImageDefinitionRequestDto.class)
    @SubclassMapping(source = AdapterImageDefinition.class, target = AdapterImageDefinitionRequestDto.class)
    @SubclassMapping(source = InterceptorImageDefinition.class, target = InterceptorImageDefinitionRequestDto.class)
    ImageDefinitionRequestDto toImageDefinitionRequestDto(ImageDefinition imageDefinition);

    @Mapping(source = "buildStatus", target = "status")
    BaseImageDetailsDto toBaseImageDetailsDto(ImageDefinition imageDefinition);

}
