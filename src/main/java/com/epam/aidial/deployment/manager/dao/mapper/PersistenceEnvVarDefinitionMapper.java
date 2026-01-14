package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceEnvVarDefinition;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PersistenceEnvVarDefinitionMapper {

    @Mapping(target = "value", ignore = true)
    EnvVarDefinition toDomain(PersistenceEnvVarDefinition entity);

    PersistenceEnvVarDefinition toEntity(EnvVarDefinition domain);
}
