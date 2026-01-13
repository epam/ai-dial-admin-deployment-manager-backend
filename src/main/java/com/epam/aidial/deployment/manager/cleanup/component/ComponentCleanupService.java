package com.epam.aidial.deployment.manager.cleanup.component;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.ComponentRemovalRepository;
import com.epam.aidial.deployment.manager.model.ComponentRemoval;
import com.epam.aidial.deployment.manager.model.ComponentType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@LogExecution
public class ComponentCleanupService {

    private final Map<ComponentType, CleanupStrategy> cleanupStrategies;
    @Qualifier("component-cleaner")
    private final ExecutorService executorService;
    private final ComponentRemovalRepository componentRemovalRepository;

    public ComponentCleanupService(List<CleanupStrategy> strategies,
                            @Qualifier("component-cleaner") ExecutorService executorService,
                            ComponentRemovalRepository componentRemovalRepository) {
        this.cleanupStrategies = strategies.stream()
                .collect(Collectors.toMap(CleanupStrategy::getComponentType, Function.identity()));
        this.executorService = executorService;
        this.componentRemovalRepository = componentRemovalRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteAsync(ComponentRemoval componentRemoval) {
        componentRemovalRepository.save(componentRemoval);
        getStrategy(componentRemoval.getType()).prepareForDeletion(componentRemoval.getId());

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
        getStrategy(componentRemoval.getType()).delete(componentRemoval.getId());
    }

    private CleanupStrategy getStrategy(ComponentType type) {
        var strategy = cleanupStrategies.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("Component type '%s' is not supported".formatted(type));
        }
        return strategy;
    }
}
