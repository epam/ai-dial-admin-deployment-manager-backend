package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.configuration.KserveDeployProperties;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.huggingface.properties.HuggingFaceProperties;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.kserve.K8sKserveClient;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.HuggingFaceSource;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.service.detection.InferenceTaskDetectionResult;
import com.epam.aidial.deployment.manager.service.detection.InferenceTaskDetector;
import com.epam.aidial.deployment.manager.service.manifest.InferenceManifestGenerator;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.service.pipeline.specification.CiliumNetworkPolicyCreator;
import io.cilium.v2.CiliumNetworkPolicy;
import io.fabric8.kubernetes.api.model.Pod;
import io.kserve.serving.v1beta1.InferenceService;
import io.kserve.serving.v1beta1.inferenceservicestatus.Components;
import io.kserve.serving.v1beta1.inferenceservicestatus.ModelStatus;
import io.kserve.serving.v1beta1.inferenceservicestatus.modelstatus.States;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.kserve.enabled", havingValue = "true")
@LogExecution
public class InferenceDeploymentManager extends AbstractModelDeploymentManager<InferenceDeployment, InferenceService> {

    private static final String SERVICE_NAME_LABEL = "serving.kserve.io/inferenceservice";
    private static final String COMPONENT_LABEL = "component";
    private static final String PROMETHEUS_PORT_ANNOTATION = "prometheus.kserve.io/port";
    private static final String PROMETHEUS_PATH_ANNOTATION = "prometheus.kserve.io/path";
    private static final int DEFAULT_KSERVE_SERVICE_PORT = 8080;

    private final InferenceManifestGenerator inferenceManifestGenerator;
    private final K8sKserveClient k8sKserveClient;
    private final InferenceTaskDetector inferenceTaskDetector;
    private final boolean useClusterInternalUrl;
    private final List<String> defaultAllowedDomains;

    public InferenceDeploymentManager(
            K8sClient k8sClient,
            DisposableResourceManager disposableResourceManager,
            ManifestGenerator manifestGenerator,
            InferenceManifestGenerator inferenceManifestGenerator,
            ContainerPortResolver containerPortResolver,
            CiliumNetworkPolicyCreator ciliumNetworkPolicyCreator,
            NodePoolProperties nodePoolProperties,
            DeploymentRepository deploymentRepository,
            K8sKserveClient k8sKserveClient,
            KserveDeployProperties kserveDeployProperties,
            HuggingFaceProperties huggingFaceProperties,
            InferenceTaskDetector inferenceTaskDetector
    ) {
        super(k8sClient, disposableResourceManager, manifestGenerator, deploymentRepository,
                containerPortResolver, ciliumNetworkPolicyCreator, nodePoolProperties, kserveDeployProperties.getNamespace(),
                kserveDeployProperties.getStartupTimeout(), DEFAULT_KSERVE_SERVICE_PORT);
        this.inferenceManifestGenerator = inferenceManifestGenerator;
        this.k8sKserveClient = k8sKserveClient;
        this.inferenceTaskDetector = inferenceTaskDetector;
        this.useClusterInternalUrl = kserveDeployProperties.isUseClusterInternalUrl();
        this.defaultAllowedDomains = huggingFaceProperties.getDefaultAllowedDomains();
    }

    @Override
    public List<Class<? extends Deployment>> getSupportedDeploymentClasses() {
        return List.of(InferenceDeployment.class);
    }

    @Override
    protected Optional<InferenceDeployment> getDeploymentOptional(String id) {
        return deploymentRepository.getById(id)
                .map(deployment -> {
                    if (deployment instanceof InferenceDeployment inferenceDeployment) {
                        return inferenceDeployment;
                    }
                    throw new IllegalArgumentException(
                            "Deployment type should be 'Inference' for Inference service deployment. Deployment: "
                                    + deployment.getId());
                });
    }

    @Override
    protected InferenceService prepareServiceSpec(InferenceDeployment deployment) {
        if (!(deployment.getSource() instanceof HuggingFaceSource huggingFaceSource)) {
            throw new IllegalArgumentException("Inference deployment source should be HuggingFace. Deployment: '%s'"
                    .formatted(deployment.getId()));
        }

        var userDefinedSensitiveEnvs = filterEnvsByExactType(deployment, SensitiveEnvVar.class);
        var userDefinedSimpleEnvs = filterEnvsByExactType(deployment, SimpleEnvVar.class);

        var containerPort = resolveContainerPort(deployment::getContainerPort);

        var poolPrimitives = resolvePoolPrimitives(deployment.getNodePoolId());

        InferenceTaskDetectionResult detection = inferenceTaskDetector.detect(huggingFaceSource);

        return inferenceManifestGenerator.serviceConfig(
                deployment.getId(),
                deployment.getServiceName(),
                deployment.getModelFormat(),
                huggingFaceSource.getStorageUri(),
                userDefinedSimpleEnvs,
                userDefinedSensitiveEnvs,
                deployment.getResources(),
                deployment.getScaling(),
                deployment.getCommand(),
                deployment.getArgs(),
                containerPort,
                deployment.getProbeProperties(),
                startupTimeoutSec,
                poolPrimitives,
                detection.task(),
                detection.id2Label());
    }

