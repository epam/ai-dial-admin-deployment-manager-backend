package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceDeploymentStatus;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceEnvVar;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceImageType;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceResources;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceSensitiveEnvVar;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceSensitiveFileEnvVar;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceSimpleEnvVar;
import com.epam.aidial.deployment.manager.dao.entity.deployment.AdapterDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.DeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.InferenceDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.InterceptorDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.McpDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.NimDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.PersistenceInferenceDeploymentHuggingFaceSource;
import com.epam.aidial.deployment.manager.dao.entity.deployment.PersistenceInferenceDeploymentSource;
import com.epam.aidial.deployment.manager.dao.entity.deployment.PersistenceNimDeploymentNgcRegistrySource;
import com.epam.aidial.deployment.manager.dao.entity.deployment.PersistenceNimDeploymentSource;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SensitiveFileEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.AdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeploymentHuggingFaceSource;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeploymentSource;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeploymentNgcRegistrySource;
import com.epam.aidial.deployment.manager.model.deployment.NimDeploymentSource;
import org.apache.commons.collections4.MapUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(componentModel = "spring",
        uses = {PersistenceEnvVarValueMapper.class, PersistenceTimestampMapper.class, PersistenceEnvVarDefinitionMapper.class,
                PersistenceProbePropertiesMapper.class},
        subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public abstract class PersistenceDeploymentMapper {

    @Mapping(target = "metadata.envs.value", ignore = true)
    @SubclassMapping(source = McpDeploymentEntity.class, target = McpDeployment.class)
    @SubclassMapping(source = AdapterDeploymentEntity.class, target = AdapterDeployment.class)
    @SubclassMapping(source = InterceptorDeploymentEntity.class, target = InterceptorDeployment.class)
    @SubclassMapping(source = NimDeploymentEntity.class, target = NimDeployment.class)
    @SubclassMapping(source = InferenceDeploymentEntity.class, target = InferenceDeployment.class)
    public abstract Deployment toDomain(DeploymentEntity entity);

    @SubclassMapping(source = PersistenceInferenceDeploymentHuggingFaceSource.class, target = InferenceDeploymentHuggingFaceSource.class)
    protected abstract InferenceDeploymentSource toDomain(PersistenceInferenceDeploymentSource entity);

    @SubclassMapping(source = PersistenceNimDeploymentNgcRegistrySource.class, target = NimDeploymentNgcRegistrySource.class)
    protected abstract NimDeploymentSource toDomain(PersistenceNimDeploymentSource entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @SubclassMapping(source = McpDeployment.class, target = McpDeploymentEntity.class)
    @SubclassMapping(source = AdapterDeployment.class, target = AdapterDeploymentEntity.class)
    @SubclassMapping(source = InterceptorDeployment.class, target = InterceptorDeploymentEntity.class)
    @SubclassMapping(source = NimDeployment.class, target = NimDeploymentEntity.class)
    @SubclassMapping(source = InferenceDeployment.class, target = InferenceDeploymentEntity.class)
    public abstract DeploymentEntity toEntity(Deployment domain);

    @SubclassMapping(source = InferenceDeploymentHuggingFaceSource.class, target = PersistenceInferenceDeploymentHuggingFaceSource.class)
    protected abstract PersistenceInferenceDeploymentSource toEntity(InferenceDeploymentSource domain);

    @SubclassMapping(source = NimDeploymentNgcRegistrySource.class, target = PersistenceNimDeploymentNgcRegistrySource.class)
    protected abstract PersistenceNimDeploymentSource toEntity(NimDeploymentSource domain);

    protected abstract PersistenceDeploymentStatus toStatusEntity(DeploymentStatus domain);

    protected abstract ImageType toImageType(PersistenceImageType type);

    protected abstract PersistenceImageType toPersistenceImageType(ImageType type);

    @SubclassMapping(source = PersistenceSimpleEnvVar.class, target = SimpleEnvVar.class)
    @SubclassMapping(source = PersistenceSensitiveFileEnvVar.class, target = SensitiveFileEnvVar.class)
    @SubclassMapping(source = PersistenceSensitiveEnvVar.class, target = SensitiveEnvVar.class)
    protected abstract EnvVar toEnvVar(PersistenceEnvVar persistenceEnvVar);

    @SubclassMapping(source = SimpleEnvVar.class, target = PersistenceSimpleEnvVar.class)
    @SubclassMapping(source = SensitiveFileEnvVar.class, target = PersistenceSensitiveFileEnvVar.class)
    @SubclassMapping(source = SensitiveEnvVar.class, target = PersistenceSensitiveEnvVar.class)
    protected abstract PersistenceEnvVar toPersistenceEnvVar(EnvVar envVar);

    protected abstract SensitiveEnvVar toSensitiveEnvVar(PersistenceSensitiveEnvVar sensitiveEnvVar);

    protected abstract SensitiveFileEnvVar toSensitiveFileEnvVar(PersistenceSensitiveFileEnvVar sensitiveEnvVar);

    public void updateEntityFromDomain(Deployment domain, DeploymentEntity existingEntity) {
        var updatedEntity = toEntity(domain);

        if (!existingEntity.getClass().equals(updatedEntity.getClass())) {
            throw new IllegalArgumentException("""
                    Updated entity and existing entity types must match.
                    ImageId: %s. Existing type: %s. Updated type: %s
                    """.formatted(
                    domain.getId(),
                    existingEntity.getClass().getSimpleName(),
                    updatedEntity.getClass().getSimpleName()));
        }

        // do not update id, createdAt, updatedAt
        existingEntity.setDisplayName(updatedEntity.getDisplayName());
        existingEntity.setDescription(updatedEntity.getDescription());
        existingEntity.setImageDefinitionId(updatedEntity.getImageDefinitionId());
        existingEntity.setImageDefinitionType(updatedEntity.getImageDefinitionType());
        existingEntity.setImageDefinitionName(updatedEntity.getImageDefinitionName());
        existingEntity.setImageDefinitionVersion(updatedEntity.getImageDefinitionVersion());
        existingEntity.setUrl(updatedEntity.getUrl());
        existingEntity.setStatus(updatedEntity.getStatus());
        existingEntity.setContainerPort(updatedEntity.getContainerPort());
        existingEntity.setInitialScale(updatedEntity.getInitialScale());
        existingEntity.setMaxScale(updatedEntity.getMaxScale());
        existingEntity.setMinScale(updatedEntity.getMinScale());
        existingEntity.setEnvs(updatedEntity.getEnvs());
        existingEntity.setMetadata(updatedEntity.getMetadata());
        existingEntity.setResources(updatedEntity.getResources());
        existingEntity.setProbeProperties(updatedEntity.getProbeProperties());
        existingEntity.setAuthor(updatedEntity.getAuthor());
        existingEntity.setAllowedDomains(updatedEntity.getAllowedDomains());

        if (existingEntity instanceof McpDeploymentEntity existingMcp
                && updatedEntity instanceof McpDeploymentEntity updatedMcp) {
            existingMcp.setTransport(updatedMcp.getTransport());
            existingMcp.setMcpEndpointPath(updatedMcp.getMcpEndpointPath());
        }

        if (existingEntity instanceof NimDeploymentEntity existingNim
                && updatedEntity instanceof NimDeploymentEntity updatedNim) {
            existingNim.setSource(updatedNim.getSource());
            existingNim.setContainerGrpcPort(updatedNim.getContainerGrpcPort());
        }

        if (existingEntity instanceof InferenceDeploymentEntity existingInference
                && updatedEntity instanceof InferenceDeploymentEntity updatedInference) {
            existingInference.setModelFormat(updatedInference.getModelFormat());
            existingInference.setSource(updatedInference.getSource());
            existingInference.setCommand(updatedInference.getCommand());
            existingInference.setArgs(updatedInference.getArgs());
            existingInference.setScaling(updatedInference.getScaling());
        }
    }

    protected PersistenceResources toPersistenceResources(Resources resources) {
        if (resources.getLimits().isEmpty() && resources.getRequests().isEmpty()) {
            return null;
        }
        var limits = resources.getLimits().isEmpty() ? null : resources.getLimits();
        var requests = resources.getRequests().isEmpty() ? null : resources.getRequests();
        return new PersistenceResources(limits, requests);
    }

    protected Resources toResources(PersistenceResources persistenceResources) {
        if (persistenceResources == null) {
            return new Resources();
        }
        var limits = MapUtils.emptyIfNull(persistenceResources.getLimits());
        var requests = MapUtils.emptyIfNull(persistenceResources.getRequests());
        return new Resources(limits, requests);
    }

}
