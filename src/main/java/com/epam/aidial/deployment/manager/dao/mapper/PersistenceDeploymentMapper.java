package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceDeploymentStatus;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceEnvVar;
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
import com.epam.aidial.deployment.manager.dao.entity.deployment.PersistenceHuggingFaceSource;
import com.epam.aidial.deployment.manager.dao.entity.deployment.PersistenceImageReferenceSource;
import com.epam.aidial.deployment.manager.dao.entity.deployment.PersistenceInternalImageSource;
import com.epam.aidial.deployment.manager.dao.entity.deployment.PersistenceNgcRegistrySource;
import com.epam.aidial.deployment.manager.dao.entity.deployment.PersistenceSource;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SensitiveFileEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.AdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.HuggingFaceSource;
import com.epam.aidial.deployment.manager.model.deployment.ImageReferenceSource;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InternalImageSource;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NgcRegistrySource;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Source;
import org.apache.commons.collections4.MapUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(componentModel = "spring",
        uses = {PersistenceEnvVarValueMapper.class, PersistenceTimestampMapper.class, PersistenceEnvVarDefinitionMapper.class,
                PersistenceProbePropertiesMapper.class, PersistenceExternalRegistryRefMapper.class},
        subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public abstract class PersistenceDeploymentMapper {

    @Mapping(target = "metadata.envs.value", ignore = true)
    @SubclassMapping(source = McpDeploymentEntity.class, target = McpDeployment.class)
    @SubclassMapping(source = AdapterDeploymentEntity.class, target = AdapterDeployment.class)
    @SubclassMapping(source = InterceptorDeploymentEntity.class, target = InterceptorDeployment.class)
    @SubclassMapping(source = NimDeploymentEntity.class, target = NimDeployment.class)
    @SubclassMapping(source = InferenceDeploymentEntity.class, target = InferenceDeployment.class)
    public abstract Deployment toDomain(DeploymentEntity entity);

    @SubclassMapping(source = PersistenceInternalImageSource.class, target = InternalImageSource.class)
    @SubclassMapping(source = PersistenceImageReferenceSource.class, target = ImageReferenceSource.class)
    @SubclassMapping(source = PersistenceNgcRegistrySource.class, target = NgcRegistrySource.class)
    @SubclassMapping(source = PersistenceHuggingFaceSource.class, target = HuggingFaceSource.class)
    protected abstract Source toDomain(PersistenceSource entity);

    @Mapping(target = "imageDefinitionId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @SubclassMapping(source = McpDeployment.class, target = McpDeploymentEntity.class)
    @SubclassMapping(source = AdapterDeployment.class, target = AdapterDeploymentEntity.class)
    @SubclassMapping(source = InterceptorDeployment.class, target = InterceptorDeploymentEntity.class)
    @SubclassMapping(source = NimDeployment.class, target = NimDeploymentEntity.class)
    @SubclassMapping(source = InferenceDeployment.class, target = InferenceDeploymentEntity.class)
    public abstract DeploymentEntity toEntity(Deployment domain);

    @SubclassMapping(source = InternalImageSource.class, target = PersistenceInternalImageSource.class)
    @SubclassMapping(source = ImageReferenceSource.class, target = PersistenceImageReferenceSource.class)
    @SubclassMapping(source = NgcRegistrySource.class, target = PersistenceNgcRegistrySource.class)
    @SubclassMapping(source = HuggingFaceSource.class, target = PersistenceHuggingFaceSource.class)
    protected abstract PersistenceSource toEntity(Source domain);

    @AfterMapping
    protected void setImageDefinitionId(Deployment domain, @MappingTarget DeploymentEntity entity) {
        if (domain.getSource() instanceof InternalImageSource source) {
            entity.setImageDefinitionId(source.imageDefinitionId());
        }
    }

    protected abstract PersistenceDeploymentStatus toStatusEntity(DeploymentStatus domain);

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
        existingEntity.setSource(updatedEntity.getSource());
        existingEntity.setImageDefinitionId(updatedEntity.getImageDefinitionId());
        existingEntity.setUrl(updatedEntity.getUrl());
        existingEntity.setStatus(updatedEntity.getStatus());
        existingEntity.setContainerPort(updatedEntity.getContainerPort());
        existingEntity.setEnvs(updatedEntity.getEnvs());
        existingEntity.setMetadata(updatedEntity.getMetadata());
        existingEntity.setResources(updatedEntity.getResources());
        existingEntity.setProbeProperties(updatedEntity.getProbeProperties());
        existingEntity.setAuthor(updatedEntity.getAuthor());
        existingEntity.setAllowedDomains(updatedEntity.getAllowedDomains());
        existingEntity.setScaling(updatedEntity.getScaling());
        existingEntity.setTopics(updatedEntity.getTopics());
        existingEntity.setCommand(updatedEntity.getCommand());
        existingEntity.setArgs(updatedEntity.getArgs());

        if (existingEntity instanceof McpDeploymentEntity existingMcp
                && updatedEntity instanceof McpDeploymentEntity updatedMcp) {
            existingMcp.setTransport(updatedMcp.getTransport());
            existingMcp.setMcpEndpointPath(updatedMcp.getMcpEndpointPath());
        }

        if (existingEntity instanceof NimDeploymentEntity existingNim
                && updatedEntity instanceof NimDeploymentEntity updatedNim) {
            existingNim.setContainerGrpcPort(updatedNim.getContainerGrpcPort());
        }

        if (existingEntity instanceof InferenceDeploymentEntity existingInference
                && updatedEntity instanceof InferenceDeploymentEntity updatedInference) {
            existingInference.setModelFormat(updatedInference.getModelFormat());
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
