package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.ImageDefinitionView;
import com.epam.aidial.deployment.manager.model.ImageDefinitionViewElement;
import com.epam.aidial.deployment.manager.web.dto.ImageDefinitionViewDto;
import com.epam.aidial.deployment.manager.web.dto.ImageDefinitionViewElementDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ImageDefinitionViewDtoMapper {

    @Mapping(source = "selectedId", target = "selectedName")
    ImageDefinitionViewDto toDto(ImageDefinitionView domain);

    @Mapping(source = "id", target = "name")
    ImageDefinitionViewElementDto toElementDto(ImageDefinitionViewElement domain);
}
