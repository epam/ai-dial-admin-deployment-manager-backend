package com.epam.aidial.deployment.manager.kubernetes.informer.handler;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.service.deployment.NimDeploymentManager;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import com.nvidia.apps.v1alpha1.NIMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

/**
 * Watches NVIDIA NIM Service resources in Kubernetes and updates the deployment status
 * in the database when changes are detected.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.nim.enabled", havingValue = "true")
@LogExecution
public class NimServiceEventHandler extends AbstractResourceEventHandler<NIMService> {

    private static final String RESOURCE_TYPE = "NIMService";

    private final NimDeploymentManager nimDeploymentManager;

    public NimServiceEventHandler(
            NimDeploymentManager nimDeploymentManager,
            @Qualifier("k8s-service-readiness-checker") ExecutorService executorService
    ) {
        super(RESOURCE_TYPE, K8sNamingUtils::extractMcpPrefixedId, executorService);
        this.nimDeploymentManager = nimDeploymentManager;
    }

    @Override
    protected void reconcile(String deploymentId, NIMService resource, boolean isDeleted) {
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