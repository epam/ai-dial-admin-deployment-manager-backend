package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.AccessedDomain;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.web.dto.AccessedDomainDto;
import com.epam.aidial.deployment.manager.web.dto.ImageBuildDetailsDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ImageBuildDetailsDtoMapper {

    @Mapping(source = "buildStatus", target = "status")
    @Mapping(source = "buildLogs", target = "logs")
    @Mapping(source = "id", target = "imageDefinitionId")
    @Mapping(source = "accessedDomains", target = "accessedDomains", qualifiedByName = "mapAccessedDomains")
    ImageBuildDetailsDto toDto(ImageDefinition imageDefinition);

    @Named("mapAccessedDomains")
    default List<AccessedDomainDto> mapAccessedDomains(List<AccessedDomain> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(this::toAccessedDomainDto).toList();
    }

    default AccessedDomainDto toAccessedDomainDto(AccessedDomain domain) {
        if (domain == null) {
            return null;
        }
        return new AccessedDomainDto(
                domain.getDomain(),
                domain.getVerdict() != null ? domain.getVerdict().name() : null
        );
    }
}
