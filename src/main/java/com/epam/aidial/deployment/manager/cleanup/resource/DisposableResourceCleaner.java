package com.epam.aidial.deployment.manager.cleanup.resource;

import com.epam.aidial.deployment.manager.cleanup.resource.model.ContainerRegistryResourceReference;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceKind;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceReference;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.docker.DockerRegistryClient;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.knative.K8sKnativeClient;
import com.epam.aidial.deployment.manager.kubernetes.kserve.K8sKserveClient;
import com.epam.aidial.deployment.manager.kubernetes.nim.K8sNimClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class DisposableResourceCleaner {

    private static final String TYPE_NOT_SUPPORTED_MESSAGE = "Resource reference of type '%s' is not supported";
    private static final String CLEANED_UP_MESSAGE_FORMAT = "Cleaned up {} '{}' in namespace '{}'";
    private static final String IMAGE_KEY = "IMAGE";

    private final DisposableResourceManager disposableResourceManager;
    private final K8sClient k8sClient;
    private final K8sKnativeClient k8sKnativeClient;
    private final K8sNimClient k8sNimClient;
    private final K8sKserveClient k8sKserveClient;
    private final DockerRegistryClient registryClient;

    @Value("${app.resource-cleaner-take-size}")
    private int takeSize;

    public void cleanAllCleanable() {
        cleanAllByLifecycle(disposableResourceManager::getAllCleanable, "cleanable");
    }

    public void cleanAllTemporary() {
        cleanAllByLifecycle(disposableResourceManager::getAllTemporary, "temporary");
    }

    private void cleanAllByLifecycle(Function<Integer, List<DisposableResource>> resourcesRetriever, String lifecycleStateForLog) {
        while (true) {
            var resources = resourcesRetriever.apply(takeSize);
            cleanDisposableResources(resources);
            if (resources.size() < takeSize) {
                break;
            }
        }
        log.info("Cleaned up all {} disposable resources", lifecycleStateForLog);
    }

    public void cleanAllCleanableByGroupId(String groupId) {
        var resources = disposableResourceManager.getAllCleanableByGroupId(groupId);
        cleanDisposableResources(resources);
        log.info("Cleaned up all cleanable disposable resources by group ID '{}'", groupId);
    }

    public void cleanTemporaryByGroupId(UUID groupId) {
        var resources = disposableResourceManager.getAllTemporaryByGroupId(String.valueOf(groupId));
        cleanDisposableResources(resources);
        log.info("Cleaned up all temporary disposable resources by group ID '{}'", groupId);
    }

    private void cleanDisposableResources(List<DisposableResource> resources) {
        Map<String, List<DisposableResource>> resourcesByKind = new HashMap<>();

        for (var resource : resources) {
            String key;

            var reference = resource.getReference();
            if (reference instanceof K8sResourceReference k8sResourceRef) {
                key = k8sResourceRef.getKind().name();
            } else if (reference instanceof ContainerRegistryResourceReference) {
                key = IMAGE_KEY;
            } else {
                throw new IllegalArgumentException(
                        TYPE_NOT_SUPPORTED_MESSAGE.formatted(reference.getClass().getSimpleName())
                );
            }

            resourcesByKind.computeIfAbsent(key, k -> new ArrayList<>()).add(resource);
        }

        List<DisposableResource> resourcesToDelete = new ArrayList<>();

        // order is important
        resourcesToDelete.addAll(clean(resourcesByKind.get(K8sResourceKind.KNATIVE_SERVICE.name())));
        resourcesToDelete.addAll(clean(resourcesByKind.get(K8sResourceKind.NIM_SERVICE.name())));
        resourcesToDelete.addAll(clean(resourcesByKind.get(K8sResourceKind.INFERENCE_SERVICE.name())));
        resourcesToDelete.addAll(clean(resourcesByKind.get(K8sResourceKind.JOB.name())));
        resourcesToDelete.addAll(clean(resourcesByKind.get(K8sResourceKind.CONFIGMAP.name())));
        resourcesToDelete.addAll(clean(resourcesByKind.get(K8sResourceKind.SECRET.name())));
        resourcesToDelete.addAll(clean(resourcesByKind.get(K8sResourceKind.CILIUM_NETWORK_POLICY.name())));
        resourcesToDelete.addAll(clean(resourcesByKind.get(IMAGE_KEY)));

        disposableResourceManager.deleteAll(resourcesToDelete);
    }

    private List<DisposableResource> clean(List<DisposableResource> resources) {
        List<DisposableResource> resourcesToDelete = new ArrayList<>();
        Optional.ofNullable(resources)
                .orElseGet(ArrayList::new)
                .forEach(resource -> {
                    boolean isCleaned = clean(resource);
                    if (isCleaned) {
                        resourcesToDelete.add(resource);
                    }
                });
        return resourcesToDelete;
    }

    private boolean clean(DisposableResource resource) {
        var reference = resource.getReference();
        try {
            if (reference instanceof K8sResourceReference k8sResourceRef) {
                cleanK8sResource(k8sResourceRef);
            } else if (reference instanceof ContainerRegistryResourceReference crResourceRef) {
                cleanContainerRegistryResource(crResourceRef);
            } else {
                throw new IllegalArgumentException(
                        TYPE_NOT_SUPPORTED_MESSAGE.formatted(reference.getClass().getSimpleName())
                );
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to clean up disposable resource '%s'".formatted(resource.getId()), e);
            return false;
        }
    }

    private void cleanK8sResource(K8sResourceReference reference) {
        var kind = reference.getKind();
        var namespace = reference.getNamespace();
        var name = reference.getName();

        switch (kind) {
            case KNATIVE_SERVICE -> {
                k8sKnativeClient.deleteService(namespace, name);
                log.debug(CLEANED_UP_MESSAGE_FORMAT, "Knative Service", name, namespace);
            }
            case NIM_SERVICE -> {
                k8sNimClient.deleteService(namespace, name);
                log.debug(CLEANED_UP_MESSAGE_FORMAT, "NIM Service", name, namespace);
            }
            case INFERENCE_SERVICE -> {
                k8sKserveClient.deleteService(namespace, name);
                log.debug(CLEANED_UP_MESSAGE_FORMAT, "Inference Service", name, namespace);
            }
            case JOB -> {
                k8sClient.deleteJob(namespace, name);
                log.debug(CLEANED_UP_MESSAGE_FORMAT, "Job", name, namespace);
            }
            case CONFIGMAP -> {
                k8sClient.deleteConfigMap(namespace, name);
                log.debug(CLEANED_UP_MESSAGE_FORMAT, "Config Map", name, namespace);
            }
            case SECRET -> {
                k8sClient.deleteSecret(namespace, name);
                log.debug(CLEANED_UP_MESSAGE_FORMAT, "Secret", name, namespace);
            }
            case CILIUM_NETWORK_POLICY -> {
                k8sClient.deleteCiliumNetworkPolicy(namespace, name);
                log.debug(CLEANED_UP_MESSAGE_FORMAT, "Cilium Network Policy", name, namespace);
            }
            default -> throw new IllegalArgumentException(
                    TYPE_NOT_SUPPORTED_MESSAGE.formatted(reference.getClass().getSimpleName())
            );
        }
    }

    private void cleanContainerRegistryResource(ContainerRegistryResourceReference reference) {
        var imageName = reference.getName();
        try {
            registryClient.deleteImage(imageName);
        } catch (Exception e) {
            var message = "Failed to delete image %s from container registry".formatted(imageName);
            log.error(message, e);
            throw new RuntimeException(message);
        }
    }
}
