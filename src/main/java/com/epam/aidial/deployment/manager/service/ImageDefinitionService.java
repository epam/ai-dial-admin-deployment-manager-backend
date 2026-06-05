package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.cleanup.component.ComponentCleanupService;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.entity.ImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.mapper.PersistenceImageDefinitionMapper;
import com.epam.aidial.deployment.manager.dao.repository.ImageDefinitionRepository;
import com.epam.aidial.deployment.manager.exception.EntityAlreadyExistsException;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.ComponentRemoval;
import com.epam.aidial.deployment.manager.model.ComponentType;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageDefinitionView;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.service.audit.HistoryService;
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
    private final HistoryService historyService;
    private final PersistenceImageDefinitionMapper persistenceImageDefinitionMapper;

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

    @Transactional(readOnly = true)
    public ImageDefinition getImageDefinitionSnapshot(UUID id, Integer revision) {
        ImageDefinitionEntity entity = historyService.entitySnapshotAtRevision(
                revision, id, ImageDefinitionEntity.class);
        return persistenceImageDefinitionMapper.toImageDefinition(entity);
    }

    @Transactional(readOnly = true)
    public Collection<ImageDefinition> getAllImageDefinitionsAtRevision(Integer revision) {
        return historyService.getEntitiesAtRevision(revision, ImageDefinitionEntity.class).stream()
                .map(persistenceImageDefinitionMapper::toImageDefinition)
                .toList();
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

        if (existing.getBuildStatus() == ImageStatus.BUILDING) {
            throw new IllegalArgumentException("Cannot update image definition with status BUILDING. It is read-only.");
        }

        if (!fromImport && existing.getBuildStatus() == ImageStatus.BUILD_SUCCESSFUL) {
            if (!existing.hasSameBuildAffectingFields(updatedImageDefinition)) {
                throw new IllegalArgumentException(
                        "Cannot update build-affecting fields of a built image definition. "
                                + "Only description, author, topics, and license can be updated while build status is BUILD_SUCCESSFUL.");
            }
            return imageDefinitionRepository.updateImageDefinitionMetaFields(id, updatedImageDefinition);
        }

        return imageDefinitionRepository.updateImageDefinition(id, updatedImageDefinition);
    }

    @Transactional
    public ImageDefinition rollback(UUID id, Integer revision) {
        // Reject ids past the highest assigned revision so that typos can't slip through to
        // updateImageDefinition, which would spuriously reset buildStatus to NOT_BUILT.
        // In-range gap ids (e.g. left by Hibernate's pooled sequence allocator after a JVM
        // restart) are still resolved leniently by Envers downstream.
        if (revision == null || revision <= 0 || revision > historyService.maxRevisionId()) {
            throw new EntityNotFoundException("Unable to find revision with id " + revision);
        }

        var existingOpt = imageDefinitionRepository.getImageDefinitionById(id);
        if (existingOpt.isEmpty()) {
            // The image definition existed at the requested revision but has since been deleted: re-create
            // it instead of failing with 404, so an operator can revert a deletion in a single call.
            return resurrect(id, revision);
        }
        var existing = existingOpt.get();

        if (existing.getBuildStatus() == ImageStatus.BUILDING || existing.getBuildStatus() == ImageStatus.BUILD_SUCCESSFUL) {
            throw new IllegalArgumentException(
                    "Cannot roll back image definition '%s' while build status is %s. Create a new version instead and re-point deployments."
                            .formatted(id, existing.getBuildStatus()));
        }

        var snapshot = getImageDefinitionSnapshot(id, revision);

        var snapshotType = ImageType.of(snapshot);
        imageDefinitionRepository.getImageDefinitionByTypeAndNameAndVersion(snapshotType, snapshot.getName(), snapshot.getVersion())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException(
                            "Cannot roll back image definition '%s': name='%s' and version='%s' would collide with existing image definition '%s'."
                                    .formatted(id, snapshot.getName(), snapshot.getVersion(), other.getId()));
                });

        return imageDefinitionRepository.updateImageDefinition(id, snapshot);
    }

    /**
     * Re-create an image definition that existed at the requested revision but has since been deleted.
     * The snapshot is reconstructed from the audit history ({@link #getImageDefinitionSnapshot} throws 404
     * if the id never existed at that revision). The re-created definition gets a <em>fresh</em> id —
     * image-definition ids are Hibernate-generated UUIDs that the generator overwrites on insert — so the
     * returned DTO's id differs from the requested {id}. Any leftovers from the deleted generation (build
     * jobs / config maps / pushed images, plus a still-pending component-removal marker) are all keyed by
     * the old id and therefore harmless to the re-created definition; they are left for the scheduled
     * cleaner to drain in due course. (If this path ever becomes id-preserving for parity with deployment
     * resurrection, an eager {@code finalizePendingCleanup} drain becomes load-bearing again — see
     * {@code DeploymentService#resurrect}.) It is created in NOT_BUILT (the prior build artifacts are gone)
     * and is subject to the same name+version uniqueness check as a normal create.
     */
    private ImageDefinition resurrect(UUID id, Integer revision) {
        var snapshot = getImageDefinitionSnapshot(id, revision);
        var snapshotType = ImageType.of(snapshot);
        imageDefinitionRepository.getImageDefinitionByTypeAndNameAndVersion(snapshotType, snapshot.getName(), snapshot.getVersion())
                .ifPresent(existing -> {
                    throw new EntityAlreadyExistsException(
                            "Image definition already exists: type=%s, name=%s, version=%s"
                                    .formatted(snapshotType, snapshot.getName(), snapshot.getVersion()));
                });
        snapshot.setId(null); // force a fresh-UUID INSERT instead of merge-with-assigned-id
        return createImageDefinition(snapshot);
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
    public void startBuild(UUID id) {
        imageDefinitionRepository.startBuild(id);
    }

    @Transactional
    public void completeBuildSuccessfully(UUID id, String imageName, long builtAt) {
        imageDefinitionRepository.completeBuildSuccessfully(id, imageName, builtAt);
    }

    @Transactional
    public void failBuild(UUID id, String errorLog) {
        imageDefinitionRepository.failBuild(id, errorLog);
    }

    @Transactional
    public boolean stopBuild(UUID id) {
        return imageDefinitionRepository.stopBuild(id);
    }

    public void deleteImageDefinitionAsync(UUID id) {
        componentCleanupService.deleteAsync(ComponentRemoval.of(String.valueOf(id), ComponentType.IMAGE_DEFINITION));
    }

    public void deleteImageDefinitionSync(UUID id) {
        componentCleanupService.deleteSync(ComponentRemoval.of(String.valueOf(id), ComponentType.IMAGE_DEFINITION));
    }

}
