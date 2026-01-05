package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.dao.entity.DisposableResourceEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PersistenceDisposableResourceMapper {

    List<DisposableResource> toModel(List<DisposableResourceEntity> entities);

    DisposableResource toModel(DisposableResourceEntity entity);

    List<DisposableResourceEntity> toEntity(List<DisposableResource> models);

    DisposableResourceEntity toEntity(DisposableResource model);

}