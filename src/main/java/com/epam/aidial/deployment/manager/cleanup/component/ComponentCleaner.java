package com.epam.aidial.deployment.manager.cleanup.component;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.ComponentRemovalRepository;
import com.epam.aidial.deployment.manager.model.ComponentRemoval;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;

@Service
@LogExecution
@RequiredArgsConstructor
public class ComponentCleaner {

    private final ImageDefinitionCleaner imageDefinitionCleaner;
    private final DeploymentCleaner deploymentCleaner;
    @Qualifier("component-cleaner")
    private final ExecutorService executorService;

    private final ComponentRemovalRepository componentRemovalRepository;

    public void deleteAsync(ComponentRemoval componentRemoval) {
        componentRemovalRepository.save(componentRemoval);

        executorService.execute(() -> {
            delete(componentRemoval);
            componentRemovalRepository.delete(componentRemoval.getId(), componentRemoval.getType());
        });
    }

    void deleteAllPersisted() {
        componentRemovalRepository.getAll()
                .forEach(componentRemoval -> {
                    delete(componentRemoval);
                    componentRemovalRepository.delete(componentRemoval.getId(), componentRemoval.getType());
                });
    }

    private void delete(ComponentRemoval componentRemoval) {
        var id = componentRemoval.getId();
        switch (componentRemoval.getType()) {
            case IMAGE_DEFINITION -> imageDefinitionCleaner.delete(id);
            case DEPLOYMENT -> deploymentCleaner.delete(id);
            default -> throw new NotImplementedException("Component type '%s' is not supported"
                    .formatted(componentRemoval.getType()));
        }
    }

}
