package com.epam.aidial.deployment.manager.dao.audit.mapper;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.entity.AdapterImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.ApplicationImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.DomainWhitelistEntity;
import com.epam.aidial.deployment.manager.dao.entity.InterceptorImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.McpImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.AdapterDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.ApplicationDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.InferenceDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.InterceptorDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.McpDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.NimDeploymentEntity;
import com.epam.aidial.deployment.manager.model.audit.ActivityResourceType;
import com.epam.aidial.deployment.manager.model.audit.ActivityType;
import org.hibernate.envers.RevisionType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@LogExecution
public class AuditActivityMapper {

    private static final Map<Class<?>, ActivityResourceType> ENTITY_TO_RESOURCE_TYPE = Map.ofEntries(
            Map.entry(AdapterDeploymentEntity.class, ActivityResourceType.AdapterDeployment),
            Map.entry(ApplicationDeploymentEntity.class, ActivityResourceType.ApplicationDeployment),
            Map.entry(InterceptorDeploymentEntity.class, ActivityResourceType.InterceptorDeployment),
            Map.entry(McpDeploymentEntity.class, ActivityResourceType.McpDeployment),
            Map.entry(NimDeploymentEntity.class, ActivityResourceType.NimDeployment),
            Map.entry(InferenceDeploymentEntity.class, ActivityResourceType.InferenceDeployment),
            Map.entry(AdapterImageDefinitionEntity.class, ActivityResourceType.AdapterImageDefinition),
            Map.entry(ApplicationImageDefinitionEntity.class, ActivityResourceType.ApplicationImageDefinition),
            Map.entry(InterceptorImageDefinitionEntity.class, ActivityResourceType.InterceptorImageDefinition),
            Map.entry(McpImageDefinitionEntity.class, ActivityResourceType.McpImageDefinition),
            Map.entry(DomainWhitelistEntity.class, ActivityResourceType.ImageBuildDomainWhitelist)
    );

    public ActivityResourceType mapResourceType(Class<?> entityClass) {
        ActivityResourceType resourceType = ENTITY_TO_RESOURCE_TYPE.get(entityClass);
        if (resourceType == null) {
            throw new IllegalArgumentException("Unmapped entity class: " + entityClass.getName());
        }
        return resourceType;
    }

    public ActivityType mapActivityType(RevisionType revisionType) {
        return switch (revisionType) {
            case ADD -> ActivityType.Create;
            case MOD -> ActivityType.Update;
            case DEL -> ActivityType.Delete;
        };
    }
}
