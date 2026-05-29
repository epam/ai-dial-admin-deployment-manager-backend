package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceKind;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceReference;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.DeploymentException;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.exception.ValidationException;
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
import com.epam.aidial.deployment.manager.service.manifest.PoolSchedulingPrimitives;
import com.epam.aidial.deployment.manager.service.pipeline.specification.CiliumNetworkPolicyCreator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import com.epam.aidial.deployment.manager.utils.K8sParseUtils;
import io.cilium.v2.CiliumNetworkPolicy;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    protected final NodePoolProperties nodePoolProperties;

    protected final String namespace;
    protected final int startupTimeoutSec;
    protected final int defaultContainerPort;

    protected AbstractDeploymentManager(K8sClient k8sClient,
                                        DisposableResourceManager disposableResourceManager,
                                        ManifestGenerator manifestGenerator,
                                        DeploymentRepository deploymentRepository,
                                        ContainerPortResolver containerPortResolver,
                                        CiliumNetworkPolicyCreator ciliumNetworkPolicyCreator,
                                        NodePoolProperties nodePoolProperties,
                                        String namespace,
                                        int startupTimeoutSec,
                                        int defaultContainerPort) {
        this.k8sClient = k8sClient;
        this.manifestGenerator = manifestGenerator;
        this.deploymentRepository = deploymentRepository;
        this.containerPortResolver = containerPortResolver;
        this.disposableResourceManager = disposableResourceManager;
        this.ciliumNetworkPolicyCreator = ciliumNetworkPolicyCreator;
        this.nodePoolProperties = nodePoolProperties;
        this.namespace = namespace;
        this.startupTimeoutSec = startupTimeoutSec;
        this.defaultContainerPort = defaultContainerPort;
    }

    protected PoolSchedulingPrimitives resolvePoolPrimitives(String nodePoolId) {
        if (StringUtils.isBlank(nodePoolId)) {
            return PoolSchedulingPrimitives.EMPTY;
        }
        var pool = nodePoolProperties.findById(nodePoolId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Node pool id '%s' is no longer configured".formatted(nodePoolId)));
        return PoolSchedulingPrimitives.of(pool);
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Deployment deploy(String id) {
        var deployment = getDeployment(id);

        if (deployment.getStatus().isActive()) {
            log.info("Deployment '{}' is already active; skipping deploy", id);
            return deployment;
        }

        try {
            if (StringUtils.isBlank(deployment.getServiceName())) {
                var serviceName = K8sNamingUtils.generateName(id);
                deployment.setServiceName(serviceName);
                deploymentRepository.updateServiceName(id, serviceName);
            }

            var serviceSpec = prepareServiceSpec(deployment);

            saveDisposableResource(id, deployment.getServiceName(), namespace);
            deploymentRepository.updateStatus(id, DeploymentStatus.PENDING);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        createCiliumNetworkPolicy(deployment, serviceSpec, id,
                                getEffectiveDeploymentAllowedDomains(deployment),
                                getCiliumIngressPorts(deployment), deployment.getServiceName());
                        createService(namespace, serviceSpec);
                    } catch (Exception e) {
                        var errorMessage = "Failed to deploy service '%s'".formatted(id);
                        log.warn(errorMessage, e);
                        markDisposableResourcesForCleanup(id, namespace, deployment.getServiceName(), deployment.getServiceName());
                        if (isUnrecoverableK8sError(e)) {
                            log.warn("Unrecoverable Kubernetes error for deployment '{}' (HTTP {}). Marking as STOPPED.",
                                    id, ((KubernetesClientException) e).getCode());
                            deploymentRepository.updateStatusInNewTransaction(id, DeploymentStatus.STOPPED);
                        }
                        throw new DeploymentException(errorMessage, e);
                    }
                }
            });

            deployment.setStatus(DeploymentStatus.PENDING);
            return deployment;

        } catch (Exception e) {
            var errorMessage = "Failed to deploy service '%s'".formatted(id);
            log.warn(errorMessage, e);
            markDisposableResourcesForCleanup(id, namespace, deployment.getServiceName(), deployment.getServiceName());
            throw new DeploymentException(errorMessage, e);
        }
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Deployment undeploy(String id) {
        var deployment = getDeployment(id);
        if (deployment.getStatus().isInactive() || deployment.getStatus() == DeploymentStatus.STOPPING) {
            log.info("Deployment '{}' is not active; skipping undeploy", id);
            return deployment;
        }

        try {
            var serviceName = getServiceName(id);
            var cnpName = resolveCiliumNetworkPolicyName(id, serviceName);
            var disposableResources = markDisposableResourcesForCleanup(id, namespace, serviceName, cnpName);
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
    public Deployment rollingUpdate(String id) {
        var deployment = getDeployment(id);

        if (deployment.getStatus() != DeploymentStatus.RUNNING) {
            log.info("Deployment '{}' is not running; skipping rolling update", id);
            return deployment;
        }

        try {
            var serviceSpec = prepareServiceSpec(deployment);
            deploymentRepository.updateStatus(id, DeploymentStatus.PENDING);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        // Refresh CNP BEFORE updateService: on topology flips a half-applied update
                        // where the new topology is live but the CNP still blocks the new traffic.
                        // Over-permissive briefly > under-permissive.
                        updateCiliumNetworkPolicy(deployment, serviceSpec, id, deployment.getServiceName(),
                                getEffectiveDeploymentAllowedDomains(deployment),
                                getCiliumIngressPorts(deployment));
                        updateService(namespace, serviceSpec);
                    } catch (Exception e) {
                        var errorMessage = "Rolling update failed for deployment '%s'".formatted(id);
                        log.warn(errorMessage, e);
                        throw new DeploymentException(errorMessage, e);
                    }
                }
            });

            deployment.setStatus(DeploymentStatus.PENDING);
            return deployment;

        } catch (Exception e) {
            var errorMessage = "Rolling update failed for deployment '%s'".formatted(id);
            log.warn(errorMessage, e);
            throw new DeploymentException(errorMessage, e);
        }
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public boolean reconcile(String id, boolean ignorePendingOnServiceNotFound) {
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
        if (log.isTraceEnabled()) {
            log.trace("{}: started state sync for deployment '{}' with DB status {} and K8s status {}. Service: {}",
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

            // If K8s status is RUNNING, extract URL and schedule health check after commit
            if (status == DeploymentStatus.RUNNING) {
                // No need to look for deployments stuck in 'stopping' state because if stopping fails/hangs,
                //   state will be changed to 'stopped' and the cleaner will pick it up on the next scheduled run
                if (deployment.getStatus() == DeploymentStatus.STOPPING) {
                    log.debug("{}: deployment '{}' is active in Kubernetes but stopping was already initiated. Skipping", initiator, deploymentId);
                    return false;
                }

                log.info("{}: deployment '{}' appears RUNNING in K8s (service '{}'), scheduling readiness check after commit to set URL",
                        initiator, deploymentId, deployment.getServiceName());
                var serviceUrl = resolveServiceUrl(service, deployment);

                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        completeReconcileWithHealthCheck(deploymentId, serviceUrl, initiator);
                    }
                });
                return true;
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
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void stopOnServiceNotFound(String id) {
        if (log.isTraceEnabled()) {
            log.trace("stopOnServiceNotFound. Deployment ID: {}", id);
        }

        var deployment = getDeployment(id);

        var serviceName = deployment.getServiceName();
        if (StringUtils.isBlank(serviceName)) {
            log.warn("stopOnServiceNotFound. Deployment '{}' has no service name", id);
            markDeploymentAsStopped(deployment);
            return;
        }

        var service = getService(namespace, serviceName);
        if (service == null) {
            markDeploymentAsStopped(deployment);
        }
    }

    private void markDeploymentAsStopped(Deployment deployment) {
        log.info("Reconciliation: deployment '{}' not found in Kubernetes but marked as {} in DB. Updating to STOPPED",
                deployment.getId(), deployment.getStatus());
        deployment.setUrl(null);
        deployment.setStatus(DeploymentStatus.STOPPED);
        deploymentRepository.update(deployment.getId(), deployment);
    }

    @Override
    public List<SensitiveEnvVar> provisionSecrets(String deploymentId, EnvPartition envPartition) {
        var sensitiveEnvs = envPartition.sensitive();
        var sensitiveFileEnvs = envPartition.sensitiveFile();

        if (MapUtils.isEmpty(sensitiveEnvs) && MapUtils.isEmpty(sensitiveFileEnvs)) {
            return List.of();
        }

        var secretName = K8sNamingUtils.generateUniqueName(deploymentId, "envs");
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
    public void cleanupSecrets(String deploymentId, List<EnvVar> currentEnvs) {
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
    public List<PodInfo> getActiveInstances(String id) {
        return getInstances(id, pod -> isPodReady(pod.getStatus()));
    }

    @Override
    public List<PodInfo> getInstances(String id) {
        return getInstances(id, null);
    }

    protected List<PodInfo> getInstances(String id, Predicate<? super Pod> filter) {
        var serviceName = getServiceName(id);
        var podList = getServicePods(namespace, serviceName);
        var podsStream = podList.stream();
        if (filter != null) {
            podsStream = podsStream.filter(filter);
        }
        return podsStream.map(this::toPodInfo).toList();
    }

    private PodInfo toPodInfo(Pod pod) {
        var containerStatuses = pod.getStatus() != null
                ? pod.getStatus().getContainerStatuses()
                : null;

        var containerInfo = extractContainerInfo(containerStatuses);

        return new PodInfo(
                pod.getMetadata().getName(),
                Instant.parse(pod.getMetadata().getCreationTimestamp()),
                containerInfo.restartCount(),
                containerInfo.lastTerminationReason(),
                containerInfo.lastExitCode(),
                containerInfo.lastSignal(),
                containerInfo.lastFinishedAt()
        );
    }

    private ContainerInfo extractContainerInfo(List<ContainerStatus> containerStatuses) {
        if (CollectionUtils.isEmpty(containerStatuses)) {
            return new ContainerInfo(0, null, null, null, null);
        }

        int totalRestartCount = 0;
        ContainerStateTerminated mostRecentTermination = null;

        for (var containerStatus : containerStatuses) {
            totalRestartCount += containerStatus.getRestartCount() != null ? containerStatus.getRestartCount() : 0;

            // Check both 'state' (current) and 'lastState' (previous)
            // to find the most recent failure event.
            var currentTerminated = getTerminatedState(containerStatus.getState());
            var previousTerminated = getTerminatedState(containerStatus.getLastState());

            // Compare timestamps to find the true "latest" error
            mostRecentTermination = getLaterTermination(mostRecentTermination, currentTerminated);
            mostRecentTermination = getLaterTermination(mostRecentTermination, previousTerminated);
        }

        if (mostRecentTermination != null) {
            return new ContainerInfo(
                    totalRestartCount,
                    mostRecentTermination.getReason(),
                    mostRecentTermination.getExitCode(),
                    mostRecentTermination.getSignal(),
                    K8sParseUtils.parseInstant(mostRecentTermination.getFinishedAt())
            );
        }

        return new ContainerInfo(totalRestartCount, null, null, null, null);
    }

    private ContainerStateTerminated getTerminatedState(ContainerState state) {
        return (state != null) ? state.getTerminated() : null;
    }

    private ContainerStateTerminated getLaterTermination(ContainerStateTerminated currentBest,
                                                         ContainerStateTerminated candidate) {
        if (candidate == null) {
            return currentBest;
        }
        if (currentBest == null) {
            return candidate;
        }

        var t1 = K8sParseUtils.parseInstant(currentBest.getFinishedAt());
        var t2 = K8sParseUtils.parseInstant(candidate.getFinishedAt());
        if (t1 == null) {
            return candidate;
        }
        if (t2 == null) {
            return currentBest;
        }

        return t2.isAfter(t1) ? candidate : currentBest;
    }

    @Override
    public ContainerResource getContainerResourceForLogs(String id, String podName, boolean previous) {
        var serviceName = getServiceName(id);

        var pod = getServicePod(namespace, serviceName, podName);
        if (pod == null) {
            log.info("Pod '{}' not found for deployment '{}'. Service='{}', Namespace='{}'",
                    podName, id, serviceName, namespace);
            throw new EntityNotFoundException("Pod is not found for deployment '%s'".formatted(id));
        }

        var containerName = getContainerName(pod);
        if (containerName == null) {
            log.info("Container name could not be resolved for pod '{}', deployment '{}'. Service='{}', Namespace='{}'",
                    podName, id, serviceName, namespace);
            throw new EntityNotFoundException("Container is not found for deployment '%s'".formatted(id));
        }

        assertContainerLoggable(id, pod, podName, containerName, previous);
        return k8sClient.getPodResource(namespace, podName).inContainer(containerName);
    }

    private void assertContainerLoggable(String deploymentId, Pod pod, String podName, String containerName,
                                         boolean previous) {
        var containerStatus = findContainerStatus(deploymentId, pod, podName, containerName);

        if (previous) {
            assertPreviousLogsAvailable(deploymentId, containerStatus, podName, containerName);
        } else {
            assertContainerRunning(deploymentId, containerStatus, podName, containerName);
        }
    }

    private ContainerStatus findContainerStatus(String deploymentId, Pod pod, String podName, String containerName) {
        var podStatus = pod.getStatus();
        var phase = (podStatus != null) ? podStatus.getPhase() : null;
        var containerStatuses = (podStatus != null) ? podStatus.getContainerStatuses() : null;

        if (CollectionUtils.isEmpty(containerStatuses)) {
            log.info("Container '{}' in pod '{}' has no status yet. Deployment='{}', Pod phase={}",
                    containerName, podName, deploymentId, phase);
            throw new ValidationException("Container is not ready for log streaming for deployment '%s'".formatted(deploymentId));
        }

        return containerStatuses.stream()
                .filter(s -> containerName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> {
                    log.info("Container '{}' not found in pod '{}'. Deployment='{}', Pod phase={}",
                            containerName, podName, deploymentId, phase);
                    return new EntityNotFoundException("Container is not found for deployment '%s'".formatted(deploymentId));
                });
    }

    private void assertPreviousLogsAvailable(String deploymentId, ContainerStatus containerStatus, String podName,
                                             String containerName) {
        var lastState = containerStatus.getLastState();
        var currentState = containerStatus.getState();
        var hasTerminatedState = (lastState != null && lastState.getTerminated() != null)
                || (currentState != null && currentState.getTerminated() != null);

        if (!hasTerminatedState) {
            log.info("Container '{}' in pod '{}' has no terminated state. Deployment='{}', {}",
                    containerName, podName, deploymentId, describeContainerState(containerStatus));
            throw new ValidationException("Previous logs are not available for container in deployment '%s'".formatted(deploymentId));
        }
    }

    private void assertContainerRunning(String deploymentId, ContainerStatus containerStatus, String podName,
                                        String containerName) {
        var state = containerStatus.getState();
        if (state != null && state.getRunning() != null) {
            return;
        }

        log.info("Container '{}' in pod '{}' is not running. Deployment='{}', {}",
                containerName, podName, deploymentId, describeContainerState(containerStatus));
        throw new ValidationException("Container is not running for deployment '%s'".formatted(deploymentId));
    }

    private static String describeContainerState(ContainerStatus containerStatus) {
        var state = containerStatus.getState();
        if (state == null) {
            return "State=Unknown";
        }
        if (state.getWaiting() != null) {
            var w = state.getWaiting();
            return "State=Waiting Reason=%s Message=%s".formatted(w.getReason(), w.getMessage());
        }
        if (state.getTerminated() != null) {
            var t = state.getTerminated();
            return "State=Terminated Reason=%s ExitCode=%s Message=%s"
                    .formatted(t.getReason(), t.getExitCode(), t.getMessage());
        }
        return "State=Unknown";
    }

    @Override
    public NonNamespaceOperation<Event, EventList, Resource<Event>> getAllEventsBase() {
        return k8sClient.getAllEventsBase(namespace);
    }

    protected String getServiceName(String id) {
        return getDeploymentOptional(id)
                .map(Deployment::getServiceName)
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new EntityNotFoundException("Service name not found for deployment: " + id));
    }

    protected abstract Optional<D> getDeploymentOptional(String id);

    protected D getDeployment(String id) {
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

    protected abstract void saveDisposableResource(String id, String serviceName, String namespace);

    protected abstract List<DisposableResource> markServiceDisposableResourcesForCleanup(String id, String serviceName, String namespace);

    protected abstract DeploymentStatus mapStatus(S service);

    protected abstract String resolveServiceUrl(S service, D deployment);

    protected void performHealthChecks(D deployment, String serviceUrl) {
        // Default implementation does nothing
    }

    /**
     * Runs after transaction commit: loads deployment and performs health checks (no transaction),
     * then updates to RUNNING or CRASHED in a short write transaction (managed by repository class).
     * This avoids holding a DB connection for the duration of health checks.
     */
    private void completeReconcileWithHealthCheck(String deploymentId, String serviceUrl, String initiator) {
        D deployment = getDeployment(deploymentId);
        try {
            performHealthChecks(deployment, serviceUrl);
            deploymentRepository.conditionalUpdateInNewTransaction(
                    deploymentId,
                    Objects::nonNull,
                    d -> {
                        d.setUrl(serviceUrl);
                        d.setStatus(DeploymentStatus.RUNNING);
                    });
            log.info("{}: deployment '{}' marked RUNNING in DB after successful readiness check", initiator, deploymentId);
        } catch (Exception e) {
            deploymentRepository.updateStatusInNewTransaction(deploymentId, DeploymentStatus.CRASHED);
            log.warn("{}: error during readiness check for deployment '{}'", initiator, deploymentId, e);
        }
    }

    protected static <T extends EnvVar> List<T> filterEnvsByExactType(Deployment deployment, Class<T> type) {
        return deployment.getEnvs().stream()
                .filter(env -> type == env.getClass())
                .map(type::cast)
                .toList();
    }

    protected int resolveContainerPort(Supplier<Integer> portSupplier) {
        return containerPortResolver.resolveContainerPort(portSupplier, defaultContainerPort);
    }

    protected static <T> Supplier<EntityNotFoundException> notFound(String what, T id) {
        return () -> new EntityNotFoundException(String.format("%s not found: %s", what, id));
    }

    private List<DisposableResource> markDisposableResourcesForCleanup(String id, String namespace, String serviceName, String cnpName) {
        log.trace("markDisposableResourcesForCleanup. id='{}', namespace='{}', serviceName='{}', cnpName='{}'", id, namespace, serviceName, cnpName);
        var serviceDisposableResources = markServiceDisposableResourcesForCleanup(id, serviceName, namespace);
        log.debug("Service disposable resources marked for cleanup: {}", serviceDisposableResources);

        List<DisposableResource> disposableResources = new ArrayList<>(serviceDisposableResources);

        var cnpDisposableResources = disposableResourceManager.markCiliumNetworkPolicyResourceForCleanup(id, namespace, cnpName);
        log.debug("Cilium Network Policy disposable resources marked for cleanup: {}", cnpDisposableResources);

        disposableResources.addAll(cnpDisposableResources);

        return disposableResources;
    }

    private static boolean isUnrecoverableK8sError(Exception e) {
        if (e instanceof KubernetesClientException kce) {
            int code = kce.getCode();
            return code == HttpURLConnection.HTTP_NOT_FOUND
                    || code == HttpURLConnection.HTTP_UNAUTHORIZED
                    || code == HttpURLConnection.HTTP_FORBIDDEN;
        }
        return false;
    }

    /**
     * Renders the deployment's {@link CiliumNetworkPolicy}. Default produces the baseline policy;
     * subclasses override to add type-specific rules. {@code serviceSpec} is {@code null} on the
     * {@link #updateCiliumNetworkPolicy(String)} path — overrides that need topology state must
     * read it from the cluster themselves.
     */
    protected CiliumNetworkPolicy buildCiliumNetworkPolicy(D deployment, S serviceSpec, String serviceName,
                                                           List<String> allowedDomains, Set<Integer> ports) {
        return ciliumNetworkPolicyCreator.create(namespace, getServiceNameLabel(), serviceName, allowedDomains, ports);
    }

    private void createCiliumNetworkPolicy(D deployment, S serviceSpec, String groupId, List<String> allowedDomains,
                                           Set<Integer> ports, String name) {
        log.trace("createCiliumNetworkPolicy. groupId='{}', allowedDomains={}, ports={}, name={}",
                groupId, allowedDomains, ports, name);
        if (!ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()) {
            log.debug("Cilium Network Policies are not enabled. Skipping creation.");
            return;
        }

        var ciliumNetworkPolicy = buildCiliumNetworkPolicy(deployment, serviceSpec, name, allowedDomains, ports);

        disposableResourceManager.saveK8sResources(List.of(ciliumNetworkPolicy), K8sResourceKind.CILIUM_NETWORK_POLICY, groupId, namespace);
        log.trace("createCiliumNetworkPolicy. Saved Cilium Network Policy as disposable resource for groupId='{}' in namespace='{}'", groupId, namespace);

        k8sClient.createCiliumNetworkPolicy(namespace, ciliumNetworkPolicy);
        log.trace("createCiliumNetworkPolicy. Created Cilium Network Policy: {}", ciliumNetworkPolicy);

        disposableResourceManager.changeResourceLifecycleByGroupId(
                groupId,
                new K8sResourceReference(this.namespace, K8sResourceKind.CILIUM_NETWORK_POLICY, name),
                ResourceLifecycleState.STABLE
        );
    }

    @Override
    @Transactional
    public void updateCiliumNetworkPolicy(String id) {
        var deployment = getDeployment(id);
        try {
            updateCiliumNetworkPolicy(deployment, null, id, deployment.getServiceName(),
                    getEffectiveDeploymentAllowedDomains(deployment),
                    getCiliumIngressPorts(deployment));
        } catch (Exception e) {
            var errorMessage = "Cilium Network Policy update failed for deployment '%s'".formatted(id);
            log.warn(errorMessage, e);
            throw new DeploymentException(errorMessage, e);
        }
    }

    private void updateCiliumNetworkPolicy(D deployment, S serviceSpec, String groupId, String serviceName,
                                           List<String> allowedDomains, Set<Integer> ports) {
        log.trace("updateCiliumNetworkPolicy. groupId='{}', serviceName='{}', allowedDomains={}, ports={}",
                groupId, serviceName, allowedDomains, ports);
        if (!ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()) {
            log.debug("Cilium Network Policies are not enabled. Skipping update.");
            return;
        }
        var cnpName = resolveCiliumNetworkPolicyName(groupId, serviceName);
        log.trace("updateCiliumNetworkPolicy. cnpName='{}'", cnpName);

        var ciliumNetworkPolicy = buildCiliumNetworkPolicy(deployment, serviceSpec, serviceName, allowedDomains, ports);
        ciliumNetworkPolicy.getMetadata().setName(cnpName);

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

    /**
     * Resolves the actual CiliumNetworkPolicy resource name for a deployment.
     *
     * <p>This method exists for backward compatibility. Before migration V1.53, CiliumNetworkPolicy
     * names were derived via {@code K8sNamingUtils.generateName(serviceName)}, producing a
     * prefix-wrapped name (e.g. {@code dm-dm-abc123}). After the migration, new deployments use
     * {@code serviceName} directly as the CNP name (e.g. {@code dm-abc123}).
     *
     * <p>The actual CNP name is looked up from the disposable resource table, which stores the name
     * that was used at creation time. Falls back to {@code serviceName} if no disposable resource
     * entry exists (shouldn't happen in practice, but is safe).
     */
    private String resolveCiliumNetworkPolicyName(String deploymentId, String serviceName) {
        return disposableResourceManager.findCiliumNetworkPolicyName(deploymentId, namespace)
                .orElse(serviceName);
    }

    protected Set<Integer> getCiliumIngressPorts(D deployment) {
        Set<Integer> ports = new HashSet<>();
        Optional.ofNullable(deployment.getContainerPort()).ifPresent(ports::add);
        ports.add(defaultContainerPort);
        return ports;
    }

    protected List<String> getEffectiveDeploymentAllowedDomains(D deployment) {
        var domains = deployment.getAllowedDomains();
        return domains != null ? new ArrayList<>(domains) : new ArrayList<>();
    }

    private Map<String, String> transformEnvs(Map<String, EnvVarValue> envs) {
        return MapUtils.emptyIfNull(envs).entrySet().stream()
                .map(entry -> {
                    var value = entry.getValue() != null ? entry.getValue().getValue() : StringUtils.EMPTY;
                    return Map.entry(entry.getKey(), value);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private record ContainerInfo(
            int restartCount,
            String lastTerminationReason,
            Integer lastExitCode,
            Integer lastSignal,
            Instant lastFinishedAt
    ) {
    }

}
