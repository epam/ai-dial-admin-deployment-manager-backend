package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.configuration.KnativeDeployProperties;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.KubernetesConditionConstants;
import com.epam.aidial.deployment.manager.kubernetes.knative.K8sKnativeClient;
import com.epam.aidial.deployment.manager.kubernetes.knative.KnativeAnnotations;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SensitiveFileEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.AdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.ApplicationDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.ImageReferenceSource;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InternalImageSource;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.deployment.healthcheck.HealthCheckProvider;
import com.epam.aidial.deployment.manager.service.manifest.KnativeManifestGenerator;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.service.pipeline.specification.CiliumNetworkPolicyCreator;
import com.epam.aidial.deployment.manager.utils.K8sParseUtils;
import io.fabric8.knative.pkg.apis.Condition;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.knative.enabled", havingValue = "true")
@LogExecution
public class KnativeDeploymentManager extends AbstractDeploymentManager<Deployment, Service> {

    private static final int DEFAULT_KNATIVE_SERVICE_PORT = 8080;

    private final KnativeManifestGenerator knativeManifestGenerator;
    private final ImageDefinitionService imageDefinitionService;
    private final K8sKnativeClient k8sKnativeClient;
    private final HealthCheckProvider healthCheckProvider;

    private final String serviceContainer;
    private final int readyGracePeriodSec;

    public KnativeDeploymentManager(
            K8sClient k8sClient,
            ManifestGenerator manifestGenerator,
            KnativeManifestGenerator knativeManifestGenerator,
            DeploymentRepository deploymentRepository,
            ImageDefinitionService imageDefinitionService,
            ContainerPortResolver containerPortResolver,
            DisposableResourceManager disposableResourceManager,
            CiliumNetworkPolicyCreator ciliumNetworkPolicyCreator,
            NodePoolProperties nodePoolProperties,
            HealthCheckProvider healthCheckProvider,
            K8sKnativeClient k8sKnativeClient,
            KnativeDeployProperties knativeDeployProperties,
            @Value("${app.knative-service-container-config.name}") String serviceContainer
    ) {
        super(k8sClient, disposableResourceManager, manifestGenerator, deploymentRepository,
                containerPortResolver, ciliumNetworkPolicyCreator, nodePoolProperties, knativeDeployProperties.getNamespace(),
                knativeDeployProperties.getStartupTimeout(), DEFAULT_KNATIVE_SERVICE_PORT);
        this.knativeManifestGenerator = knativeManifestGenerator;
        this.imageDefinitionService = imageDefinitionService;
        this.healthCheckProvider = healthCheckProvider;
        this.k8sKnativeClient = k8sKnativeClient;
        this.serviceContainer = serviceContainer;
        this.readyGracePeriodSec = knativeDeployProperties.getReadyGracePeriod();
    }

    @Override
    public List<Class<? extends Deployment>> getSupportedDeploymentClasses() {
        return List.of(McpDeployment.class, InterceptorDeployment.class, AdapterDeployment.class, ApplicationDeployment.class);
    }

    @Override
    protected Optional<Deployment> getDeploymentOptional(String id) {
        return deploymentRepository.getById(id).map(deployment -> {
            if (deployment instanceof McpDeployment
                    || deployment instanceof InterceptorDeployment
                    || deployment instanceof AdapterDeployment
                    || deployment instanceof ApplicationDeployment) {
                return deployment;
            }
            throw new IllegalArgumentException(
                    "Deployment type should be 'MCP', 'Interceptor', 'Adapter' or 'Application' for Knative service deployment. Deployment: "
                            + deployment.getId());
        });
    }

    @Override
    protected DeployContext<Service> prepareServiceSpec(Deployment deployment) {
        var imageName = resolveImageName(deployment);

        var userDefinedSensitiveEnvs = filterEnvsByExactType(deployment, SensitiveEnvVar.class);
        var userDefinedSensitiveFileEnvs = filterEnvsByExactType(deployment, SensitiveFileEnvVar.class);
        var userDefinedSimpleEnvs = filterEnvsByExactType(deployment, SimpleEnvVar.class);

        var containerPort = resolveContainerPort(deployment::getContainerPort);

        var poolPrimitives = resolvePoolPrimitives(deployment.getNodePoolId());

        var service = knativeManifestGenerator.serviceConfig(
                deployment.getId(),
                deployment.getServiceName(),
                userDefinedSimpleEnvs,
                userDefinedSensitiveEnvs,
                userDefinedSensitiveFileEnvs,
                imageName,
                deployment.getScaling(),
                deployment.getResources(),
                containerPort,
                deployment.getProbeProperties(),
                deployment.getCommand(),
                deployment.getArgs(),
                poolPrimitives);
        return DeployContext.unchained(service);
    }

    private String resolveImageName(Deployment deployment) {
        return switch (deployment.getSource()) {
            case ImageReferenceSource(String imageReference, var ignored) -> imageReference;
            case InternalImageSource internalSource ->
                    imageDefinitionService.getImageDefinition(internalSource.imageDefinitionId())
                            .orElseThrow(notFound("ImageDefinition", internalSource.imageDefinitionId()))
                            .getImageName();
            case null -> throw new IllegalArgumentException(
                    "Deployment '%s' does not define an image source".formatted(deployment.getId()));
            default -> throw new IllegalArgumentException(
                    "Unsupported source type for Knative deployment '%s': %s".formatted(
                            deployment.getId(), deployment.getSource().getClass().getName()));
        };
    }

