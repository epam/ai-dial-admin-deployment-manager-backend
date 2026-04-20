package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.audit.AuditActivity;
import com.epam.aidial.deployment.manager.web.dto.audit.AuditActivityDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditActivityDtoMapper {

    AuditActivityDto toDto(AuditActivity model);
}
