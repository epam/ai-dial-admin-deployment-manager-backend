package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.config.ExportRequest;
import com.epam.aidial.deployment.manager.model.config.SelectedItemsExportRequest;
import com.epam.aidial.deployment.manager.web.dto.config.ExportRequestDto;
import com.epam.aidial.deployment.manager.web.dto.config.SelectedItemsExportRequestDto;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassMapping;

import static org.mapstruct.SubclassExhaustiveStrategy.RUNTIME_EXCEPTION;

@Mapper(componentModel = "spring")
public interface ExportConfigMapper {

    @SubclassMapping(source = SelectedItemsExportRequestDto.class, target = SelectedItemsExportRequest.class)
    @BeanMapping(subclassExhaustiveStrategy = RUNTIME_EXCEPTION)
    ExportRequest toExportRequest(ExportRequestDto dto);
}
