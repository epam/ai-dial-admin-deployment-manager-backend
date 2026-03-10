package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.entity.ImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceImageStatus;
import com.epam.aidial.deployment.manager.model.ImageDefinitionView;
import com.epam.aidial.deployment.manager.model.ImageDefinitionViewElement;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@LogExecution
public class PersistenceImageDefinitionViewMapper {

    public List<ImageDefinitionView> toViews(List<ImageDefinitionEntity> imageDefinitions) {
        Map<String, List<ImageDefinitionEntity>> groupedByName = imageDefinitions.stream()
                .collect(Collectors.groupingBy(ImageDefinitionEntity::getName));

        List<ImageDefinitionView> views = new ArrayList<>();

        for (var entry : groupedByName.entrySet()) {
            var group = entry.getValue();
            var selectedEntity = findSelectedImageDefinition(group);
            var viewElements = group.stream()
                    .map(this::toViewElement)
                    .collect(Collectors.toList());

            var view = new ImageDefinitionView();
            view.setName(entry.getKey());
            view.setSelectedId(selectedEntity.getId());
            view.setAvailableVersions(viewElements);

            views.add(view);
        }

        return views;
    }

    private ImageDefinitionEntity findSelectedImageDefinition(List<ImageDefinitionEntity> group) {
        return group.stream()
                .filter(entity -> PersistenceImageStatus.BUILD_SUCCESSFUL.equals(entity.getBuildStatus()))
                .max(Comparator.comparing(ImageDefinitionEntity::getVersion))
                .orElse(
                    // If none with built status found, use the latest version with any status
                    group.stream()
                        .max(Comparator.comparing(ImageDefinitionEntity::getVersion))
                        .orElseThrow(() -> new IllegalStateException("Empty image definition group"))
                );
    }

    private ImageDefinitionViewElement toViewElement(ImageDefinitionEntity entity) {
        return new ImageDefinitionViewElement(
                entity.getId(),
                entity.getVersion(),
                mapImageStatus(entity.getBuildStatus()),
                entity.getDescription(),
                new ArrayList<>(entity.getTopics())
        );
    }

    private ImageStatus mapImageStatus(PersistenceImageStatus status) {
        if (status == null) {
            return ImageStatus.NOT_BUILT;
        }

        return switch (status) {
            case NOT_BUILT -> ImageStatus.NOT_BUILT;
            case BUILDING -> ImageStatus.BUILDING;
            case BUILD_FAILED -> ImageStatus.BUILD_FAILED;
            case BUILD_SUCCESSFUL -> ImageStatus.BUILD_SUCCESSFUL;
        };
    }
}