package com.epam.aidial.deployment.manager.kubernetes.informer.handler;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.service.deployment.KnativeDeploymentManager;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.fabric8.knative.serving.v1.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

/**
 * Watches Knative Service resources in Kubernetes and updates the deployment status
 * in the database when changes are detected.
 */
@Component
@ConditionalOnProperty(name = "app.knative.enabled", havingValue = "true")
@LogExecution
public class KnativeServiceEventHandler extends AbstractResourceEventHandler<Service> {

    private static final String RESOURCE_TYPE = "KnativeService";

    private final KnativeDeploymentManager knativeDeploymentManager;

    public KnativeServiceEventHandler(
            KnativeDeploymentManager knativeDeploymentManager,
            @Qualifier("k8s-service-readiness-checker") ExecutorService executorService
    ) {
        super(RESOURCE_TYPE, K8sNamingUtils::extractMcpPrefixedId, executorService);
        this.knativeDeploymentManager = knativeDeploymentManager;
    }

    @Override
    protected void reconcile(String deploymentId, Service resource, boolean isDeleted) {
        var reconcileConfig = ReconcileConfig.<Service>builder()
                .deploymentId(deploymentId)
                .service(resource)
                .serviceIsMissing(isDeleted)
                .initiator("KnativeServiceWatcher")
                .ignorePendingOnServiceNotFound(true)
                .build();
        knativeDeploymentManager.reconcile(reconcileConfig);
    }

}
