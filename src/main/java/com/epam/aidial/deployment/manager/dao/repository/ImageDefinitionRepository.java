package com.epam.aidial.deployment.manager.dao.repository;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.entity.AdapterImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.ApplicationImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.ImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.InterceptorImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.McpImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceImageStatus;
import com.epam.aidial.deployment.manager.dao.jpa.ImageDefinitionJpaRepository;
import com.epam.aidial.deployment.manager.dao.mapper.PersistenceImageDefinitionMapper;
import com.epam.aidial.deployment.manager.dao.mapper.PersistenceImageDefinitionViewMapper;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageDefinitionView;
import com.epam.aidial.deployment.manager.model.ImageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Repository
@LogExecution
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

    public Collection<ImageDefinition> getAllImageDefinitionsByType(ImageType type) {
        var entityClass = detectEntityClass(type);
        return imageDefinitionJpaRepository.findAllByType(entityClass).stream()
                .map(mapper::toImageDefinition)
                .collect(Collectors.toList());
    }

    public Collection<ImageDefinition> getAllImageDefinitionsByNameAndType(String name, ImageType type) {
        var entityClass = detectEntityClass(type);
        return imageDefinitionJpaRepository.findAllByNameAndType(name, entityClass).stream()
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

    @Transactional(readOnly = true)
    public Optional<ImageDefinition> getImageDefinitionByTypeAndNameAndVersion(ImageType type, String name, String version) {
        var entityClass = detectEntityClass(type);
        return imageDefinitionJpaRepository.findByNameAndTypeAndVersion(name, entityClass, version)
                .map(mapper::toImageDefinition);
    }

    public Optional<ImageDefinition> getImageDefinitionForUpdateById(UUID id) {
        return imageDefinitionJpaRepository.findForUpdateById(id)
                .map(mapper::toImageDefinition);
    }

    public ImageDefinition saveImageDefinition(ImageDefinition imageDefinition) {
        var entity = mapper.toImageDefinitionEntity(imageDefinition);
        var savedEntity = imageDefinitionJpaRepository.saveAndFlush(entity);
        log.debug("Image definition '{}' saved successfully", savedEntity.getId());
        return mapper.toImageDefinition(savedEntity);
    }

    public void deleteImageDefinitionById(UUID id) {
        imageDefinitionJpaRepository.deleteById(id);
        log.debug("Image definition '{}' deleted successfully", id);
    }

    public ImageDefinition updateImageDefinition(UUID id, ImageDefinition updatedImageDefinition) {
        var existingEntity = findImageDefinitionById(id);

        mapper.updateEntityFromDomain(updatedImageDefinition, existingEntity);

        var savedEntity = imageDefinitionJpaRepository.saveAndFlush(existingEntity);
        log.debug("Image definition '{}' updated successfully", id);
        return mapper.toImageDefinition(savedEntity);
    }

    public void addBuildLogs(UUID id, List<String> logs) {
        var entity = findImageDefinitionById(id);
        appendBuildLogs(entity, logs);
        imageDefinitionJpaRepository.saveAndFlush(entity);
        log.debug("Build logs added for image definition '{}', {} log entries added", id, logs.size());
    }

    public void startBuild(UUID id) {
        var entity = findImageDefinitionById(id);
        entity.setBuildStatus(PersistenceImageStatus.BUILDING);
        entity.setBuildLogs(new ArrayList<>());
        appendBuildLogs(entity, List.of("Image build started"));
        imageDefinitionJpaRepository.saveAndFlush(entity);
        log.debug("Build started for image definition '{}'", id);
    }

    public void completeBuildSuccessfully(UUID id, String imageName, long builtAt) {
        var entity = findImageDefinitionById(id);
        entity.setBuildStatus(PersistenceImageStatus.BUILD_SUCCESSFUL);
        entity.setImageName(imageName);
        entity.setBuiltAt(builtAt);
        imageDefinitionJpaRepository.saveAndFlush(entity);
        log.debug("Build completed successfully for image definition '{}': imageName={}, builtAt={}",
                id, imageName, builtAt);
    }

    public void failBuild(UUID id, String errorLog) {
        var entity = findImageDefinitionById(id);
        entity.setBuildStatus(PersistenceImageStatus.BUILD_FAILED);
        appendBuildLogs(entity, List.of(errorLog));
        imageDefinitionJpaRepository.saveAndFlush(entity);
        log.debug("Build failed for image definition '{}'", id);
    }

    private ImageDefinitionEntity findImageDefinitionById(UUID id) {
        return imageDefinitionJpaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Image definition not found by id: %s".formatted(id)));
    }

    private void appendBuildLogs(ImageDefinitionEntity entity, List<String> logs) {
        if (entity.getBuildLogs() == null) {
            entity.setBuildLogs(new ArrayList<>());
        }

        List<String> buildLogs = entity.getBuildLogs();
        buildLogs.addAll(logs);

        int excess = buildLogs.size() - buildLogsSizeLimit;
        if (excess > 0) {
            buildLogs.subList(0, excess).clear(); // Remove oldest entries
        }
    }

    public List<ImageDefinitionView> getAllImageDefinitionViews() {
        var imageDefinitions = imageDefinitionJpaRepository.findAll();
        return viewMapper.toViews(imageDefinitions);
    }

    public List<ImageDefinitionView> getAllImageDefinitionViewsByType(ImageType type) {
        var entityClass = detectEntityClass(type);
        var imageDefinitions = imageDefinitionJpaRepository.findAllByType(entityClass);
        return viewMapper.toViews(imageDefinitions);
    }

    private static Class<? extends ImageDefinitionEntity> detectEntityClass(ImageType type) {
        if (type == null) {
            throw new IllegalArgumentException("Image type must not be null");
        }
        return switch (type) {
            case MCP -> McpImageDefinitionEntity.class;
            case ADAPTER -> AdapterImageDefinitionEntity.class;
            case INTERCEPTOR -> InterceptorImageDefinitionEntity.class;
            case APPLICATION -> ApplicationImageDefinitionEntity.class;
        };
    }

}