package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.cleanup.component.ComponentCleanupService;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.ImageDefinitionRepository;
import com.epam.aidial.deployment.manager.exception.EntityAlreadyExistsException;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.ComponentRemoval;
import com.epam.aidial.deployment.manager.model.ComponentType;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageDefinitionView;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class ImageDefinitionService {

    private final ImageDefinitionRepository imageDefinitionRepository;
    private final ComponentCleanupService componentCleanupService;
    private final SecurityClaimsExtractor securityClaimsExtractor;

    @Transactional(readOnly = true)
    public Collection<ImageDefinition> getAllImageDefinitions() {
        return imageDefinitionRepository.getAllImageDefinitions();
    }

    @Transactional(readOnly = true)
    public Collection<ImageDefinition> getAllImageDefinitionsByType(ImageType type) {
        return imageDefinitionRepository.getAllImageDefinitionsByType(type);
    }

    @Transactional(readOnly = true)
    public Collection<ImageDefinitionView> getImageDefinitionViewsByType(ImageType type) {
        return imageDefinitionRepository.getAllImageDefinitionViewsByType(type);
    }

    @Transactional(readOnly = true)
    public Collection<ImageDefinitionView> getImageDefinitionViews() {
        return imageDefinitionRepository.getAllImageDefinitionViews();
    }

    @Transactional(readOnly = true)
    public Collection<ImageDefinition> getAllImageDefinitionsByNameAndType(String name, ImageType type) {
        return imageDefinitionRepository.getAllImageDefinitionsByNameAndType(name, type);
    }

    @Transactional(readOnly = true)
    public Collection<ImageDefinition> getAllImageDefinitionsByName(String name) {
        return imageDefinitionRepository.getAllImageDefinitionsByName(name);
    }

    @Transactional(readOnly = true)
    public Optional<ImageDefinition> getImageDefinition(UUID id) {
        return imageDefinitionRepository.getImageDefinitionById(id);
    }

    @Transactional(readOnly = true)
    public Optional<ImageDefinition> getImageDefinitionByTypeAndNameAndVersion(ImageType type, String name, String version) {
        return imageDefinitionRepository.getImageDefinitionByTypeAndNameAndVersion(type, name, version);
    }

    @Transactional
    public ImageDefinition createImageDefinition(ImageDefinition imageDefinition) {
        var type = ImageType.of(imageDefinition);
        var name = imageDefinition.getName();
        var version = imageDefinition.getVersion();

        imageDefinitionRepository.getImageDefinitionByTypeAndNameAndVersion(type, name, version)
                .ifPresent(existing -> {
                    throw new EntityAlreadyExistsException(
                            "Image definition already exists: type=%s, name=%s, version=%s".formatted(type, name, version));
                });

        imageDefinition.setBuildStatus(ImageStatus.NOT_BUILT);

        // Set author information - use provided author or extract from security context
        if (StringUtils.isBlank(imageDefinition.getAuthor())) {
            imageDefinition.setAuthor(securityClaimsExtractor.getEmail());
        }

        return imageDefinitionRepository.saveImageDefinition(imageDefinition);
    }

    @Transactional
    public ImageDefinition updateImageDefinition(UUID id, ImageDefinition updatedImageDefinition) {
        return updateImageDefinition(id, updatedImageDefinition, false);
    }

    @Transactional
    public ImageDefinition updateImageDefinition(UUID id, ImageDefinition updatedImageDefinition, boolean fromImport) {
        var existing = imageDefinitionRepository.getImageDefinitionById(id)
                .orElseThrow(() -> new EntityNotFoundException("Image definition not found by id: %s".formatted(id)));

        // Allowing update of built images on import
        if ((!fromImport && existing.getBuildStatus() == ImageStatus.BUILD_SUCCESSFUL) || existing.getBuildStatus() == ImageStatus.BUILDING) {
            throw new IllegalArgumentException("Cannot update image definition with status %s. It is read-only."
                    .formatted(existing.getBuildStatus()));
        }

        return imageDefinitionRepository.updateImageDefinition(id, updatedImageDefinition);
    }

    @Transactional
    public void updateBuildStatus(UUID id, ImageStatus buildStatus) {
        imageDefinitionRepository.updateBuildStatus(id, buildStatus);
    }

    @Transactional
    public void addBuildLog(UUID id, String log) {
        addBuildLogs(id, List.of(log));
    }

    @Transactional
    public void addBuildLogs(UUID id, List<String> logs) {
        imageDefinitionRepository.addBuildLogs(id, logs);
    }

    @Transactional
    public void setImageName(UUID id, String imageName) {
        imageDefinitionRepository.setImageName(id, imageName);
    }

    @Transactional
    public void setBuiltAt(UUID id, long timestamp) {
        imageDefinitionRepository.setBuiltAt(id, timestamp);
    }

    @Transactional
    public void resetBuildLogs(UUID id) {
        imageDefinitionRepository.resetBuildLogs(id);
    }

    public void deleteImageDefinitionAsync(UUID id) {
        componentCleanupService.deleteAsync(ComponentRemoval.of(String.valueOf(id), ComponentType.IMAGE_DEFINITION));
    }

    public void deleteImageDefinitionSync(UUID id) {
        componentCleanupService.deleteSync(ComponentRemoval.of(String.valueOf(id), ComponentType.IMAGE_DEFINITION));
    }

}
