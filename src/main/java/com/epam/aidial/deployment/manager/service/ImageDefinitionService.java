package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.cleanup.component.ComponentCleanupService;
import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.ImageDefinitionRepository;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.ComponentRemoval;
import com.epam.aidial.deployment.manager.model.ComponentType;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageDefinitionView;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import com.epam.aidial.deployment.manager.web.dto.ImageTypeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class ImageDefinitionService {

    private final ImageDefinitionRepository imageDefinitionRepository;
    private final ComponentCleanupService componentCleanupService;
    private final SecurityClaimsExtractor securityClaimsExtractor;
    private final DisposableResourceManager disposableResourceManager;

    @Transactional(readOnly = true)
    public Collection<ImageDefinition> getAllImageDefinitions() {
        return imageDefinitionRepository.getAllImageDefinitions();
    }

    @Transactional(readOnly = true)
    public Collection<ImageDefinition> getAllImageDefinitionsByType(ImageTypeDto type) {
        return imageDefinitionRepository.getAllImageDefinitionsByType(type);
    }

    @Transactional(readOnly = true)
    public Collection<ImageDefinitionView> getImageDefinitionViewsByType(ImageTypeDto type) {
        return imageDefinitionRepository.getAllImageDefinitionViewsByType(type);
    }

    @Transactional(readOnly = true)
    public Collection<ImageDefinitionView> getImageDefinitionViews() {
        return imageDefinitionRepository.getAllImageDefinitionViews();
    }

    @Transactional(readOnly = true)
    public Collection<ImageDefinition> getAllImageDefinitionsByDisplayNameAndType(String displayName, ImageTypeDto type) {
        return imageDefinitionRepository.getAllImageDefinitionsByDisplayNameAndType(displayName, type);
    }

    @Transactional(readOnly = true)
    public Collection<ImageDefinition> getAllImageDefinitionsByDisplayName(String displayName) {
        return imageDefinitionRepository.getAllImageDefinitionsByDisplayName(displayName);
    }

    @Transactional(readOnly = true)
    public Optional<ImageDefinition> getImageDefinition(String id) {
        return imageDefinitionRepository.getImageDefinitionById(id);
    }

    @Transactional
    public ImageDefinition createImageDefinition(ImageDefinition imageDefinition) {
        disposableResourceManager.checkNoResourcesAreAssociatedWithId(imageDefinition.getId(), "image definition");

        imageDefinition.setBuildStatus(ImageStatus.NOT_BUILT);

        // Set author information - use provided author or extract from security context
        if (StringUtils.isBlank(imageDefinition.getAuthor())) {
            imageDefinition.setAuthor(securityClaimsExtractor.getEmail());
        }

        return imageDefinitionRepository.saveImageDefinition(imageDefinition);
    }

    @Transactional
    public ImageDefinition updateImageDefinition(String id, ImageDefinition updatedImageDefinition) {
        var existing = imageDefinitionRepository.getImageDefinitionById(id)
                .orElseThrow(() -> new EntityNotFoundException("Image definition not found by id: %s".formatted(id)));

        if (existing.getBuildStatus() == ImageStatus.BUILD_SUCCESSFUL || existing.getBuildStatus() == ImageStatus.BUILDING) {
            throw new IllegalArgumentException("Cannot update image definition with status %s. It is read-only."
                    .formatted(existing.getBuildStatus()));
        }

        return imageDefinitionRepository.updateImageDefinition(id, updatedImageDefinition);
    }

    @Transactional
    public void updateBuildStatus(String id, ImageStatus buildStatus) {
        imageDefinitionRepository.updateBuildStatus(id, buildStatus);
    }

    @Transactional
    public void addBuildLog(String id, String log) {
        addBuildLogs(id, List.of(log));
    }

    @Transactional
    public void addBuildLogs(String id, List<String> logs) {
        imageDefinitionRepository.addBuildLogs(id, logs);
    }

    @Transactional
    public void setImageName(String id, String imageName) {
        imageDefinitionRepository.setImageName(id, imageName);
    }

    @Transactional
    public void setBuiltAt(String id, long timestamp) {
        imageDefinitionRepository.setBuiltAt(id, timestamp);
    }

    @Transactional
    public void resetBuildLogs(String id) {
        imageDefinitionRepository.resetBuildLogs(id);
    }

    public void deleteImageDefinitionAsync(String id) {
        componentCleanupService.deleteAsync(ComponentRemoval.of(id, ComponentType.IMAGE_DEFINITION));
    }

}
