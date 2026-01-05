package com.epam.aidial.deployment.manager.kubernetes.watcher.nim;

import com.epam.aidial.deployment.manager.configuration.NimDeployProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.kubernetes.nim.K8sNimClient;
import com.epam.aidial.deployment.manager.kubernetes.watcher.AbstractServiceWatcher;
import com.epam.aidial.deployment.manager.kubernetes.watcher.WatchSupplier;
import com.epam.aidial.deployment.manager.kubernetes.watcher.WatcherManager;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.service.deployment.NimDeploymentManager;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import com.nvidia.apps.v1alpha1.NIMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Watches NVIDIA NIM Service resources in Kubernetes and updates the deployment status
 * in the database when changes are detected.
 */
@Slf4j
@Component
@LogExecution
public class NimServiceWatcher extends AbstractServiceWatcher<NIMService> {

    private static final String RESOURCE_TYPE = "NIMService";

    private final NimDeploymentManager nimDeploymentManager;

    public NimServiceWatcher(
            K8sNimClient k8sNimClient,
            DeploymentRepository deploymentRepository,
            WatcherManager watcherManager,
            NimDeploymentManager nimDeploymentManager,
            @Qualifier("k8s-service-readiness-checker") ExecutorService executorService,
            NimDeployProperties nimDeployProperties) {
        super(
                RESOURCE_TYPE,
                nimDeployProperties.getNamespace(),
                deploymentRepository,
                watcherManager,
                executorService,
                createWatchSupplier(k8sNimClient),
                K8sNamingUtils::extractMcpPrefixedId
        );
        this.nimDeploymentManager = nimDeploymentManager;
        watcherManager.registerWatcher(this);
    }

    private static WatchSupplier<NIMService> createWatchSupplier(K8sNimClient k8sNimClient) {
        return k8sNimClient::watchServices;
    }

    @Override
    protected void reconcile(UUID deploymentId, NIMService resource, boolean isDeleted) {
        var reconcileConfig = ReconcileConfig.<NIMService>builder()
                .deploymentId(deploymentId)
                .service(resource)
                .serviceIsMissing(isDeleted)
                .initiator("NimServiceWatcher")
                .ignorePendingOnServiceNotFound(true)
                .build();
        nimDeploymentManager.reconcile(reconcileConfig);
    }

}