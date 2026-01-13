package com.epam.aidial.deployment.manager.huggingface.web.mapper;

import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModelSibling;
import com.epam.aidial.deployment.manager.huggingface.web.dto.HuggingFaceModelSiblingDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface HuggingFaceModelSiblingDtoMapper {

    HuggingFaceModelSiblingDto toDto(HuggingFaceModelSibling sibling);
}
