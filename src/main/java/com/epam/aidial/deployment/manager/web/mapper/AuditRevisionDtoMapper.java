package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.audit.AuditRevision;
import com.epam.aidial.deployment.manager.web.dto.audit.AuditRevisionDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditRevisionDtoMapper {

    AuditRevisionDto toDto(AuditRevision model);
}
