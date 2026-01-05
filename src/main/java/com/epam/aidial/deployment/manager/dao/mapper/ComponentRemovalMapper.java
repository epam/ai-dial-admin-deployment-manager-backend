package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.dao.entity.ComponentRemovalEntity;
import com.epam.aidial.deployment.manager.model.ComponentRemoval;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ComponentRemovalMapper {

    @Mapping(target = "id", source = "componentId.id")
    @Mapping(target = "type", source = "componentId.type")
    ComponentRemoval toDomain(ComponentRemovalEntity entity);

    @InheritInverseConfiguration
    ComponentRemovalEntity toEntity(ComponentRemoval domain);

}