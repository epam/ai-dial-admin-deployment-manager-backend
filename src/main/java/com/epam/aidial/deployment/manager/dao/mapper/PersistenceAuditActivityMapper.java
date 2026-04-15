package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.dao.audit.entity.AuditActivityEntity;
import com.epam.aidial.deployment.manager.model.audit.AuditActivity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PersistenceAuditActivityMapper {

    AuditActivity toModel(AuditActivityEntity entity);
}