    @Override
    protected void createService(String namespace, InferenceService service) {
        k8sKserveClient.createService(namespace, service);
    }

    @Override
    protected void updateService(String namespace, InferenceService service) {
        k8sKserveClient.updateService(namespace, service);
    }

    @Override
    protected void deleteService(String namespace, String name) {
        k8sKserveClient.deleteService(namespace, name);
    }

    @Override
    protected InferenceService getService(String namespace, String name) {
        return k8sKserveClient.getService(namespace, name);
    }

    @Override
    protected List<Pod> getServicePods(String namespace, String name) {
        return k8sKserveClient.getServicePods(namespace, name).getItems();
    }

    @Override
    protected Pod getServicePod(String namespace, String name, String podName) {
        return k8sKserveClient.getServicePod(namespace, name, podName);
    }

    @Override
    protected void saveDisposableResource(String id, String serviceName, String namespace) {
        disposableResourceManager.saveInferenceServiceResource(id, serviceName, namespace);
    }

    @Override
    protected List<DisposableResource> markServiceDisposableResourcesForCleanup(String id, String serviceName, String namespace) {
        return disposableResourceManager.markInferenceServiceResourceForCleanup(id, serviceName, namespace);
    }

    @Override
    protected DeploymentStatus mapStatus(InferenceService service) {
        log.trace("mapStatus. service: {}", service);
        var serviceName = service.getMetadata().getName();

        if (service.getMetadata().getDeletionTimestamp() != null) {
            log.debug("mapStatus. serviceName: '{}'. status is stopping", serviceName);
            return DeploymentStatus.STOPPING;
        }

        var status = service.getStatus();
        if (status == null) {
            log.debug("mapStatus. serviceName: '{}'. status is undefined", serviceName);
            return DeploymentStatus.PENDING;
        }

        var modelStatus = status.getModelStatus();
        if (modelStatus == null) {
            log.debug("mapStatus. serviceName: '{}'. modelStatus is undefined", serviceName);
            return DeploymentStatus.PENDING;
        }

        ModelStatus.TransitionStatus transitionStatus = modelStatus.getTransitionStatus();
        var states = modelStatus.getStates();

        if (states == null) {
            log.debug("mapStatus. serviceName: '{}'. modelStatus.states is undefined", serviceName);
            return DeploymentStatus.PENDING;
        }

        States.ActiveModelState activeModelState = states.getActiveModelState();

        log.debug("mapStatus. serviceName: '{}'. transitionStatus: {}. activeModelState: {}", serviceName,
                transitionStatus, activeModelState);

        // Check for CRASHED state first
        if (States.ActiveModelState.FAILEDTOLOAD.equals(activeModelState)
                || ModelStatus.TransitionStatus.BLOCKEDBYFAILEDLOAD.equals(transitionStatus)
                || ModelStatus.TransitionStatus.INVALIDSPEC.equals(transitionStatus)) {
            return DeploymentStatus.CRASHED;
        }

        // RUNNING requires both: ActiveModelState=LOADED means a pod is serving, but during a
        // rolling update the OLD pod can still be LOADED while the new spec hasn't propagated.
        // TransitionStatus=UPTODATE confirms the operator's latest spec is the one actually live.
        // For chained deployments the transformer component must also be reachable.
        if (States.ActiveModelState.LOADED.equals(activeModelState) && ModelStatus.TransitionStatus.UPTODATE.equals(transitionStatus)) {
            if (isChainedDeployment(service) && !isTransformerReady(service)) {
                log.debug("mapStatus. serviceName: '{}'. predictor RUNNING but transformer not yet ready", serviceName);
                return DeploymentStatus.PENDING;
            }
            return DeploymentStatus.RUNNING;
        }

        // All other cases are PENDING
        return DeploymentStatus.PENDING;
    }

    private boolean isChainedDeployment(InferenceService service) {
        return service.getSpec() != null && service.getSpec().getTransformer() != null;
    }

    /**
     * Augments the baseline policy with chained-mode rules (spec 022 FR-001) when the
     * {@code InferenceService} carries a transformer. On the update path {@code serviceSpec}
     * is null and the live cluster resource is read; failing fast on absence avoids silently
     * stripping the augmentation (which would recreate the bug spec 022 fixes).
     */
    @Override
    protected CiliumNetworkPolicy buildCiliumNetworkPolicy(InferenceDeployment deployment,
                                                           InferenceService serviceSpec,
                                                           String serviceName,
                                                           List<String> allowedDomains,
                                                           Set<Integer> ports) {
        InferenceService source = serviceSpec != null ? serviceSpec : requireLiveService(serviceName);
        return ciliumNetworkPolicyCreator.create(
                namespace, getServiceNameLabel(), serviceName, allowedDomains, ports, isChainedDeployment(source));
    }

