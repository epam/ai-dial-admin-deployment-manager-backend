package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.DomainEntry;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.web.dto.DomainEntryDto;
import com.epam.aidial.deployment.manager.web.dto.ImageBuildDetailsDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ImageBuildDetailsDtoMapper {

    @Mapping(source = "imageDefinition.buildStatus", target = "status")
    @Mapping(source = "imageDefinition.buildLogs", target = "logs")
    @Mapping(source = "imageDefinition.id", target = "imageDefinitionId")
    @Mapping(source = "domainEntries", target = "domains")
    ImageBuildDetailsDto toDto(ImageDefinition imageDefinition, List<DomainEntry> domainEntries);

    List<DomainEntryDto> toDomainDtos(List<DomainEntry> entries);

    @Mapping(target = "domain", source = "domain")
    @Mapping(target = "verdict", source = "verdict")
    DomainEntryDto toDomainDto(DomainEntry entry);

}
