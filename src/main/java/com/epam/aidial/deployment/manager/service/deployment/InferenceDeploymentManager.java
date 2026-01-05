package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.configuration.KserveDeployProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.kserve.K8sKserveClient;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.service.manifest.InferenceManifestGenerator;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.service.pipeline.specification.CiliumNetworkPolicyCreator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.fabric8.kubernetes.api.model.Pod;
import io.kserve.serving.v1beta1.InferenceService;
import io.kserve.serving.v1beta1.InferenceServiceStatus;
import io.kserve.serving.v1beta1.inferenceservicestatus.ModelStatus;
import io.kserve.serving.v1beta1.inferenceservicestatus.modelstatus.States;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@LogExecution
public class InferenceDeploymentManager extends AbstractModelDeploymentManager<InferenceDeployment, InferenceService> {

    private static final int DEFAULT_KSERVE_SERVICE_PORT = 8080;

    private final InferenceManifestGenerator inferenceManifestGenerator;
    private final K8sKserveClient k8sKserveClient;
    private final boolean useClusterInternalUrl;

    public InferenceDeploymentManager(
            K8sClient k8sClient,
            DisposableResourceManager disposableResourceManager,
            ManifestGenerator manifestGenerator,
            InferenceManifestGenerator inferenceManifestGenerator,
            ContainerPortResolver containerPortResolver,
            CiliumNetworkPolicyCreator ciliumNetworkPolicyCreator,
            DeploymentRepository deploymentRepository,
            K8sKserveClient k8sKserveClient,
            KserveDeployProperties kserveDeployProperties
    ) {
        super(k8sClient, disposableResourceManager, manifestGenerator, deploymentRepository,
                containerPortResolver, ciliumNetworkPolicyCreator, kserveDeployProperties.getNamespace(),
                kserveDeployProperties.getStartupTimeout(), DEFAULT_KSERVE_SERVICE_PORT);
        this.inferenceManifestGenerator = inferenceManifestGenerator;
        this.k8sKserveClient = k8sKserveClient;
        this.useClusterInternalUrl = kserveDeployProperties.isUseClusterInternalUrl();
    }

    @Override
    protected String getServiceName(UUID id) {
        return K8sNamingUtils.generateName(id.toString());
    }

    @Override
    protected Optional<InferenceDeployment> getDeploymentOptional(UUID id) {
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
        var userDefinedSensitiveEnvs = filterEnvsByExactType(deployment, SensitiveEnvVar.class);
        var userDefinedSimpleEnvs = filterEnvsByExactType(deployment, SimpleEnvVar.class);

        var containerPort = resolveContainerPort(deployment::getContainerPort);

        return inferenceManifestGenerator.serviceConfig(
                deployment.getId().toString(),
                deployment.getModelFormat(),
                deployment.getSource().getStorageUri(),
                userDefinedSimpleEnvs,
                userDefinedSensitiveEnvs,
                deployment.getResources(),
                deployment.getMinScale(),
                deployment.getMaxScale(),
                deployment.getCommand(),
                deployment.getArgs(),
                containerPort);
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
    protected void saveDisposableResource(UUID id, String namespace) {
        disposableResourceManager.saveInferenceServiceResource(id, namespace);
    }

    @Override
    protected List<DisposableResource> markServiceDisposableResourcesForCleanup(UUID id, String namespace) {
        return disposableResourceManager.markInferenceServiceResourceForCleanup(id, namespace);
    }

    @Override
    protected DeploymentStatus mapStatus(InferenceService service) {
        log.trace("mapStatus. service: {}", service);
        var serviceName = service.getMetadata().getName();

        var status = service.getStatus();
        if (status == null) {
            log.debug("mapStatus. serviceName: {}. status is undefined", serviceName);
            return DeploymentStatus.PENDING;
        }

        var modelStatus = status.getModelStatus();
        if (modelStatus == null) {
            log.debug("mapStatus. serviceName: {}. modelStatus is undefined", serviceName);
            return DeploymentStatus.PENDING;
        }

        ModelStatus.TransitionStatus transitionStatus = modelStatus.getTransitionStatus();
        var states = modelStatus.getStates();

        if (states == null) {
            log.debug("mapStatus. serviceName: {}. modelStatus.states is undefined", serviceName);
            return DeploymentStatus.PENDING;
        }

        States.ActiveModelState activeModelState = states.getActiveModelState();

        log.debug("mapStatus. serviceName: {}. transitionStatus: {}. activeModelState: {}", serviceName,
                transitionStatus, activeModelState);

        // Check for CRASHED state first
        if (States.ActiveModelState.FAILEDTOLOAD.equals(activeModelState)
                || ModelStatus.TransitionStatus.BLOCKEDBYFAILEDLOAD.equals(transitionStatus)
                || ModelStatus.TransitionStatus.INVALIDSPEC.equals(transitionStatus)) {
            return DeploymentStatus.CRASHED;
        }

        // Check for RUNNING state
        if (States.ActiveModelState.LOADED.equals(activeModelState) && ModelStatus.TransitionStatus.UPTODATE.equals(transitionStatus)) {
            return DeploymentStatus.RUNNING;
        }

        // All other cases are PENDING
        return DeploymentStatus.PENDING;
    }

    @Override
    protected String resolveServiceUrl(InferenceService service, InferenceDeployment deployment) {
        log.trace("resolveServiceUrl. service: {}", service);
        var serviceName = service.getMetadata().getName();

        var status = service.getStatus();
        if (status == null) {
            log.debug("resolveServiceUrl. serviceName: {}. status is undefined", serviceName);
            return null;
        }

        return useClusterInternalUrl
                ? getClusterInternalUrl(status, serviceName)
                : getStatusUrl(status, serviceName);
    }

    private String getClusterInternalUrl(InferenceServiceStatus status, String serviceName) {
        var address = status.getAddress();
        if (address == null) {
            log.debug("resolveServiceUrl. serviceName: {}. address is undefined", serviceName);
            return null;
        }
        var url = address.getUrl();
        log.info("resolveServiceUrl. serviceName: {}. Using cluster internal URL: {}", serviceName, url);
        return url;
    }

    private String getStatusUrl(InferenceServiceStatus status, String serviceName) {
        var url = status.getUrl();
        log.info("resolveServiceUrl. serviceName: {}. Using external URL: {}", serviceName, url);
        return url;
    }
}
