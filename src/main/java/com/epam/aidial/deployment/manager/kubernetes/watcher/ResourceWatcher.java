package com.epam.aidial.deployment.manager.kubernetes.watcher;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for all Kubernetes resource watchers.
 * Implements the Watcher interface from the Fabric8 Kubernetes client and provides
 * common functionality for handling resource events.
 *
 * @param <T> The type of Kubernetes resource being watched
 */
@Slf4j
public abstract class ResourceWatcher<T extends HasMetadata> implements Watcher<T> {

    protected static final String EVENT_ADDED = "added";
    protected static final String EVENT_MODIFIED = "modified";
    protected static final String EVENT_DELETED = "deleted";

    private final String resourceTypeName;
    private final String namespace;

    protected ResourceWatcher(String resourceTypeName, String namespace) {
        this.resourceTypeName = resourceTypeName;
        this.namespace = namespace;
    }

    @Override
    @Transactional
    public void eventReceived(Action action, T resource) {
        String resourceName = resource.getMetadata().getName();
        String resourceNamespace = resource.getMetadata().getNamespace();

        log.debug("Received {} event for {} {}/{}: {}",
                action, resourceTypeName, resourceNamespace, resourceName, resource);

        try {
            switch (action) {
                case ADDED -> handleAddedResource(resource);
                case MODIFIED -> handleModifiedResource(resource);
                case DELETED -> handleDeletedResource(resource);
                case ERROR ->
                        log.warn("Received ERROR event for {} {}/{}", resourceTypeName, resourceNamespace, resourceName);
                default ->
                        log.warn("Unhandled event type {} for {} {}/{}", action, resourceTypeName, resourceNamespace, resourceName);
            }
        } catch (Exception e) {
            log.warn("Error processing {} event for {} {}/{}: {}",
                    action, resourceTypeName, resourceNamespace, resourceName, e.getMessage(), e);
        }
    }

    @Override
    public void onClose(WatcherException cause) {
        if (cause != null) {
            log.error("Watcher for {} in namespace {} closed with exception: {}",
                    resourceTypeName, namespace, cause.getMessage(), cause);
            handleWatcherClosed(cause);
        } else {
            log.info("Watcher for {} in namespace {} closed normally",
                    resourceTypeName, namespace);
        }
    }

    protected abstract void handleAddedResource(T resource);

    protected abstract void handleModifiedResource(T resource);

    protected abstract void handleDeletedResource(T resource);

    protected abstract void handleWatcherClosed(WatcherException cause);

    protected String getNamespace() {
        return namespace;
    }

}