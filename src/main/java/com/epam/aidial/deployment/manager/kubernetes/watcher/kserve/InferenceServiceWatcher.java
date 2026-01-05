package com.epam.aidial.deployment.manager.kubernetes.watcher.kserve;

import com.epam.aidial.deployment.manager.configuration.KserveDeployProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.kubernetes.kserve.K8sKserveClient;
import com.epam.aidial.deployment.manager.kubernetes.watcher.AbstractServiceWatcher;
import com.epam.aidial.deployment.manager.kubernetes.watcher.WatchSupplier;
import com.epam.aidial.deployment.manager.kubernetes.watcher.WatcherManager;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.service.deployment.InferenceDeploymentManager;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.kserve.serving.v1beta1.InferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Watches Kserve Service resources in Kubernetes and updates the deployment status
 * in the database when changes are detected.
 */
@Slf4j
@Component
@LogExecution
public class InferenceServiceWatcher extends AbstractServiceWatcher<InferenceService> {

    private static final String RESOURCE_TYPE = "InferenceService";

    private final InferenceDeploymentManager inferenceDeploymentManager;

    public InferenceServiceWatcher(
            K8sKserveClient k8sKserveClient,
            DeploymentRepository deploymentRepository,
            WatcherManager watcherManager,
            InferenceDeploymentManager inferenceDeploymentManager,
            @Qualifier("k8s-service-readiness-checker") ExecutorService executorService,
            KserveDeployProperties kserveDeployProperties) {
        super(
                RESOURCE_TYPE,
                kserveDeployProperties.getNamespace(),
                deploymentRepository,
                watcherManager,
                executorService,
                createWatchSupplier(k8sKserveClient),
                K8sNamingUtils::extractId
        );
        this.inferenceDeploymentManager = inferenceDeploymentManager;
        watcherManager.registerWatcher(this);
    }

    private static WatchSupplier<InferenceService> createWatchSupplier(K8sKserveClient k8sKserveClient) {
        return k8sKserveClient::watchServices;
    }

    @Override
    protected void reconcile(UUID deploymentId, InferenceService resource, boolean isDeleted) {
        var reconcileConfig = ReconcileConfig.<InferenceService>builder()
                .deploymentId(deploymentId)
                .service(resource)
                .serviceIsMissing(isDeleted)
                .initiator("InferenceServiceWatcher")
                .ignorePendingOnServiceNotFound(true)
                .build();
        inferenceDeploymentManager.reconcile(reconcileConfig);
    }

}