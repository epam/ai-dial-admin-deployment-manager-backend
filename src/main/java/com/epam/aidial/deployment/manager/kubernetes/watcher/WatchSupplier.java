package com.epam.aidial.deployment.manager.kubernetes.watcher;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;

/**
 * Functional interface for creating Kubernetes resource watches.
 *
 * @param <T> The type of Kubernetes resource being watched
 */
@FunctionalInterface
public interface WatchSupplier<T extends HasMetadata> {
    /**
     * Creates a watch for the specified resource type in the given namespace.
     *
     * @param namespace The namespace to watch
     * @param watcher   The watcher instance to receive events
     * @return A Watch instance that can be closed to stop watching
     */
    Watch supply(String namespace, Watcher<T> watcher);
}