    @Override
    protected void createService(String namespace, Service service) {
        k8sKnativeClient.createService(namespace, service);
    }

    @Override
    protected void updateService(String namespace, Service service) {
        k8sKnativeClient.updateService(namespace, service);
    }

    @Override
    protected void deleteService(String namespace, String name) {
        k8sKnativeClient.deleteServiceAndAllRunningPods(namespace, name);
    }

    @Override
    protected Service getService(String namespace, String name) {
        return k8sKnativeClient.getService(namespace, name);
    }

    @Override
    protected List<Pod> getServicePods(String namespace, String name) {
        return k8sKnativeClient.getServicePods(namespace, name).getItems();
    }

    @Override
    protected Pod getServicePod(String namespace, String name, String podName) {
        return k8sKnativeClient.getServicePod(namespace, name, podName);
    }

    @Override
    protected boolean isPodReady(PodStatus podStatus) {
        var containerStatus = podStatus.getContainerStatuses().stream()
                .filter(status -> serviceContainer.equals(status.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Container %s is missing in service pod".formatted(serviceContainer)));
        return containerStatus.getState().getWaiting() == null;
    }

    @Override
    protected String getContainerName(Pod pod) {
        return serviceContainer;
    }

    @Override
    protected String getServiceNameLabel() {
        return KnativeAnnotations.SERVICE;
    }

    @Override
    protected void saveDisposableResource(String id, String serviceName, String namespace) {
        disposableResourceManager.saveKnativeServiceResource(id, serviceName, namespace);
    }

    @Override
    protected List<DisposableResource> markServiceDisposableResourcesForCleanup(String id, String serviceName, String namespace) {
        return disposableResourceManager.markKnativeServiceResourceForCleanup(id, serviceName, namespace);
    }

    @Override
    protected DeploymentStatus mapStatus(Service service) {
        if (service.getMetadata().getDeletionTimestamp() != null) {
            return DeploymentStatus.STOPPING;
        }

        var status = service.getStatus();
        if (status == null || status.getConditions() == null) {
            return DeploymentStatus.PENDING;
        }

        var readyCondition = findReadyCondition(status.getConditions());
        if (readyCondition == null) {
            return DeploymentStatus.PENDING;
        }

        if (KubernetesConditionConstants.STATUS_TRUE.equals(readyCondition.getStatus())) {
            return DeploymentStatus.RUNNING;
        } else if (KubernetesConditionConstants.STATUS_FALSE.equals(readyCondition.getStatus())) {
            if (isWithinReadyGracePeriod(readyCondition)) {
                log.debug("Ready=False within grace period (lastTransitionTime: {}). Treating as PENDING",
                        readyCondition.getLastTransitionTime());
                return DeploymentStatus.PENDING;
            }
            return DeploymentStatus.CRASHED;
        } else {
            return DeploymentStatus.PENDING;
        }
    }

    @Override
    protected String resolveServiceUrl(Service service, Deployment deployment) {
        var name = service.getMetadata().getName();
        var status = service.getStatus();

        if (status == null || status.getConditions() == null) {
            log.debug("Service '{}' has no status or conditions available.", name);
            return null;
        }

        logConditions(name, status.getConditions());
        if (hasReadyCondition(status.getConditions())) {
            if (StringUtils.isBlank(status.getUrl())) {
                throw new IllegalStateException("Knative Service '%s' is ready, but its URL is empty."
                        .formatted(name));
            }
            return status.getUrl();
        }

        return null;
    }

    @Override
    protected void performHealthChecks(Deployment deployment, String serviceUrl) {
        log.info("Deployment '{}' container is ready at {}", deployment.getId(), serviceUrl);
        healthCheckProvider.waitReady(deployment.getId(), serviceUrl, Duration.ofSeconds(startupTimeoutSec));
    }

    private boolean hasReadyCondition(List<Condition> conditions) {
        var readyConditionOptional = conditions.stream()
                .filter(c -> KubernetesConditionConstants.CONDITION_READY.equals(c.getType()))
                .findFirst();

        if (readyConditionOptional.isEmpty()) {
            return false;
        }

        var readyCondition = readyConditionOptional.get();
        return KubernetesConditionConstants.STATUS_TRUE.equals(readyCondition.getStatus());
    }

    private boolean isWithinReadyGracePeriod(Condition readyCondition) {
        if (readyGracePeriodSec <= 0) {
            return false;
        }
        var transitionTime = K8sParseUtils.parseInstant(readyCondition.getLastTransitionTime());
        if (transitionTime == null) {
            return false;
        }
        var elapsed = Duration.between(transitionTime, Instant.now());
        return elapsed.getSeconds() < readyGracePeriodSec;
    }

    private Condition findReadyCondition(List<Condition> conditions) {
        for (var condition : conditions) {
            if (KubernetesConditionConstants.CONDITION_READY.equals(condition.getType())) {
                return condition;
            }
        }
        return null;
    }

    private void logConditions(String serviceName, List<Condition> conditions) {
        if (!log.isDebugEnabled()) {
            return;
        }
        for (var condition : conditions) {
            log.debug("Service: '{}', condition: {}, status: {}, reason: {}, message: {}",
                    serviceName, condition.getType(), condition.getStatus(), condition.getReason(),
                    condition.getMessage());
        }
    }
}