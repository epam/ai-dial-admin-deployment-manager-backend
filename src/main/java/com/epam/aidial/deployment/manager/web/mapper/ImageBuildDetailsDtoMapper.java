package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.web.dto.ImageBuildDetailsDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ImageBuildDetailsDtoMapper {

    @Mapping(source = "buildStatus", target = "status")
    @Mapping(source = "buildLogs", target = "logs")
    @Mapping(source = "id", target = "imageDefinitionName")
    ImageBuildDetailsDto toDto(ImageDefinition imageDefinition);

}
