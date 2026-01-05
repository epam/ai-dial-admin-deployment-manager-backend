package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.ImageDefinitionView;
import com.epam.aidial.deployment.manager.model.ImageDefinitionViewElement;
import com.epam.aidial.deployment.manager.web.dto.ImageDefinitionViewDto;
import com.epam.aidial.deployment.manager.web.dto.ImageDefinitionViewElementDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ImageDefinitionViewDtoMapper {

    ImageDefinitionViewDto toDto(ImageDefinitionView domain);

    ImageDefinitionViewElementDto toElementDto(ImageDefinitionViewElement domain); // is it needed?
}
