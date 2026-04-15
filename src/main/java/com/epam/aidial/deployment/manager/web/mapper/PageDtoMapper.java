package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.page.PageRequestModel;
import com.epam.aidial.deployment.manager.web.dto.page.PageRequestDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PageDtoMapper {

    PageRequestModel toPageRequestModel(PageRequestDto dto);
}
