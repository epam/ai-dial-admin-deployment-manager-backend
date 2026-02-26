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

    private static final ThreadLocal<Integer> SYNC_DELETION_DEPTH = ThreadLocal.withInitial(() -> 0);

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

    /**
     * Returns true if the current thread is performing a synchronous deletion,
     * so that strategies (e.g. ImageDefinitionCleanupStrategy) can delete child components synchronously.
     */
    public boolean isSyncDeletion() {
        return SYNC_DELETION_DEPTH.get() > 0;
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteSync(ComponentRemoval componentRemoval) {
        componentRemovalRepository.save(componentRemoval);
        getStrategy(componentRemoval.getType()).prepareForDeletion(componentRemoval.getId());

        SYNC_DELETION_DEPTH.set(SYNC_DELETION_DEPTH.get() + 1);
        try {
            delete(componentRemoval);
            componentRemovalRepository.delete(componentRemoval.getId(), componentRemoval.getType());
        } finally {
            int depth = SYNC_DELETION_DEPTH.get() - 1;
            if (depth == 0) {
                SYNC_DELETION_DEPTH.remove();
            } else {
                SYNC_DELETION_DEPTH.set(depth);
            }
        }
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
