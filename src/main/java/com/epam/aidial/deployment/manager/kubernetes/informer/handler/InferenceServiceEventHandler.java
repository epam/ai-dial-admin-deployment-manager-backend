package com.epam.aidial.deployment.manager.kubernetes.informer.handler;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.service.deployment.InferenceDeploymentManager;
import io.kserve.serving.v1beta1.InferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

/**
 * Watches Kserve Service resources in Kubernetes and updates the deployment status
 * in the database when changes are detected.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.kserve.enabled", havingValue = "true")
@LogExecution
public class InferenceServiceEventHandler extends AbstractResourceEventHandler<InferenceService> {

    private static final String RESOURCE_TYPE = "InferenceService";

    private final InferenceDeploymentManager inferenceDeploymentManager;

    public InferenceServiceEventHandler(
            InferenceDeploymentManager inferenceDeploymentManager,
            DeploymentRepository deploymentRepository,
            @Qualifier("k8s-service-readiness-checker") ExecutorService executorService
    ) {
        super(RESOURCE_TYPE, deploymentRepository, executorService);
        this.inferenceDeploymentManager = inferenceDeploymentManager;
    }

    @Override
    protected void reconcile(String deploymentId, InferenceService resource, boolean isDeleted) {
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