package com.epam.aidial.deployment.manager.dao.repository;

import com.epam.aidial.deployment.manager.dao.entity.AdapterImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.ImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.InterceptorImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.McpImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.NimImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.jpa.ImageDefinitionJpaRepository;
import com.epam.aidial.deployment.manager.dao.mapper.PersistenceImageDefinitionMapper;
import com.epam.aidial.deployment.manager.dao.mapper.PersistenceImageDefinitionViewMapper;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageDefinitionView;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.web.dto.DeploymentTypeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ImageDefinitionRepository {

    private final ImageDefinitionJpaRepository imageDefinitionJpaRepository;
    private final PersistenceImageDefinitionViewMapper viewMapper;
    private final PersistenceImageDefinitionMapper mapper;

    @Value("${app.image-build-logs-size-limit}")
    private final int buildLogsSizeLimit;

    public Collection<ImageDefinition> getAllImageDefinitions() {
        return imageDefinitionJpaRepository.findAll().stream()
                .map(mapper::toImageDefinition)
                .collect(Collectors.toList());
    }

    public Collection<ImageDefinition> getAllImageDefinitionsByType(DeploymentTypeDto type) {
        var entityClazz = detectEntityClazz(type);
        return imageDefinitionJpaRepository.findAllByType(entityClazz).stream()
                .map(mapper::toImageDefinition)
                .collect(Collectors.toList());
    }

    public Collection<ImageDefinition> getAllImageDefinitionsByName(String name) {
        return imageDefinitionJpaRepository.findAllByName(name).stream()
                .map(mapper::toImageDefinition)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ImageDefinition> getImageDefinitionById(UUID id) {
        return imageDefinitionJpaRepository.findById(id)
                .map(mapper::toImageDefinition);
    }

    public Optional<ImageDefinition> getImageDefinitionForUpdateById(UUID id) {
        return imageDefinitionJpaRepository.findForUpdateById(id)
                .map(mapper::toImageDefinition);
    }

    public ImageDefinition saveImageDefinition(ImageDefinition imageDefinition) {
        var entity = mapper.toImageDefinitionEntity(imageDefinition);
        var savedEntity = imageDefinitionJpaRepository.saveAndFlush(entity);
        return mapper.toImageDefinition(savedEntity);
    }

    public void deleteImageDefinitionById(UUID id) {
        imageDefinitionJpaRepository.deleteById(id);
    }

    public ImageDefinition updateImageDefinition(UUID id, ImageDefinition updatedImageDefinition) {
        var existingEntity = imageDefinitionJpaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Image definition not found by id: %s".formatted(id)));

        mapper.updateEntityFromDomain(updatedImageDefinition, existingEntity);

        var savedEntity = imageDefinitionJpaRepository.saveAndFlush(existingEntity);
        return mapper.toImageDefinition(savedEntity);
    }

    public void updateBuildStatus(UUID id, ImageStatus buildStatus) {
        var persistenceStatus = mapper.toImageStatusDto(buildStatus);
        imageDefinitionJpaRepository.updateBuildStatus(id, persistenceStatus);
    }

    public void setImageName(UUID id, String imageName) {
        imageDefinitionJpaRepository.setImageName(id, imageName);
    }

    public void setBuiltAt(UUID id, Long builtAt) {
        imageDefinitionJpaRepository.setBuiltAt(id, builtAt);
    }

    public void resetBuildLogs(UUID id) {
        imageDefinitionJpaRepository.resetBuildLogs(id);
    }

    public void addBuildLogs(UUID id, List<String> logs) {
        var entity = imageDefinitionJpaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Image definition not found by id: %s".formatted(id)));

        if (entity.getBuildLogs() == null) {
            entity.setBuildLogs(new ArrayList<>());
        }

        List<String> buildLogs = entity.getBuildLogs();
        buildLogs.addAll(logs);

        int excess = buildLogs.size() - buildLogsSizeLimit;
        if (excess > 0) {
            buildLogs.subList(0, excess).clear(); // Remove oldest entries
        }

        imageDefinitionJpaRepository.saveAndFlush(entity);
    }

    public List<ImageDefinitionView> getAllImageDefinitionViews() {
        var imageDefinitions = imageDefinitionJpaRepository.findAll();
        return viewMapper.toViews(imageDefinitions);
    }

    public List<ImageDefinitionView> getAllImageDefinitionViewsByType(DeploymentTypeDto type) {
        var entityClazz = detectEntityClazz(type);
        var imageDefinitions = imageDefinitionJpaRepository.findAllByType(entityClazz);
        return viewMapper.toViews(imageDefinitions);
    }

    private static Class<? extends ImageDefinitionEntity> detectEntityClazz(DeploymentTypeDto type) {
        return switch (type) {
            case MCP -> McpImageDefinitionEntity.class;
            case ADAPTER -> AdapterImageDefinitionEntity.class;
            case INTERCEPTOR -> InterceptorImageDefinitionEntity.class;
            case NIM -> NimImageDefinitionEntity.class;
            case INFERENCE ->
                    throw new IllegalArgumentException("InferenceService image definitions are not supported");
        };
    }

}