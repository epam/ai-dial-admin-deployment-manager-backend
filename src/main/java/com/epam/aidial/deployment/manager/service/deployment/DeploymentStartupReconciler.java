package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Component responsible for marking active deployments as stopped in the database if their Kubernetes resources are missing at startup,
 * ensuring the application's internal state matches the actual state of Kubernetes resources.
 * The main components of state synchronization are event handlers:
 * {@link com.epam.aidial.deployment.manager.kubernetes.informer.handler.AbstractResourceEventHandler}.
 */
@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class DeploymentStartupReconciler {

    private static final int EXECUTOR_TERMINATION_TIMEOUT = 10;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentManagerProvider deploymentManagerProvider;

    @Value("${app.deployment-bootstrap-enabled}")
    private boolean bootstrapEnabled;

    @Value("${app.deployment-bootstrap-batch-size}")
    private int batchSize;

    @Value("${app.deployment-bootstrap-threads}")
    private int bootstrapThreads;

    @PostConstruct
    public void init() {
        if (!bootstrapEnabled) {
            log.info("Deployment bootstrap is disabled. Skipping initial state synchronization.");
            return;
        }

        log.info("Starting deployment bootstrap process with {} threads and batch size {}",
                bootstrapThreads, batchSize);

        ExecutorService executor = Executors.newFixedThreadPool(bootstrapThreads, r -> {
            Thread thread = new Thread(r, "deployment-bootstrap");
            thread.setDaemon(true);
            return thread;
        });

        try {
            performInitialStateSynchronization(executor);

            executor.shutdown();
            if (!executor.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT, TimeUnit.MINUTES)) {
                log.warn("Deployment bootstrap executor did not complete within {} minutes timeout. "
                        + "Forcing shutdown.", EXECUTOR_TERMINATION_TIMEOUT);
                executor.shutdownNow();
            }

            log.info("Deployment bootstrap process completed successfully");
        } catch (Exception e) {
            String message = "Failed to complete deployment bootstrap process: %s".formatted(e.getMessage());
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    private void performInitialStateSynchronization(ExecutorService executor) {
        log.info("Performing initial state synchronization for active deployments with batch size {}", batchSize);

        try {
            int pageNumber = 0;
            int totalProcessed = 0;
            Page<Deployment> page;

            do {
                page = deploymentRepository.getAllActiveDeploymentsPaged(batchSize, pageNumber);
                Collection<Deployment> deployments = page.getContent();

                if (deployments.isEmpty()) {
                    break;
                }

                log.info("Processing batch of {} deployments (page: {}, total processed: {})",
                        deployments.size(), pageNumber, totalProcessed);

                for (Deployment deployment : deployments) {
                    executor.execute(() -> stopDeploymentOnServiceNotFound(deployment));
                }

                totalProcessed += deployments.size();
                pageNumber++;

            } while (page.hasNext());

            log.info("Successfully submitted {} deployments for state synchronization", totalProcessed);

        } catch (Exception e) {
            log.error("Failed to perform initial state synchronization: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void stopDeploymentOnServiceNotFound(Deployment deployment) {
        String deploymentId = deployment.getId();

        try {
            deploymentManagerProvider.provide(deploymentId)
                    .stopOnServiceNotFound(deploymentId);
        } catch (Exception e) {
            log.error("Failed to synchronize state for deployment ID {} during bootstrap: {}",
                    deploymentId, e.getMessage(), e);
            throw e;
        }
    }
}