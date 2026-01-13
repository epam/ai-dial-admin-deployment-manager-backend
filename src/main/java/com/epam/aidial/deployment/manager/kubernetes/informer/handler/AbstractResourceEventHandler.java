package com.epam.aidial.deployment.manager.kubernetes.informer.handler;

import com.epam.aidial.deployment.manager.kubernetes.informer.IdExtractor;
import com.networknt.schema.utils.StringUtils;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ExecutorService;

/**
 * Base class for all Kubernetes resource watchers.
 * Provides common functionality for handling resource events from Kubernetes informers,
 * including consistent error handling, logging, and event processing.
 *
 * @param <T> the type of Kubernetes resource being watched (must extend HasMetadata)
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractResourceEventHandler<T extends HasMetadata> implements ResourceEventHandler<T> {

    protected static final String EVENT_ADDED = "added";
    protected static final String EVENT_MODIFIED = "modified";
    protected static final String EVENT_DELETED = "deleted";

    private final String resourceTypeName;
    private final IdExtractor idExtractor;
    protected final ExecutorService executorService;

    @Override
    @Transactional
    public void onAdd(T resource) {
        processEvent(resource, EVENT_ADDED, false);
    }

    @Override
    @Transactional
    public void onUpdate(T oldResource, T newResource) {
        processEvent(newResource, EVENT_MODIFIED, false);
    }

    @Override
    @Transactional
    public void onDelete(T resource, boolean deletedFinalStateUnknown) {
        processEvent(resource, EVENT_DELETED, true);
    }

    private void processEvent(T resource, String eventTypeName, boolean isDeleted) {
        String resourceName = getResourceName(resource);
        String resourceNamespace = getResourceNamespace(resource);

        log.debug("Received {} event for {} resource '{}/{}'", eventTypeName, resourceTypeName, resourceNamespace, resourceName);
        try {
            String deploymentId = idExtractor.extract(resourceName);
            if (StringUtils.isBlank(deploymentId)) {
                log.warn("Skipping {} '{}' - resource name does not contain a valid deployment ID", resourceTypeName, resourceName);
                return;
            }

            triggerReconcile(deploymentId, resource, isDeleted);

            log.debug("Processed {} {}: '{}'", eventTypeName, resourceTypeName, resourceName);

        } catch (Exception e) {
            log.warn("Failed to process {} event for {} resource '{}/{}': {}",
                    eventTypeName, resourceTypeName, resourceNamespace, resourceName, e.getMessage(), e);
        }
    }

    private String getResourceName(T resource) {
        return resource.getMetadata().getName();
    }

    private String getResourceNamespace(T resource) {
        return resource.getMetadata().getNamespace();
    }

    private void triggerReconcile(String deploymentId, T resource, boolean isDeleted) {
        executorService.execute(() -> {
            try {
                reconcile(deploymentId, resource, isDeleted);
            } catch (Exception e) {
                log.warn("Error reconciling deployment '{}'", deploymentId, e);
            }
        });
    }

    protected abstract void reconcile(String deploymentId, T resource, boolean isDeleted);

}