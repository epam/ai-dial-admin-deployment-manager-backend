package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceAccessedDomain;
import com.epam.aidial.deployment.manager.model.AccessVerdict;
import com.epam.aidial.deployment.manager.model.AccessedDomain;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface PersistenceAccessedDomainMapper {

    @Mapping(target = "verdict", source = "verdict", qualifiedByName = "verdictStringToEnum")
    AccessedDomain toDomain(PersistenceAccessedDomain entity);

    @Mapping(target = "verdict", source = "verdict", qualifiedByName = "verdictEnumToString")
    PersistenceAccessedDomain toEntity(AccessedDomain domain);

    @Named("verdictStringToEnum")
    default AccessVerdict verdictStringToEnum(String verdict) {
        return verdict == null ? null : AccessVerdict.valueOf(verdict);
    }

    @Named("verdictEnumToString")
    default String verdictEnumToString(AccessVerdict verdict) {
        return verdict == null ? null : verdict.name();
    }

    @Named("toDomainList")
    default List<AccessedDomain> toDomainList(List<PersistenceAccessedDomain> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Named("toEntityList")
    default List<PersistenceAccessedDomain> toEntityList(List<AccessedDomain> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(this::toEntity).collect(Collectors.toList());
    }
}
