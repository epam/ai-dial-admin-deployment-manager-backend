package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceKind;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceReference;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.DeploymentException;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.EnvVarValue;
import com.epam.aidial.deployment.manager.model.FileEnvVarValue;
import com.epam.aidial.deployment.manager.model.PodInfo;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SensitiveFileEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.service.pipeline.specification.CiliumNetworkPolicyCreator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractDeploymentManager<D extends Deployment, S> implements DeploymentManager<S> {

    protected final K8sClient k8sClient;
    protected final ManifestGenerator manifestGenerator;
    protected final DeploymentRepository deploymentRepository;
    protected final ContainerPortResolver containerPortResolver;
    protected final DisposableResourceManager disposableResourceManager;
    protected final CiliumNetworkPolicyCreator ciliumNetworkPolicyCreator;

    protected final String namespace;
    protected final int startupTimeoutSec;
    protected final Integer defaultContainerPort;

    protected AbstractDeploymentManager(K8sClient k8sClient,
                                        DisposableResourceManager disposableResourceManager,
                                        ManifestGenerator manifestGenerator,
                                        DeploymentRepository deploymentRepository,
                                        ContainerPortResolver containerPortResolver,
                                        CiliumNetworkPolicyCreator ciliumNetworkPolicyCreator,
                                        String namespace,
                                        int startupTimeoutSec,
                                        Integer defaultContainerPort) {
        this.k8sClient = k8sClient;
        this.manifestGenerator = manifestGenerator;
        this.deploymentRepository = deploymentRepository;
        this.containerPortResolver = containerPortResolver;
        this.disposableResourceManager = disposableResourceManager;
        this.ciliumNetworkPolicyCreator = ciliumNetworkPolicyCreator;
        this.namespace = namespace;
        this.startupTimeoutSec = startupTimeoutSec;
        this.defaultContainerPort = defaultContainerPort;
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Deployment deploy(UUID id) {
        var deployment = getDeployment(id);

        if (deployment.getStatus().isActive()) {
            log.info("Deployment '{}' is already active; skipping deploy", id);
            return deployment;
        }

        try {
            var serviceSpec = prepareServiceSpec(deployment);

            saveDisposableResource(id, namespace);
            deploymentRepository.updateStatus(id, DeploymentStatus.PENDING);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        createCiliumNetworkPolicy(id, deployment.getAllowedDomains());
                        createService(namespace, serviceSpec);
                    } catch (Exception e) {
                        var errorMessage = "Failed to deploy service '%s'".formatted(id);
                        log.warn(errorMessage, e);
                        markDisposableResourcesForCleanup(id, namespace);
                        throw new DeploymentException(errorMessage, e);
                    }
                }
            });

            deployment.setStatus(DeploymentStatus.PENDING);
            return deployment;

        } catch (Exception e) {
            var errorMessage = "Failed to deploy service '%s'".formatted(id);
            log.warn(errorMessage, e);
            markDisposableResourcesForCleanup(id, namespace);
            throw new DeploymentException(errorMessage, e);
        }
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Deployment undeploy(UUID id) {
        var deployment = getDeployment(id);
        if (deployment.getStatus().isInactive() || deployment.getStatus() == DeploymentStatus.STOPPING) {
            log.info("Deployment '{}' is not active; skipping undeploy", id);
            return deployment;
        }

        try {
            var serviceName = getServiceName(id);
            var cnpName = CiliumNetworkPolicyCreator.getPolicyName(serviceName);
            var disposableResources = markDisposableResourcesForCleanup(id, namespace);
            deploymentRepository.updateStatus(id, DeploymentStatus.STOPPING);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        deleteService(namespace, serviceName);
                        deleteCiliumNetworkPolicy(cnpName);
                        disposableResourceManager.deleteAll(disposableResources);
                    } catch (Exception e) {
                        var errorMessage = "Unexpected error while undeploying service '%s'".formatted(id);
                        log.warn(errorMessage, e);
                        throw new DeploymentException(errorMessage, e);
                    }
                }
            });

            deployment.setStatus(DeploymentStatus.STOPPING);
            return deployment;

        } catch (Exception e) {
            var errorMessage = "Unexpected error while undeploying service '%s'".formatted(id);
            log.warn(errorMessage, e);
            throw new DeploymentException(errorMessage, e);
        }
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void rollingUpdate(UUID id) {
        var deployment = getDeployment(id);

        if (deployment.getStatus() != DeploymentStatus.RUNNING) {
            log.info("Deployment '{}' is not running; skipping rolling update", id);
            return;
        }

        try {
            var serviceSpec = prepareServiceSpec(deployment);
            deploymentRepository.updateStatus(id, DeploymentStatus.PENDING);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        updateCiliumNetworkPolicy(id, deployment.getAllowedDomains());
                        updateService(namespace, serviceSpec);
                    } catch (Exception e) {
                        var errorMessage = "Rolling update failed for deployment '%s'".formatted(id);
                        log.warn(errorMessage, e);
                        throw new DeploymentException(errorMessage, e);
                    }
                }
            });

        } catch (Exception e) {
            var errorMessage = "Rolling update failed for deployment '%s'".formatted(id);
            log.warn(errorMessage, e);
            throw new DeploymentException(errorMessage, e);
        }
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public boolean reconcile(UUID id, boolean ignorePendingOnServiceNotFound) {
        var serviceName = getServiceName(id);
        var service = getService(namespace, serviceName);
        var reconcileConfig = ReconcileConfig.<S>builder()
                .deploymentId(id)
                .service(service)
                .serviceIsMissing(service == null)
                .initiator("Reconciliation")
                .ignorePendingOnServiceNotFound(ignorePendingOnServiceNotFound)
                .build();
        return reconcile(reconcileConfig);
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public boolean reconcile(ReconcileConfig<S> config) {
        var deploymentId = config.getDeploymentId();
        var initiator = config.getInitiator();
        var service = config.getService();

        var deploymentOptional = getDeploymentOptional(deploymentId);
        if (deploymentOptional.isEmpty()) {
            log.warn("{}: deployment '{}' not found in DB. Skipping", initiator, deploymentId);
            return false;
        }
        var deployment = deploymentOptional.get();

        var status = config.isServiceIsMissing() ? DeploymentStatus.NOT_DEPLOYED : mapStatus(service);
        if (log.isDebugEnabled()) {
            log.debug("{}: started state sync for deployment '{}' with DB status {} and K8s status {}. Service: {}",
                    initiator, deploymentId, deployment.getStatus(), status, Serialization.asYaml(service));
        }

        try {
            // If statuses match, no update needed
            if (status == deployment.getStatus()) {
                log.debug("{}: deployment '{}' status is already {}, no update required", initiator, deploymentId, status);
                return false;
            }

            if (status.isInactive()) {
                if (deployment.getStatus().isInactive()
                        || (deployment.getStatus() == DeploymentStatus.PENDING && config.isIgnorePendingOnServiceNotFound())) {
                    log.debug("{}: deployment '{}' not found in Kubernetes and marked as {} in DB. Skipping",
                            initiator, deploymentId, deployment.getStatus());
                    return false;
                }

                log.info("{}: deployment '{}' not found in Kubernetes but marked as {} in DB. Updating to STOPPED",
                        initiator, deploymentId, deployment.getStatus());
                deployment.setUrl(null);
                deployment.setStatus(DeploymentStatus.STOPPED);
                deploymentRepository.update(deploymentId, deployment); //could lead to serialization problem
                return true;
            }

            // If K8s status is RUNNING, extract URL and perform health checks
            if (status == DeploymentStatus.RUNNING) {
                // No need to look for deployments stuck in 'stopping' state because if stopping fails/hangs,
                //   state will be changed to 'stopped' and the cleaner will pick it up on the next scheduled run
                if (deployment.getStatus() == DeploymentStatus.STOPPING) {
                    log.debug("{}: deployment '{}' is active in Kubernetes but stopping was already initiated. Skipping", initiator, deploymentId);
                    return false;
                }

                var serviceName = getServiceName(deploymentId);

                log.info("{}: deployment '{}' appears RUNNING in K8s (service '{}'), triggering readiness check to set URL",
                        initiator, deploymentId, serviceName);
                try {
                    var serviceUrl = resolveServiceUrl(service, deployment);
                    // TODO: Hotfix: Addressing a slowdown in DeploymentWatcherBootstrap that is currently blocking deployment-manager from starting up successfully.
                    // performHealthChecks(deployment, serviceUrl, System.currentTimeMillis());
                    deployment.setUrl(serviceUrl);
                    deployment.setStatus(DeploymentStatus.RUNNING);
                    deploymentRepository.update(deploymentId, deployment);
                    log.info("{}: deployment '{}' marked RUNNING in DB after successful readiness check", initiator, deploymentId);
                    return true;
                } catch (Exception e) {
                    deploymentRepository.updateStatus(deploymentId, DeploymentStatus.CRASHED);
                    log.warn("{}: error during readiness check for deployment '{}'", initiator, deploymentId, e);
                    return false;
                }
            }

            // For other statuses, just update the status
            log.info("{}: updating deployment '{}' status from {} to {}", initiator, deploymentId, deployment.getStatus(), status);
            deploymentRepository.updateStatus(deploymentId, status);
            return true;

        } catch (Exception e) {
            log.error("{}: unexpected error for deployment '{}'", initiator, deploymentId, e);
            throw e;
        }
    }

    @Override
    public List<SensitiveEnvVar> provisionSecrets(UUID deploymentId, EnvPartition envPartition) {
        var sensitiveEnvs = envPartition.sensitive();
        var sensitiveFileEnvs = envPartition.sensitiveFile();

        if (MapUtils.isEmpty(sensitiveEnvs) && MapUtils.isEmpty(sensitiveFileEnvs)) {
            return List.of();
        }

        var secretName = K8sNamingUtils.generateUniqueName(deploymentId.toString(), "envs");
        var transformedSensitiveEnvs = transformEnvs(sensitiveEnvs);
        var transformedSensitiveFileEnvs = transformEnvs(sensitiveFileEnvs);
        var secret = manifestGenerator.secretConfig(secretName, transformedSensitiveEnvs, transformedSensitiveFileEnvs);

        disposableResourceManager.saveK8sResources(List.of(secret), K8sResourceKind.SECRET, deploymentId, this.namespace);
        k8sClient.createSecret(this.namespace, secret);
        disposableResourceManager.changeResourceLifecycleByGroupIdInSameTransaction(
                deploymentId,
                new K8sResourceReference(this.namespace, K8sResourceKind.SECRET, secretName),
                ResourceLifecycleState.STABLE
        );

        var sensitiveEnvVars = sensitiveEnvs.entrySet().stream()
                .map(e -> SensitiveEnvVar.builder()
                        .name(e.getKey())
                        .value(e.getValue())
                        .k8sSecretName(secretName)
                        .k8sSecretKey(e.getKey())
                        .build())
                .toList();
        var sensitiveFileEnvVars = sensitiveFileEnvs.entrySet().stream()
                .map(e -> SensitiveFileEnvVar.builder()
                        .name(e.getKey())
                        .value(e.getValue())
                        .k8sSecretName(secretName)
                        .k8sSecretKey(e.getKey())
                        .build())
                .toList();

        return ListUtils.union(sensitiveEnvVars, sensitiveFileEnvVars);
    }

    @Override
    public void cleanupSecrets(UUID deploymentId, List<EnvVar> currentEnvs) {
        currentEnvs.stream()
                .filter(SensitiveEnvVar.class::isInstance)
                .map(SensitiveEnvVar.class::cast)
                .map(env -> new K8sResourceReference(this.namespace, K8sResourceKind.SECRET, env.getK8sSecretName()))
                .forEach(res -> disposableResourceManager
                        .changeResourceLifecycleByGroupIdInSameTransaction(deploymentId, res, ResourceLifecycleState.TO_CLEANUP));
    }

    @Override
    public Deployment resolveSecrets(Deployment deployment) {
        var sensitive = deployment.getEnvs().stream()
                .filter(SensitiveEnvVar.class::isInstance)
                .map(SensitiveEnvVar.class::cast)
                .collect(Collectors.toMap(SensitiveEnvVar::getName, Function.identity()));
        if (sensitive.isEmpty()) {
            return deployment;
        }

        var secrets = sensitive.values().stream()
                .map(SensitiveEnvVar::getK8sSecretName)
                .distinct()
                .map(name -> k8sClient.findSecret(namespace, name).orElseThrow(notFound("Secret", name)))
                .collect(Collectors.toMap(s -> s.getMetadata().getName(), Function.identity()));

        sensitive.values().forEach(env -> {
            var secret = secrets.get(env.getK8sSecretName());
            var value = Optional.ofNullable(secret.getData())
                    .map(d -> d.get(env.getK8sSecretKey()))
                    .map(v -> env instanceof SensitiveFileEnvVar ? v : new String(Base64.getDecoder().decode(v)))
                    .orElseThrow(() -> new EntityNotFoundException("Secret key %s missing in %s"
                            .formatted(env.getK8sSecretKey(), env.getK8sSecretName())));

            EnvVarValue envVarValue;
            if (env.getValue() instanceof FileEnvVarValue fileEnvVarValue) {
                envVarValue = new FileEnvVarValue(fileEnvVarValue.fileName(), value);
            } else {
                envVarValue = new SimpleEnvVarValue(value);
            }
            env.setValue(envVarValue);
        });

        return deployment;
    }

    @Override
    public List<PodInfo> getActiveInstances(UUID id) {
        return getInstances(id, pod -> isPodReady(pod.getStatus()));
    }

    @Override
    public List<PodInfo> getInstances(UUID id) {
        return getInstances(id, null);
    }

    protected List<PodInfo> getInstances(UUID id, Predicate<? super Pod> filter) {
        var serviceName = getServiceName(id);
        var podList = getServicePods(namespace, serviceName);
        var podsStream = podList.stream();
        if (filter != null) {
            podsStream = podsStream.filter(filter);
        }
        return podsStream.map(pod -> new PodInfo(
                        pod.getMetadata().getName(),
                        Instant.parse(pod.getMetadata().getCreationTimestamp())
                ))
                .toList();
    }

    @Override
    public ContainerResource getContainerResource(UUID id, String podName) {
        var serviceName = getServiceName(id);

        var pod = getServicePod(namespace, serviceName, podName);
        if (pod == null) {
            log.warn("Pod '{}' not found for deployment '{}'", podName, serviceName);
            return null;
        }

        var containerName = getContainerName(pod);
        if (containerName == null) {
            throw new EntityNotFoundException("Container name could not be resolved for pod %s".formatted(podName));
        }

        return k8sClient.getPodResource(namespace, podName).inContainer(containerName);
    }

    @Override
    public NonNamespaceOperation<Event, EventList, Resource<Event>> getAllEventsBase() {
        return k8sClient.getAllEventsBase(namespace);
    }

    protected abstract String getServiceName(UUID id);

    protected abstract Optional<D> getDeploymentOptional(UUID id);

    protected D getDeployment(UUID id) {
        return getDeploymentOptional(id).orElseThrow(notFound("Deployment", id));
    }

    protected abstract S prepareServiceSpec(D deployment);

    protected abstract void createService(String namespace, S service);

    protected abstract void updateService(String namespace, S service);

    protected abstract void deleteService(String namespace, String name);

    protected abstract S getService(String namespace, String name);

    protected abstract List<Pod> getServicePods(String namespace, String name);

    protected abstract Pod getServicePod(String namespace, String name, String podName);

    protected abstract boolean isPodReady(PodStatus status);

    protected abstract String getContainerName(Pod pod);

    protected abstract String getServiceNameLabel();

    protected abstract void saveDisposableResource(UUID id, String namespace);

    protected abstract List<DisposableResource> markServiceDisposableResourcesForCleanup(UUID id, String namespace);

    protected abstract DeploymentStatus mapStatus(S service);

    protected abstract String resolveServiceUrl(S service, D deployment);

    protected void performHealthChecks(D deployment, String serviceUrl, long startTime) {
        // Default implementation does nothing
    }

    protected static <T extends EnvVar> List<T> filterEnvsByExactType(Deployment deployment, Class<T> type) {
        return deployment.getEnvs().stream()
                .filter(env -> type == env.getClass())
                .map(type::cast)
                .toList();
    }

    protected Integer resolveContainerPort(Supplier<Integer> portSupplier) {
        return containerPortResolver.resolveContainerPort(portSupplier, defaultContainerPort);
    }

    protected static <T> Supplier<EntityNotFoundException> notFound(String what, T id) {
        return () -> new EntityNotFoundException(String.format("%s not found: %s", what, id));
    }

    private List<DisposableResource> markDisposableResourcesForCleanup(UUID id, String namespace) {
        log.trace("markDisposableResourcesForCleanup. id='{}', namespace='{}'", id, namespace);
        var serviceDisposableResources = markServiceDisposableResourcesForCleanup(id, namespace);
        log.debug("Service disposable resources marked for cleanup: {}", serviceDisposableResources);

        List<DisposableResource> disposableResources = new ArrayList<>(serviceDisposableResources);

        var cnpName = CiliumNetworkPolicyCreator.getPolicyName(getServiceName(id));
        log.trace("markDisposableResourcesForCleanup. Cilium Network Policy name resolved: '{}'", cnpName);

        var cnpDisposableResources = disposableResourceManager.markCiliumNetworkPolicyResourceForCleanup(id, namespace, cnpName);
        log.debug("Cilium Network Policy disposable resources marked for cleanup: {}", cnpDisposableResources);

        disposableResources.addAll(cnpDisposableResources);

        return disposableResources;
    }

    private void createCiliumNetworkPolicy(UUID groupId, List<String> allowedDomains) {
        log.trace("createCiliumNetworkPolicy. groupId='{}', allowedDomains={}", groupId, allowedDomains);
        if (!ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()) {
            log.debug("Cilium Network Policies are not enabled. Skipping creation.");
            return;
        }
        var serviceNameLabel = getServiceNameLabel();
        var serviceName = getServiceName(groupId);
        log.trace("createCiliumNetworkPolicy. serviceNameLabel='{}', serviceName='{}'", serviceNameLabel, serviceName);

        var ciliumNetworkPolicy = ciliumNetworkPolicyCreator.create(namespace, serviceNameLabel, serviceName, allowedDomains);

        disposableResourceManager.saveK8sResources(List.of(ciliumNetworkPolicy), K8sResourceKind.CILIUM_NETWORK_POLICY, groupId, namespace);
        log.trace("createCiliumNetworkPolicy. Saved Cilium Network Policy as disposable resource for groupId='{}' in namespace='{}'", groupId, namespace);

        k8sClient.createCiliumNetworkPolicy(namespace, ciliumNetworkPolicy);
        log.trace("createCiliumNetworkPolicy. Created Cilium Network Policy: {}", ciliumNetworkPolicy);
    }

    private void updateCiliumNetworkPolicy(UUID groupId, List<String> allowedDomains) {
        log.trace("updateCiliumNetworkPolicy. groupId='{}', allowedDomains={}", groupId, allowedDomains);
        if (!ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()) {
            log.debug("Cilium Network Policies are not enabled. Skipping update.");
            return;
        }
        var serviceNameLabel = getServiceNameLabel();
        var serviceName = getServiceName(groupId);
        log.trace("updateCiliumNetworkPolicy. serviceNameLabel='{}', serviceName='{}'", serviceNameLabel, serviceName);

        var ciliumNetworkPolicy = ciliumNetworkPolicyCreator.create(namespace, serviceNameLabel, serviceName, allowedDomains);

        k8sClient.updateCiliumNetworkPolicy(namespace, ciliumNetworkPolicy);
        log.trace("updateCiliumNetworkPolicy. Updated Cilium Network Policy: {}", ciliumNetworkPolicy);
    }

    private void deleteCiliumNetworkPolicy(String name) {
        log.trace("deleteCiliumNetworkPolicy. name='{}'", name);
        try {
            k8sClient.deleteCiliumNetworkPolicy(namespace, name);
            log.trace("deleteCiliumNetworkPolicy. Deleted Cilium Network Policy '{}' in namespace='{}'", name, namespace);
        } catch (Exception e) {
            String message = "Failed to delete Cilium Network Policy '%s' in namespace: %s"
                    .formatted(name, namespace);
            if (ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()) {
                log.warn(message, e);
                throw e;
            } else {
                log.debug("{}. Cilium Network Policies are disabled", message);
            }
        }
    }

    private Map<String, String> transformEnvs(Map<String, EnvVarValue> envs) {
        return MapUtils.emptyIfNull(envs).entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue().getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
