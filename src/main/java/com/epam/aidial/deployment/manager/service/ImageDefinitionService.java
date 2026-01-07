package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.cleanup.component.ComponentCleanupService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Page<ImageDefinition> getAllImageDefinitions(Pageable pageable) {
        return imageDefinitionRepository.getAllImageDefinitions(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ImageDefinition> getAllImageDefinitionsByType(ImageTypeDto type, Pageable pageable) {
        return imageDefinitionRepository.getAllImageDefinitionsByType(type, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ImageDefinitionView> getImageDefinitionViewsByType(ImageTypeDto type, Pageable pageable) {
        return imageDefinitionRepository.getAllImageDefinitionViewsByType(type, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ImageDefinitionView> getImageDefinitionViews(Pageable pageable) {
        return imageDefinitionRepository.getAllImageDefinitionViews(pageable);
    }

    @Transactional(readOnly = true)
    public Collection<ImageDefinition> getAllImageDefinitionsByNameAndType(String name, ImageTypeDto type) {
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

    @Transactional
    public ImageDefinition createImageDefinition(ImageDefinition imageDefinition) {
        imageDefinition.setBuildStatus(ImageStatus.NOT_BUILT);

        // Set author information - use provided author or extract from security context
        if (StringUtils.isBlank(imageDefinition.getAuthor())) {
            imageDefinition.setAuthor(securityClaimsExtractor.getEmail());
        }

        return imageDefinitionRepository.saveImageDefinition(imageDefinition);
    }

    @Transactional
    public ImageDefinition updateImageDefinition(UUID id, ImageDefinition updatedImageDefinition) {
        var existing = imageDefinitionRepository.getImageDefinitionById(id)
                .orElseThrow(() -> new EntityNotFoundException("Image definition not found by id: %s".formatted(id)));

        if (existing.getBuildStatus() == ImageStatus.BUILD_SUCCESSFUL || existing.getBuildStatus() == ImageStatus.BUILDING) {
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
        componentCleanupService.deleteAsync(ComponentRemoval.of(id, ComponentType.IMAGE_DEFINITION));
    }

}
