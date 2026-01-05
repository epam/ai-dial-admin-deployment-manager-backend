package com.epam.aidial.deployment.manager.cleanup.resource;

import com.epam.aidial.deployment.manager.cleanup.resource.model.ContainerRegistryResourceReference;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceKind;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceReference;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceReference;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DisposableResourceRepository;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.fabric8.kubernetes.api.model.HasMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class DisposableResourceManager {

    private static final Set<ResourceLifecycleState> CLEANABLE_STATES = Set.of(
            ResourceLifecycleState.TO_CLEANUP, ResourceLifecycleState.TEMPORARY
    );

    private final DisposableResourceRepository resourceRepository;

    @Transactional(readOnly = true)
    public List<DisposableResource> getAllByGroupId(UUID groupId) {
        return resourceRepository.getAllByGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public List<DisposableResource> getAllCleanable(int take) {
        return resourceRepository.getAllByLifecycleStates(CLEANABLE_STATES, take);
    }

    @Transactional(readOnly = true)
    public List<DisposableResource> getAllTemporary(int take) {
        return resourceRepository.getAllByLifecycleStates(Set.of(ResourceLifecycleState.TEMPORARY), take);
    }

    @Transactional(readOnly = true)
    public List<DisposableResource> getAllCleanableByGroupId(UUID groupId) {
        return resourceRepository.getAllByGroupIdAndLifecycleStates(groupId, CLEANABLE_STATES);
    }

    @Transactional(readOnly = true)
    public List<DisposableResource> getAllTemporaryByGroupId(UUID groupId) {
        return resourceRepository.getAllByGroupIdAndLifecycleStates(groupId, Set.of(ResourceLifecycleState.TEMPORARY));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markResourcesForCleanupByGroupId(UUID groupId) {
        var disposableResources = getAllByGroupId(groupId);
        disposableResources.forEach(rs -> rs.setLifecycleState(ResourceLifecycleState.TO_CLEANUP));
        resourceRepository.saveAll(disposableResources);
        log.debug("Marked for clean-up all disposable resources related to group ID: {}", groupId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<DisposableResource> markKnativeServiceResourceForCleanup(UUID id, String namespace) {
        return markServiceResourceForCleanup(id, namespace, K8sResourceKind.KNATIVE_SERVICE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<DisposableResource> markNimServiceResourceForCleanup(UUID id, String namespace) {
        return markServiceResourceForCleanup(id, namespace, K8sResourceKind.NIM_SERVICE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<DisposableResource> markInferenceServiceResourceForCleanup(UUID id, String namespace) {
        return markServiceResourceForCleanup(id, namespace, K8sResourceKind.INFERENCE_SERVICE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<DisposableResource> markCiliumNetworkPolicyResourceForCleanup(UUID groupId, String namespace, String name) {
        var reference = K8sResourceReference.builder()
                .kind(K8sResourceKind.CILIUM_NETWORK_POLICY)
                .namespace(namespace)
                .name(name)
                .build();
        return changeResourceLifecycleInternal(groupId, reference, ResourceLifecycleState.TO_CLEANUP);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void changeResourceLifecycleByGroupIdInSameTransaction(
            UUID groupId,
            ResourceReference resourceReference,
            ResourceLifecycleState state
    ) {
        changeResourceLifecycleInternal(groupId, resourceReference, state);
    }

    private List<DisposableResource> changeResourceLifecycleInternal(
            UUID groupId,
            ResourceReference resourceReference,
            ResourceLifecycleState state
    ) {
        var disposableResources = resourceRepository.findAllByGroupIdAndReference(groupId, resourceReference);
        disposableResources.forEach(rs -> rs.setLifecycleState(state));
        if (!disposableResources.isEmpty()) {
            resourceRepository.saveAll(disposableResources);
            log.debug("Updated lifecycle to {} for {} disposable resource(s) related to group ID: {} and reference: {}",
                    state, disposableResources.size(), groupId, resourceReference);
        }
        return disposableResources;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveK8sResources(List<? extends HasMetadata> k8sResources,
                                 K8sResourceKind resourceKind,
                                 UUID groupId,
                                 String namespace) {
        var now = Instant.now();
        var resources = k8sResources.stream()
                .map(rs -> DisposableResource.builder()
                        .groupId(groupId)
                        .reference(K8sResourceReference.builder()
                                .kind(resourceKind)
                                .namespace(namespace)
                                .name(K8sNamingUtils.extractName(rs))
                                .build())
                        .lifecycleState(ResourceLifecycleState.TEMPORARY)
                        .createdAt(now)
                        .build()
                ).toList();
        resourceRepository.saveAll(resources);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveKnativeServiceResource(UUID id, String namespace) {
        saveServiceResource(id, namespace, K8sResourceKind.KNATIVE_SERVICE, ResourceLifecycleState.STABLE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNimServiceResource(UUID id, String namespace) {
        saveServiceResource(id, namespace, K8sResourceKind.NIM_SERVICE, ResourceLifecycleState.STABLE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveInferenceServiceResource(UUID id, String namespace) {
        saveServiceResource(id, namespace, K8sResourceKind.INFERENCE_SERVICE, ResourceLifecycleState.STABLE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveContainerRegistryResource(String imageName, UUID groupId, ResourceLifecycleState lifecycleState) {
        var imageResource = DisposableResource.builder()
                .groupId(groupId)
                .reference(ContainerRegistryResourceReference.builder()
                        .name(imageName)
                        .build())
                .lifecycleState(lifecycleState)
                .createdAt(Instant.now())
                .build();
        resourceRepository.save(imageResource);
    }

    @Transactional
    public void deleteAll(List<DisposableResource> resources) {
        resourceRepository.deleteAll(resources);
    }

    @Transactional
    public void delete(DisposableResource resource) {
        resourceRepository.delete(resource);
    }

    private List<DisposableResource> markServiceResourceForCleanup(UUID id, String namespace, K8sResourceKind kind) {
        var reference = buildServiceReference(id, namespace, kind);
        return changeResourceLifecycleInternal(id, reference, ResourceLifecycleState.TO_CLEANUP);
    }

    private void saveServiceResource(UUID id, String namespace, K8sResourceKind kind, ResourceLifecycleState lifecycleState) {
        var serviceResource = DisposableResource.builder()
                .groupId(id)
                .reference(buildServiceReference(id, namespace, kind))
                .lifecycleState(lifecycleState)
                .createdAt(Instant.now())
                .build();
        resourceRepository.save(serviceResource);
    }

    private K8sResourceReference buildServiceReference(UUID id, String namespace, K8sResourceKind kind) {
        return K8sResourceReference.builder()
                .kind(kind)
                .namespace(namespace)
                .name(generateServiceName(id, kind))
                .build();
    }

    private String generateServiceName(UUID id, K8sResourceKind kind) {
        return switch (kind) {
            case INFERENCE_SERVICE -> K8sNamingUtils.generateName(id.toString());
            case KNATIVE_SERVICE, NIM_SERVICE -> K8sNamingUtils.generateMcpPrefixedName(id.toString());
            default -> throw new IllegalArgumentException("Unsupported service kind: " + kind);
        };
    }

}
