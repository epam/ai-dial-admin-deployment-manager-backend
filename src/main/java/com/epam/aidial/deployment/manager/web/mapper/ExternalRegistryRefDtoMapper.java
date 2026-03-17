package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.ExternalRegistryRef;
import com.epam.aidial.deployment.manager.model.GenericRef;
import com.epam.aidial.deployment.manager.model.GitHubRef;
import com.epam.aidial.deployment.manager.model.McpRegistryRef;
import com.epam.aidial.deployment.manager.web.dto.ExternalRegistryRefDto;
import com.epam.aidial.deployment.manager.web.dto.GenericRefDto;
import com.epam.aidial.deployment.manager.web.dto.GitHubRefDto;
import com.epam.aidial.deployment.manager.web.dto.McpRegistryRefDto;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(componentModel = "spring", subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface ExternalRegistryRefDtoMapper {

    @SubclassMapping(source = McpRegistryRefDto.class, target = McpRegistryRef.class)
    @SubclassMapping(source = GitHubRefDto.class, target = GitHubRef.class)
    @SubclassMapping(source = GenericRefDto.class, target = GenericRef.class)
    ExternalRegistryRef toModel(ExternalRegistryRefDto dto);

    @SubclassMapping(source = McpRegistryRef.class, target = McpRegistryRefDto.class)
    @SubclassMapping(source = GitHubRef.class, target = GitHubRefDto.class)
    @SubclassMapping(source = GenericRef.class, target = GenericRefDto.class)
    ExternalRegistryRefDto toDto(ExternalRegistryRef model);
}
