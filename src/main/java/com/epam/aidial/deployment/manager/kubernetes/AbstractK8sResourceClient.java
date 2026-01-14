package com.epam.aidial.deployment.manager.kubernetes;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Predicate;

import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
public abstract class AbstractK8sResourceClient<T extends HasMetadata, L extends KubernetesResourceList<T>> {

    protected final K8sClient k8sClient;

    protected AbstractK8sResourceClient(K8sClient k8sClient) {
        this.k8sClient = k8sClient;
    }

    public PodList getServicePods(String namespace, String name) {
        return k8sClient.getPods(namespace, Map.of(getLabelKey(), name));
    }

    public Pod getServicePod(String namespace, String name, String podName) {
        return k8sClient.getPod(namespace, podName, Map.of(getLabelKey(), name));
    }

    public void createService(String namespace, T service) {
        var name = service.getMetadata().getName();

        if (log.isDebugEnabled()) {
            log.debug("Creating {} '{}' in namespace '{}', CRD: {}",
                    getResourceName(), name, namespace, Serialization.asYaml(service));
        } else {
            log.info("Creating {} '{}' in namespace '{}'", getResourceName(), name, namespace);
        }

        try {
            getClient().inNamespace(namespace)
                    .resource(service)
                    .create();
            log.info("{} '{}' created", getResourceName(), name);
        } catch (KubernetesClientException e) {
            log.warn("Kubernetes API error during {} '{}' creation: {}", getResourceName(), name, e.getMessage(), e);
            throw e;
        }
    }

    public void updateService(String namespace, T service) {
        var name = service.getMetadata().getName();

        if (log.isDebugEnabled()) {
            log.debug("Updating {} '{}' in namespace '{}', CRD: {}",
                    getResourceName(), name, namespace, Serialization.asYaml(service));
        } else {
            log.info("Updating {} '{}' in namespace '{}'", getResourceName(), name, namespace);
        }

        try {
            getClient().inNamespace(namespace)
                    .resource(service)
                    .update();
            log.info("{} '{}' updated", getResourceName(), name);
        } catch (KubernetesClientException e) {
            log.warn("Kubernetes API error during {} '{}' update: {}", getResourceName(), name, e.getMessage(), e);
            throw e;
        }
    }

    public T waitService(String namespace, String name, Predicate<T> predicate, int timeoutSec) {
        log.debug("Waiting for {} '{}' in namespace '{}' to pass the predicate. Timeout: {}s.",
                getResourceName(), name, namespace, timeoutSec);

        var serviceAfterWait = getClient()
                .inNamespace(namespace)
                .withName(name)
                .waitUntilCondition(predicate, timeoutSec, SECONDS);

        log.debug("{} '{}' in namespace '{}' has passed the predicate.", getResourceName(), name, namespace);
        return serviceAfterWait;
    }

    public void deleteService(String namespace, String name) {
        log.info("Attempting to delete {} '{}' in namespace '{}'", getResourceName(), name, namespace);

        try {
            var resource = getClient().inNamespace(namespace).withName(name);
            configureDeletion(resource);
            var results = resource.delete();

            if (!results.isEmpty()) {
                // List is not empty, meaning the resource was found and deletion initiated.
                // It should contain exactly one element for a single named resource deletion.
                if (results.size() > 1) {
                    // This case should theoretically not happen when deleting by specific name,
                    // but log a warning just in case of unexpected API behavior.
                    log.warn("Deletion of single service '{}' unexpectedly returned {} status details. Details: {}",
                            name, results.size(), results);
                }

                // Log details from the first (and expected only) status object
                var details = results.get(0);
                log.info("{} '{}' (UID: '{}') deletion successfully initiated in namespace '{}'.",
                        getResourceName(), details.getName(), details.getUid(), namespace);
            } else {
                // List is empty, meaning the resource was not found.
                log.warn("{} '{}' not found in namespace '{}'. Assuming already deleted.",
                        getResourceName(), name, namespace);
            }
        } catch (KubernetesClientException e) {
            log.warn("Failed to delete {} '{}' in namespace '{}': {} (Code: {})",
                    getResourceName(), name, namespace, e.getMessage(), e.getCode(), e);
            throw e;
        } catch (Exception e) {
            log.warn("An unexpected error occurred while deleting {} '{}' in namespace '{}': {}",
                    getResourceName(), name, namespace, e.getMessage(), e);
            throw e;
        }
    }

    public T getService(String namespace, String name) {
        log.debug("Getting {} '{}' from namespace '{}'", getResourceName(), name, namespace);
        try {
            var service = getClient().inNamespace(namespace)
                    .withName(name)
                    .get();
            log.debug("Successfully retrieved {} '{}' from namespace '{}'", getResourceName(), name, namespace);
            return service;
        } catch (KubernetesClientException e) {
            if (e.getCode() == 404) {
                log.debug("{} '{}' not found in namespace '{}' (404)", getResourceName(), name, namespace);
                return null;
            }
            log.warn("Error retrieving {} '{}' from namespace '{}': {} (Code: {})",
                    getResourceName(), name, namespace, e.getMessage(), e.getCode());
            throw e;
        }
    }

    public SharedIndexInformer<T> createInformer(String namespace, long resyncIntervalSec) {
        log.info("Creating informer for {} in namespace '{}' with resync interval {} sec",
                getResourceName(), namespace, resyncIntervalSec);
        return getClient().inNamespace(namespace).runnableInformer(resyncIntervalSec * 1000);
    }

    protected abstract MixedOperation<T, L, Resource<T>> getClient();

    protected abstract String getLabelKey();

    protected abstract String getResourceName();

    protected void configureDeletion(Resource<T> resource) {
        // Default implementation does nothing, can be overridden by subclasses
    }

}
