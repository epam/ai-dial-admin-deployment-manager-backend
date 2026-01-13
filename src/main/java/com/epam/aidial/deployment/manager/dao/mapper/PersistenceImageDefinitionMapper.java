package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.dao.entity.AdapterImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.ImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.InterceptorImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.McpImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceImageStatus;
import com.epam.aidial.deployment.manager.model.AdapterImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(
        componentModel = "spring",
        uses = {PersistenceImageSourceMapper.class, PersistenceEnvVarValueMapper.class, PersistenceTimestampMapper.class},
        subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION
)
public interface PersistenceImageDefinitionMapper {

    @SubclassMapping(source = AdapterImageDefinitionEntity.class, target = AdapterImageDefinition.class)
    @SubclassMapping(source = InterceptorImageDefinitionEntity.class, target = InterceptorImageDefinition.class)
    @SubclassMapping(source = McpImageDefinitionEntity.class, target = McpImageDefinition.class)
    ImageDefinition toImageDefinition(ImageDefinitionEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "type", source = "domain", qualifiedByName = "getTypeFromClass")
    @SubclassMapping(source = AdapterImageDefinition.class, target = AdapterImageDefinitionEntity.class)
    @SubclassMapping(source = InterceptorImageDefinition.class, target = InterceptorImageDefinitionEntity.class)
    @SubclassMapping(source = McpImageDefinition.class, target = McpImageDefinitionEntity.class)
    ImageDefinitionEntity toImageDefinitionEntity(ImageDefinition domain);

    PersistenceImageStatus toImageStatusDto(ImageStatus imageStatus);

    default void updateEntityFromDomain(ImageDefinition domain, ImageDefinitionEntity existingEntity) {
        var updatedEntity = toImageDefinitionEntity(domain);

        if (!existingEntity.getClass().equals(updatedEntity.getClass())) {
            throw new IllegalArgumentException("""
                    Updated entity and existing entity types must match.
                    ImageId: %s. Existing type: %s. Updated type: %s
                    """.formatted(
                    domain.getId(),
                    existingEntity.getClass().getSimpleName(),
                    updatedEntity.getClass().getSimpleName()
            )
            );
        }

        // do not update id, createdAt, updatedAt, buildStatus, imageName, buildLogs, builtAt, type
        existingEntity.setName(updatedEntity.getName());
        existingEntity.setDescription(updatedEntity.getDescription());
        existingEntity.setSource(updatedEntity.getSource());
        existingEntity.setLicense(updatedEntity.getLicense());
        existingEntity.setTopics(updatedEntity.getTopics());
        existingEntity.setVersion(updatedEntity.getVersion());
        existingEntity.setAuthor(updatedEntity.getAuthor());
        existingEntity.setAllowedDomains(updatedEntity.getAllowedDomains());

        // resetting build status on update
        existingEntity.setBuildStatus(PersistenceImageStatus.NOT_BUILT);

        if (existingEntity instanceof McpImageDefinitionEntity existingMcp
                && updatedEntity instanceof McpImageDefinitionEntity updatedMcp) {
            existingMcp.setTransportType(updatedMcp.getTransportType());
        }
    }

    @Named("getTypeFromClass")
    default String getTypeFromClass(ImageDefinition domain) {
        Class<?> entityClass = domain.getClass();
        if (McpImageDefinition.class.isAssignableFrom(entityClass)) {
            return "MCP";
        } else if (AdapterImageDefinition.class.isAssignableFrom(entityClass)) {
            return "ADAPTER";
        } else if (InterceptorImageDefinition.class.isAssignableFrom(entityClass)) {
            return "INTERCEPTOR";
        }
        return null;
    }
}
