package com.epam.aidial.deployment.manager.kubernetes.watcher.knative;

import com.epam.aidial.deployment.manager.configuration.KnativeDeployProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.kubernetes.knative.K8sKnativeClient;
import com.epam.aidial.deployment.manager.kubernetes.watcher.AbstractServiceWatcher;
import com.epam.aidial.deployment.manager.kubernetes.watcher.WatchSupplier;
import com.epam.aidial.deployment.manager.kubernetes.watcher.WatcherManager;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.service.deployment.KnativeDeploymentManager;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.fabric8.knative.serving.v1.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Watches Knative Service resources in Kubernetes and updates the deployment status
 * in the database when changes are detected.
 */
@Component
@LogExecution
public class KnativeServiceWatcher extends AbstractServiceWatcher<Service> {

    private static final String RESOURCE_TYPE = "KnativeService";

    private final KnativeDeploymentManager knativeDeploymentManager;

    public KnativeServiceWatcher(
            K8sKnativeClient k8sKnativeClient,
            DeploymentRepository deploymentRepository,
            WatcherManager watcherManager,
            KnativeDeploymentManager knativeDeploymentManager,
            @Qualifier("k8s-service-readiness-checker") ExecutorService executorService,
            KnativeDeployProperties knativeDeployProperties) {
        super(
                RESOURCE_TYPE,
                knativeDeployProperties.getNamespace(),
                deploymentRepository,
                watcherManager,
                executorService,
                createWatchSupplier(k8sKnativeClient),
                K8sNamingUtils::extractMcpPrefixedId
        );
        this.knativeDeploymentManager = knativeDeploymentManager;
        watcherManager.registerWatcher(this);
    }

    @Override
    protected void reconcile(UUID deploymentId, Service resource, boolean isDeleted) {
        var reconcileConfig = ReconcileConfig.<Service>builder()
                .deploymentId(deploymentId)
                .service(resource)
                .serviceIsMissing(isDeleted)
                .initiator("KnativeServiceWatcher")
                .ignorePendingOnServiceNotFound(true)
                .build();
        knativeDeploymentManager.reconcile(reconcileConfig);
    }

    private static WatchSupplier<Service> createWatchSupplier(K8sKnativeClient k8sKnativeClient) {
        return k8sKnativeClient::watchServices;
    }

}
