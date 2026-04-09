package com.epam.aidial.deployment.manager.kubernetes.knative;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.AbstractK8sResourceClient;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import io.fabric8.knative.client.KnativeClient;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.knative.serving.v1.ServiceList;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
@Component
@LogExecution
public class K8sKnativeClient extends AbstractK8sResourceClient<Service, ServiceList> {

    private final KnativeClient knativeClient;

    public K8sKnativeClient(KnativeClient knativeClient, K8sClient k8sClient) {
        super(k8sClient);
        this.knativeClient = knativeClient;
    }

    @Override
    protected MixedOperation<Service, ServiceList, Resource<Service>> getClient() {
        return knativeClient.services();
    }

    @Override
    protected String getLabelKey() {
        return KnativeAnnotations.SERVICE;
    }

    @Override
    protected String getResourceName() {
        return "Knative Service";
    }

    @Override
    protected void configureDeletion(Resource<Service> resource) {
        resource.withPropagationPolicy(DeletionPropagation.FOREGROUND)
                .withGracePeriod(0L);
    }

    public void updateService(String namespace, Service service)
            throws KubernetesClientException, IllegalStateException {

        var name = service.getMetadata().getName();

        try {
            log.info("Updating Knative Service '{}' in namespace '{}'", name, namespace);

            var existingService = getService(namespace, name);

            if (existingService == null) {
                throw new IllegalStateException("Service '" + name + "' not found in namespace '" + namespace + "'");
            }

            // Preserve immutable annotations from the existing service
            var immutableAnnotations = existingService.getMetadata().getAnnotations();
            if (immutableAnnotations != null) {
                var newAnnotations = Optional.ofNullable(service.getMetadata().getAnnotations())
                        .orElse(new HashMap<>());

                if (immutableAnnotations.containsKey(KnativeAnnotations.CREATOR)) {
                    newAnnotations.put(KnativeAnnotations.CREATOR, immutableAnnotations.get(KnativeAnnotations.CREATOR));
                    service.getMetadata().setAnnotations(newAnnotations);
                }
            }

            super.updateService(namespace, service);

        } catch (KubernetesClientException e) {
            log.warn("Kubernetes API error during Knative Service '{}' update: {}", name, e.getMessage(), e);
            throw e;
        }
    }

    public void waitForDeletion(String namespace, String name, int timeoutSec) {
        log.debug("Waiting for Knative Service '{}' in namespace '{}' to be fully deleted (timeout: {}s)...",
                name, namespace, timeoutSec);

        try {
            var resource = knativeClient.services()
                    .inNamespace(namespace)
                    .withName(name);

            // Wait until the service is deleted - waitUntilCondition returns when predicate is true
            // The predicate returns true when service is null (deleted)
            // waitUntilCondition will handle 404 exceptions internally and pass null to predicate when deleted
            resource.waitUntilCondition(Objects::isNull, timeoutSec, SECONDS);

            log.info("Knative Service '{}' in namespace '{}' has been fully deleted.", name, namespace);

        } catch (KubernetesClientException e) {
            // If the service is already deleted (404), that's fine
            if (e.getCode() == 404) {
                log.info("Knative Service '{}' in namespace '{}' is already deleted.", name, namespace);
                return;
            }

            throw e;
        }
    }

    public void deleteServiceAndAllRunningPods(String namespace, String name) {
        log.info("Deleting Knative Service '{}' in namespace '{}' and all running pods...", name, namespace);

        deleteService(namespace, name);

        // Delete all pods immediately to ensure complete cleanup
        deleteAllRunningPods(namespace, name);
    }

    private void deleteAllRunningPods(String namespace, String appName) {
        // Knative has a default termination grace period and ignores any configured value.
        // Therefore, an extra step is performed to delete pods instantly.
        try {
            var podList = getServicePods(namespace, appName);
            podList.getItems()
                    .forEach(p -> k8sClient.deletePod(namespace, p.getMetadata().getName()));
        } catch (Exception e) {
            log.warn("Failed to delete pods for service {}: {}", appName, e.getMessage());
            throw e;
        }
    }

}
