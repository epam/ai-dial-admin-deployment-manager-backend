package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceDockerImageSource;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceGitDockerfileImageSource;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceImageSource;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
import com.epam.aidial.deployment.manager.model.ImageSource;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(componentModel = "spring", uses = {PersistenceExternalRegistryRefMapper.class},
        subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface PersistenceImageSourceMapper {

    @SubclassMapping(source = PersistenceDockerImageSource.class, target = DockerImageSource.class)
    @SubclassMapping(source = PersistenceGitDockerfileImageSource.class, target = GitDockerfileImageSource.class)
    ImageSource toImageSource(PersistenceImageSource imageSourceDto);

    @SubclassMapping(source = DockerImageSource.class, target = PersistenceDockerImageSource.class)
    @SubclassMapping(source = GitDockerfileImageSource.class, target = PersistenceGitDockerfileImageSource.class)
    PersistenceImageSource toImageSourceDto(ImageSource imageSource);

}
