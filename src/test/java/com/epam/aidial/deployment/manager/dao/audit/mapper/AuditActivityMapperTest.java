package com.epam.aidial.deployment.manager.dao.audit.mapper;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditActivityMapperTest {

    private final AuditActivityMapper mapper = new AuditActivityMapper();

    static Stream<Arguments> entityToResourceTypeMappings() {
        return Stream.of(
                Arguments.of(AdapterDeploymentEntity.class, ActivityResourceType.AdapterDeployment),
                Arguments.of(ApplicationDeploymentEntity.class, ActivityResourceType.ApplicationDeployment),
                Arguments.of(InterceptorDeploymentEntity.class, ActivityResourceType.InterceptorDeployment),
                Arguments.of(McpDeploymentEntity.class, ActivityResourceType.McpDeployment),
                Arguments.of(NimDeploymentEntity.class, ActivityResourceType.NimDeployment),
                Arguments.of(InferenceDeploymentEntity.class, ActivityResourceType.InferenceDeployment),
                Arguments.of(AdapterImageDefinitionEntity.class, ActivityResourceType.AdapterImageDefinition),
                Arguments.of(ApplicationImageDefinitionEntity.class, ActivityResourceType.ApplicationImageDefinition),
                Arguments.of(InterceptorImageDefinitionEntity.class, ActivityResourceType.InterceptorImageDefinition),
                Arguments.of(McpImageDefinitionEntity.class, ActivityResourceType.McpImageDefinition),
                Arguments.of(DomainWhitelistEntity.class, ActivityResourceType.ImageBuildDomainWhitelist)
        );
    }

    @ParameterizedTest
    @MethodSource("entityToResourceTypeMappings")
    void mapResourceType_mapsAllEntityClasses(Class<?> entityClass, ActivityResourceType expected) {
        assertThat(mapper.mapResourceType(entityClass)).isEqualTo(expected);
    }

    @Test
    void mapResourceType_throwsForUnmappedClass() {
        assertThatThrownBy(() -> mapper.mapResourceType(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unmapped entity class");
    }

    static Stream<Arguments> revisionTypeToActivityTypeMappings() {
        return Stream.of(
                Arguments.of(RevisionType.ADD, ActivityType.Create),
                Arguments.of(RevisionType.MOD, ActivityType.Update),
                Arguments.of(RevisionType.DEL, ActivityType.Delete)
        );
    }

    @ParameterizedTest
    @MethodSource("revisionTypeToActivityTypeMappings")
    void mapActivityType_mapsAllRevisionTypes(RevisionType revisionType, ActivityType expected) {
        assertThat(mapper.mapActivityType(revisionType)).isEqualTo(expected);
    }
}
