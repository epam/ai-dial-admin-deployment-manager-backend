package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.service.pipeline.specification.CiliumNetworkPolicyCreator;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;

/**
 * Base class for model serving deployments (e.g., KServe and NIM).
 * Holds common logic for pod readiness checks and container resolution.
 */
public abstract class AbstractModelDeploymentManager<D extends Deployment, S extends HasMetadata>
        extends AbstractDeploymentManager<D, S> {

    protected AbstractModelDeploymentManager(K8sClient k8sClient,
                                             DisposableResourceManager disposableResourceManager,
                                             ManifestGenerator manifestGenerator,
                                             DeploymentRepository deploymentRepository,
                                             ContainerPortResolver containerPortResolver,
                                             CiliumNetworkPolicyCreator ciliumNetworkPolicyCreator,
                                             String namespace,
                                             int startupTimeoutSec,
                                             Integer defaultContainerPort) {
        super(k8sClient, disposableResourceManager, manifestGenerator, deploymentRepository, containerPortResolver,
                ciliumNetworkPolicyCreator, namespace, startupTimeoutSec, defaultContainerPort);
    }

    @Override
    protected boolean isPodReady(PodStatus podStatus) {
        var containerStatus = podStatus.getContainerStatuses().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("A container is missing in the service pod"));
        return containerStatus.getState().getWaiting() == null;
    }

    @Override
    protected String getContainerName(Pod pod) {
        return pod.getSpec().getContainers().stream().findFirst()
                .map(Container::getName)
                .orElseThrow(() -> new IllegalStateException(
                        "Container not found for pod " + pod.getMetadata().getName()));
    }

}

