package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.dao.audit.entity.AuditRevisionEntity;
import com.epam.aidial.deployment.manager.model.audit.AuditRevision;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PersistenceAuditRevisionMapper {

    AuditRevision toModel(AuditRevisionEntity entity);
}
