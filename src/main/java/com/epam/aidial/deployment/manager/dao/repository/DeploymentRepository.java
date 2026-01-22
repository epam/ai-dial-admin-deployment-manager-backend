package com.epam.aidial.deployment.manager.dao.repository;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceDeploymentStatus;
import com.epam.aidial.deployment.manager.dao.entity.deployment.AdapterDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.DeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.InferenceDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.InterceptorDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.McpDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.NimDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.jpa.DeploymentJpaRepository;
import com.epam.aidial.deployment.manager.dao.mapper.PersistenceDeploymentMapper;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.web.dto.DeploymentTypeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DeploymentRepository {

    private final DeploymentJpaRepository deploymentJpaRepository;
    private final PersistenceDeploymentMapper mapper;

    public List<Deployment> getAll() {
        return deploymentJpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    public List<Deployment> getAllByImageDefinitionId(UUID imageDefinitionId) {
        return deploymentJpaRepository.findAllByImageDefinitionId(imageDefinitionId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    public List<Deployment> getAllByType(DeploymentTypeDto type) {
        Class<? extends DeploymentEntity> entityClass = toEntityClass(type);
        return deploymentJpaRepository.findAllByType(entityClass).stream()
                .map(mapper::toDomain)
                .toList();
    }

    public List<Deployment> getAllByType(List<DeploymentTypeDto> types) {
        List<Class<? extends DeploymentEntity>> entityClasses = types.stream()
                .map(this::toEntityClass)
                .collect(Collectors.toList());

        return deploymentJpaRepository.findAllByTypes(entityClasses).stream()
                .map(mapper::toDomain)
                .toList();
    }

    private Class<? extends DeploymentEntity> toEntityClass(DeploymentTypeDto type) {
        return switch (type) {
            case MCP -> McpDeploymentEntity.class;
            case ADAPTER -> AdapterDeploymentEntity.class;
            case INTERCEPTOR -> InterceptorDeploymentEntity.class;
            case NIM -> NimDeploymentEntity.class;
            case INFERENCE -> InferenceDeploymentEntity.class;
        };
    }

    public Optional<Deployment> getById(String id) {
        return deploymentJpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Deployment save(Deployment deployment) {
        var entity = mapper.toEntity(deployment);
        var savedEntity = deploymentJpaRepository.saveAndFlush(entity);
        log.debug("Deployment '{}' saved successfully", savedEntity.getId());
        return mapper.toDomain(savedEntity);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Deployment update(String id, Deployment updatedDeployment) {
        var existingEntity = findDeploymentById(id);
        updateEntityFromDomain(existingEntity, updatedDeployment);

        var savedEntity = deploymentJpaRepository.saveAndFlush(existingEntity);
        log.debug("Deployment '{}' updated successfully", id);
        return mapper.toDomain(savedEntity);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public boolean conditionalUpdate(String id, Predicate<Deployment> condition, Consumer<Deployment> mutator) {
        var existingEntity = findDeploymentById(id);
        var domainObject = mapper.toDomain(existingEntity);

        if (condition.test(domainObject)) {
            log.debug("Deployment '{}' matched condition, updating", id);
            mutator.accept(domainObject);
            updateEntityFromDomain(existingEntity, domainObject);
            deploymentJpaRepository.save(existingEntity);
            log.debug("Deployment '{}' updated", id);
            return true;
        } else {
            log.debug("Deployment '{}' did not match condition, not updating", id);
            return false;
        }
    }

    public void deleteById(String id) {
        deploymentJpaRepository.deleteById(id);
        log.debug("Deployment '{}' deleted successfully", id);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void updateImageDefinitionForDeployments(ImageDefinition imageDefinition, List<String> deploymentIds) {
        deploymentJpaRepository.updateImageDefinitionIdForDeployments(imageDefinition.getId(), imageDefinition.getName(),
                imageDefinition.getVersion(), deploymentIds);
        log.debug("Image definition updated for deployments: {}", deploymentIds);
    }

    @Transactional
    public void updateStatus(String id, DeploymentStatus status) {
        deploymentJpaRepository.updateStatus(id, PersistenceDeploymentStatus.valueOf(status.name()));
        log.debug("Status updated for deployment '{}' to: {}", id, status);
    }

    private void updateEntityFromDomain(DeploymentEntity entity, Deployment domain) {
        mapper.updateEntityFromDomain(domain, entity);
    }

    private DeploymentEntity findDeploymentById(String id) {
        return deploymentJpaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Deployment not found by id: '%s'".formatted(id)));
    }

}
