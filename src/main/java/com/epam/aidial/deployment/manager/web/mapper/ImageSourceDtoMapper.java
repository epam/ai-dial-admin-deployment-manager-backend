package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
import com.epam.aidial.deployment.manager.model.ImageSource;
import com.epam.aidial.deployment.manager.web.dto.DockerImageSourceDto;
import com.epam.aidial.deployment.manager.web.dto.GitDockerfileImageSourceDto;
import com.epam.aidial.deployment.manager.web.dto.ImageSourceDto;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(componentModel = "spring", uses = {ExternalRegistryRefDtoMapper.class},
        subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface ImageSourceDtoMapper {

    @SubclassMapping(source = DockerImageSourceDto.class, target = DockerImageSource.class)
    @SubclassMapping(source = GitDockerfileImageSourceDto.class, target = GitDockerfileImageSource.class)
    ImageSource toImageSource(ImageSourceDto imageSourceDto);

    DockerImageSource toDockerImageSource(DockerImageSourceDto dockerImageSource);

    GitDockerfileImageSource toGitDockerfileImageSource(GitDockerfileImageSourceDto gitDockerfileImageSource);

    @SubclassMapping(source = DockerImageSource.class, target = DockerImageSourceDto.class)
    @SubclassMapping(source = GitDockerfileImageSource.class, target = GitDockerfileImageSourceDto.class)
    ImageSourceDto toImageSourceDto(ImageSource imageSource);

}
