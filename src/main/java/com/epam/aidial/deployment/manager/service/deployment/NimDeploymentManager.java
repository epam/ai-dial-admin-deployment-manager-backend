package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.configuration.NimDeployProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.ServiceState;
import com.epam.aidial.deployment.manager.kubernetes.nim.K8sNimClient;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.NgcRegistrySource;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.service.manifest.NimManifestGenerator;
import com.epam.aidial.deployment.manager.service.pipeline.specification.CiliumNetworkPolicyCreator;
import com.nvidia.apps.v1alpha1.NIMService;
import io.fabric8.kubernetes.api.model.Pod;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.nim.enabled", havingValue = "true")
@LogExecution
public class NimDeploymentManager extends AbstractModelDeploymentManager<NimDeployment, NIMService> {

    private static final String SERVICE_NAME_LABEL = "app.kubernetes.io/name";
    private static final int DEFAULT_NIM_SERVICE_PORT = 8000;

    private final NimManifestGenerator nimManifestGenerator;
    private final K8sNimClient k8sNimClient;
    private final NimDeployProperties nimDeployProperties;

    public NimDeploymentManager(
            K8sClient k8sClient,
            DisposableResourceManager disposableResourceManager,
            ManifestGenerator knativeManifestGenerator,
            NimManifestGenerator nimManifestGenerator,
            DeploymentRepository deploymentRepository,
            ContainerPortResolver containerPortResolver,
            CiliumNetworkPolicyCreator ciliumNetworkPolicyCreator,
            K8sNimClient k8sNimClient,
            NimDeployProperties nimDeployProperties
    ) {
        super(k8sClient, disposableResourceManager, knativeManifestGenerator, deploymentRepository,
                containerPortResolver, ciliumNetworkPolicyCreator, nimDeployProperties.getNamespace(),
                nimDeployProperties.getStartupTimeout(), DEFAULT_NIM_SERVICE_PORT);
        this.nimManifestGenerator = nimManifestGenerator;
        this.k8sNimClient = k8sNimClient;
        this.nimDeployProperties = nimDeployProperties;
    }

    @Override
    public List<Class<? extends Deployment>> getSupportedDeploymentClasses() {
        return List.of(NimDeployment.class);
    }

    @Override
    protected Optional<NimDeployment> getDeploymentOptional(String id) {
        return deploymentRepository.getById(id).map(deployment -> {
            if (deployment instanceof NimDeployment nimDeployment) {
                return nimDeployment;
            }
            throw new IllegalArgumentException("Deployment type should be 'NIM' for NIM service deployment. Deployment: '%s'"
                    .formatted(deployment.getId()));
        });
    }

    @Override
    protected Set<Integer> getCiliumIngressPorts(NimDeployment deployment) {
        var ports = super.getCiliumIngressPorts(deployment);
        Optional.ofNullable(deployment.getContainerGrpcPort()).ifPresent(ports::add);
        return ports;
    }

    @Override
    protected NIMService prepareServiceSpec(NimDeployment deployment) {
        if (!(deployment.getSource() instanceof NgcRegistrySource(String imageRef))) {
            throw new IllegalArgumentException("NIM deployment source should be NGC registry. Deployment: '%s'"
                    .formatted(deployment.getId()));
        }

        var userDefinedSensitiveEnvs = filterEnvsByExactType(deployment, SensitiveEnvVar.class);
        var userDefinedSimpleEnvs = filterEnvsByExactType(deployment, SimpleEnvVar.class);

        var containerPort = resolveContainerPort(deployment::getContainerPort);
        var containerGrpcPort = deployment.getContainerGrpcPort();
        var storageSize = deployment.getStorageSize();

        return nimManifestGenerator.serviceConfig(
                deployment.getId(),
                deployment.getServiceName(),
                userDefinedSimpleEnvs,
                userDefinedSensitiveEnvs,
                deployment.getResources(),
                imageRef,
                containerPort,
                containerGrpcPort,
                storageSize,
                deployment.getScaling(),
                deployment.getProbeProperties(),
                startupTimeoutSec,
                deployment.getCommand(),
                deployment.getArgs());
    }

    @Override
    protected void createService(String namespace, NIMService service) {
        k8sNimClient.createService(namespace, service);
    }

    @Override
    protected void updateService(String namespace, NIMService service) {
        k8sNimClient.updateService(namespace, service);
    }

    @Override
    protected void deleteService(String namespace, String name) {
        k8sNimClient.deleteService(namespace, name);
    }

    @Override
    protected NIMService getService(String namespace, String name) {
        return k8sNimClient.getService(namespace, name);
    }

    @Override
    protected List<Pod> getServicePods(String namespace, String name) {
        return k8sNimClient.getServicePods(namespace, name).getItems();
    }

    @Override
    protected Pod getServicePod(String namespace, String name, String podName) {
        return k8sNimClient.getServicePod(namespace, name, podName);
    }

    @Override
    protected void saveDisposableResource(String id, String serviceName, String namespace) {
        disposableResourceManager.saveNimServiceResource(id, serviceName, namespace);
    }

    @Override
    protected List<DisposableResource> markServiceDisposableResourcesForCleanup(String id, String serviceName, String namespace) {
        return disposableResourceManager.markNimServiceResourceForCleanup(id, serviceName, namespace);
    }

    @Override
    protected DeploymentStatus mapStatus(NIMService service) {
        if (service.getMetadata().getDeletionTimestamp() != null) {
            return DeploymentStatus.STOPPING;
        }

        var status = service.getStatus();
        if (status == null) {
            return DeploymentStatus.PENDING;
        }

        var serviceState = ServiceState.fromStateName(status.getState());

        if (serviceState == ServiceState.READY) {
            return DeploymentStatus.RUNNING;
        } else if (serviceState == ServiceState.FAILED) {
            return DeploymentStatus.CRASHED;
        } else {
            // CREATING, UPDATING, DELETING, UNKNOWN states
            return DeploymentStatus.PENDING;
        }
    }

    @Override
    protected String resolveServiceUrl(NIMService service, NimDeployment deployment) {
        log.trace("resolveServiceUrl. service: {}", service);
        var serviceName = service.getMetadata().getName();

        var status = service.getStatus();
        if (status == null) {
            log.debug("resolveServiceUrl. serviceName: '{}'. status is undefined", serviceName);
            return null;
        }

        var model = status.getModel();
        if (model == null) {
            log.debug("resolveServiceUrl. serviceName: '{}'. model is undefined", serviceName);
            return null;
        }

        String url;
        String defaultSchema;
        if (nimDeployProperties.isUseClusterInternalUrl()) {
            url = model.getClusterEndpoint();
            defaultSchema = "http";
            log.info("resolveServiceUrl. serviceName: '{}'. Using cluster internal URL: {}", serviceName, url);
        } else {
            url = model.getExternalEndpoint();
            defaultSchema = "https";
            log.info("resolveServiceUrl. serviceName: '{}'. Using external URL: {}", serviceName, url);
        }
        return prependSchema(url, defaultSchema);
    }

    @Override
    protected String getServiceNameLabel() {
        return SERVICE_NAME_LABEL;
    }

    private String prependSchema(String url, String defaultSchema) {
        if (StringUtils.isEmpty(url)) {
            return null;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        var schema = StringUtils.isNotEmpty(nimDeployProperties.getUrlSchema())
                ? nimDeployProperties.getUrlSchema().replace("://", "")
                : defaultSchema;
        return schema + "://" + url;
    }

}