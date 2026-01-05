package com.epam.aidial.deployment.manager.kubernetes.watcher;

import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.WatcherException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract base class for Kubernetes service watchers.
 * Implements common lifecycle management, event handling, and status update logic.
 *
 * @param <T> The type of Kubernetes resource being watched
 */
@Slf4j
public abstract class AbstractServiceWatcher<T extends HasMetadata> extends ResourceWatcher<T> implements WatcherRegistration {

    protected final DeploymentRepository deploymentRepository;
    protected final WatcherManager watcherManager;
    protected final ExecutorService executorService;
    protected final String namespace;
    protected final String resourceTypeName;

    private final AtomicReference<Watch> watchReference = new AtomicReference<>();
    private final WatchSupplier<T> watchSupplier;
    private final IdExtractor idExtractor;

    protected AbstractServiceWatcher(
            String resourceTypeName,
            String namespace,
            DeploymentRepository deploymentRepository,
            WatcherManager watcherManager,
            ExecutorService executorService,
            WatchSupplier<T> watchSupplier,
            IdExtractor idExtractor) {
        super(resourceTypeName, namespace);
        this.resourceTypeName = resourceTypeName;
        this.namespace = namespace;
        this.deploymentRepository = deploymentRepository;
        this.watcherManager = watcherManager;
        this.executorService = executorService;
        this.watchSupplier = watchSupplier;
        this.idExtractor = idExtractor;
    }

    @Override
    public void start() {
        log.info("Starting {} watcher in namespace {}", resourceTypeName, namespace);
        try {
            Watch watch = watchSupplier.supply(namespace, this);

            // Store the watch reference for later closing
            Watch oldWatch = watchReference.getAndSet(watch);
            if (oldWatch != null) {
                try {
                    oldWatch.close();
                } catch (Exception e) {
                    log.warn("Error closing previous {} watch: {}", resourceTypeName, e.getMessage(), e);
                }
            }

            log.info("{} watcher started successfully", resourceTypeName);
        } catch (Exception e) {
            log.error("Failed to start {} watcher: {}", resourceTypeName, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void stop() {
        log.info("Stopping {} watcher", resourceTypeName);
        Watch watch = watchReference.getAndSet(null);
        if (watch != null) {
            try {
                watch.close();
                log.info("{} watcher stopped successfully", resourceTypeName);
            } catch (Exception e) {
                log.error("Error closing {} watch: {}", resourceTypeName, e.getMessage(), e);
            }
        }
    }

    @Override
    public String getResourceType() {
        return resourceTypeName;
    }

    @Override
    protected void handleAddedResource(T resource) {
        handleResource(resource, EVENT_ADDED, false);
    }

    @Override
    @Transactional
    protected void handleModifiedResource(T resource) {
        handleResource(resource, EVENT_MODIFIED, false);
    }

    @Override
    @Transactional
    protected void handleDeletedResource(T resource) {
        handleResource(resource, EVENT_DELETED, true);
    }

    private void handleResource(T resource, String eventType, boolean isDeleted) {
        String resourceName = resource.getMetadata().getName();
        log.debug("Processing {} {}: {}", eventType, resourceTypeName, resourceName);

        try {
            UUID deploymentId = idExtractor.extract(resourceName);
            if (deploymentId == null) {
                log.warn("Skipping {} '{}' - resource name does not contain a valid deployment ID", resourceTypeName, resourceName);
                return;
            }

            triggerReconcile(deploymentId, resource, isDeleted);

            log.debug("Processed {} {}: '{}'", eventType, resourceTypeName, resourceName);

        } catch (Exception e) {
            log.warn("Error processing {} {} '{}'", eventType, resourceTypeName, resourceName, e);
        }
    }

    private void triggerReconcile(UUID deploymentId, T resource, boolean isDeleted) {
        executorService.execute(() -> {
            try {
                reconcile(deploymentId, resource, isDeleted);
            } catch (Exception e) {
                log.warn("Error reconciling deployment '{}'", deploymentId, e);
            }
        });
    }

    protected abstract void reconcile(UUID deploymentId, T resource, boolean isDeleted);

    @Override
    protected void handleWatcherClosed(WatcherException cause) {
        log.warn("{} watcher closed with exception: {}", resourceTypeName, cause.getMessage());
        watcherManager.restartWatcher(this);
    }

}