    private InferenceService requireLiveService(String serviceName) {
        InferenceService service = k8sKserveClient.getService(namespace, serviceName);
        if (service == null) {
            throw new IllegalStateException(
                    "InferenceService '%s' not found in namespace '%s'".formatted(serviceName, namespace));
        }
        return service;
    }

    /**
     * Best-effort transformer readiness check based on URL presence.
     *
     * <p>KServe does not surface a per-component equivalent of the predictor's
     * {@code ActiveModelState=LOADED && TransitionStatus=UPTODATE} pair for the transformer; URL
     * presence is the closest signal available in {@code InferenceServiceStatus.components}. As a
     * consequence, a transformer that previously had its URL set but then crashed will still
     * be reported as ready until KServe reconciles. If KServe later adds richer per-component
     * status fields, gate on those instead.
     */
    private boolean isTransformerReady(InferenceService service) {
        var status = service.getStatus();
        if (status == null || status.getComponents() == null) {
            return false;
        }
        var transformer = status.getComponents().get("transformer");
        if (transformer == null) {
            return false;
        }
        if (transformer.getUrl() != null) {
            return true;
        }
        var address = transformer.getAddress();
        return address != null && address.getUrl() != null;
    }

    @Override
    protected String resolveServiceUrl(InferenceService service, InferenceDeployment deployment) {
        log.trace("resolveServiceUrl. service: {}", service);
        var serviceName = service.getMetadata().getName();

        var status = service.getStatus();
        if (status == null) {
            log.debug("resolveServiceUrl. serviceName: '{}'. status is undefined", serviceName);
            return null;
        }

        var components = status.getComponents();
        if (components == null) {
            log.debug("resolveServiceUrl. serviceName: '{}'. components is undefined", serviceName);
            return null;
        }

        // Chained deployments expose the transformer as the public endpoint; predictor stays internal.
        var transformer = components.get("transformer");
        if (transformer != null) {
            log.debug("resolveServiceUrl. serviceName: '{}'. Using transformer component URL", serviceName);
            return useClusterInternalUrl
                    ? getClusterInternalUrl(transformer, serviceName)
                    : getStatusUrl(transformer, serviceName);
        }

        var predictor = components.get("predictor");
        if (predictor == null) {
            log.debug("resolveServiceUrl. serviceName: '{}'. predictor is undefined", serviceName);
            return null;
        }

        return useClusterInternalUrl
                ? getClusterInternalUrl(predictor, serviceName)
                : getStatusUrl(predictor, serviceName);
    }

    @Override
    protected String getServiceNameLabel() {
        return SERVICE_NAME_LABEL;
    }

    /** KServe pods carry a {@code component} label ({@code predictor}/{@code transformer}); other types don't. */
    @Override
    protected String resolveComponent(Pod pod) {
        var labels = pod.getMetadata().getLabels();
        return labels != null ? labels.get(COMPONENT_LABEL) : null;
    }

    /**
     * KServe stamps {@code prometheus.kserve.io/port}/{@code /path} on a pod only when it actually
     * exposes a Prometheus endpoint. The predictor carries them; a chained transformer whose metric
     * aggregation is disabled does not — so a {@code null} port is the authoritative signal that the
     * transformer has no scrape target, letting the collector skip it instead of scraping a doomed
     * default port (which times out while the pod starts and resets once it is running).
     */
    @Override
    protected Integer resolveMetricsPort(Pod pod) {
        var value = annotation(pod, PROMETHEUS_PORT_ANNOTATION);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Pod '{}' has a non-numeric {} annotation: '{}'; ignoring",
                    pod.getMetadata().getName(), PROMETHEUS_PORT_ANNOTATION, value);
            return null;
        }
    }

    @Override
    protected String resolveMetricsPath(Pod pod) {
        return annotation(pod, PROMETHEUS_PATH_ANNOTATION);
    }

    private static String annotation(Pod pod, String key) {
        var annotations = pod.getMetadata().getAnnotations();
        return annotations != null ? annotations.get(key) : null;
    }

    @Override
    protected List<String> getEffectiveDeploymentAllowedDomains(InferenceDeployment deployment) {
        List<String> allowedDomains = super.getEffectiveDeploymentAllowedDomains(deployment);

        if (!(deployment.getSource() instanceof HuggingFaceSource)
                || CollectionUtils.isEmpty(defaultAllowedDomains)) {
            return allowedDomains;
        }

        return Stream.concat(
                        allowedDomains.stream(),
                        defaultAllowedDomains.stream())
                .distinct()
                .toList();
    }

    private String getClusterInternalUrl(Components predictor, String serviceName) {
        var address = predictor.getAddress();
        if (address == null) {
            log.debug("resolveServiceUrl. serviceName: '{}'. address is undefined", serviceName);
            return null;
        }
        var url = address.getUrl();
        log.info("resolveServiceUrl. serviceName: '{}'. Using cluster internal URL: {}", serviceName, url);
        return url;
    }

    private String getStatusUrl(Components predictor, String serviceName) {
        var url = predictor.getUrl();
        log.info("resolveServiceUrl. serviceName: '{}'. Using cluster external URL: {}", serviceName, url);
        return url;
    }

}
