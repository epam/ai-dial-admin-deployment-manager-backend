package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceExternalRegistryRef;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceGenericRef;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceGitHubRef;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceMcpRegistryRef;
import com.epam.aidial.deployment.manager.model.ExternalRegistryRef;
import com.epam.aidial.deployment.manager.model.GenericRef;
import com.epam.aidial.deployment.manager.model.GitHubRef;
import com.epam.aidial.deployment.manager.model.McpRegistryRef;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(componentModel = "spring", subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface PersistenceExternalRegistryRefMapper {

    @SubclassMapping(source = McpRegistryRef.class, target = PersistenceMcpRegistryRef.class)
    @SubclassMapping(source = GitHubRef.class, target = PersistenceGitHubRef.class)
    @SubclassMapping(source = GenericRef.class, target = PersistenceGenericRef.class)
    PersistenceExternalRegistryRef toEntity(ExternalRegistryRef model);

    @SubclassMapping(source = PersistenceMcpRegistryRef.class, target = McpRegistryRef.class)
    @SubclassMapping(source = PersistenceGitHubRef.class, target = GitHubRef.class)
    @SubclassMapping(source = PersistenceGenericRef.class, target = GenericRef.class)
    ExternalRegistryRef toDomain(PersistenceExternalRegistryRef entity);
}
