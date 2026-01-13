package com.epam.aidial.deployment.manager.huggingface.web.mapper;

import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModel;
import com.epam.aidial.deployment.manager.huggingface.web.dto.HuggingFaceModelDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = HuggingFaceModelSiblingDtoMapper.class)
public interface HuggingFaceModelDtoMapper {

    HuggingFaceModelDto toDto(HuggingFaceModel model);
}
