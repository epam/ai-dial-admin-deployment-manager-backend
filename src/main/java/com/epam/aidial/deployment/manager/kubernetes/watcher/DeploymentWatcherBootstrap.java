package com.epam.aidial.deployment.manager.kubernetes.watcher;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentManagerProvider;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Component responsible for bootstrapping the deployment watchers.
 * Performs initial state synchronization on application startup.
 */
@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class DeploymentWatcherBootstrap {

    private static final int EXECUTOR_TERMINATION_TIMEOUT = 10;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentManagerProvider deploymentManagerProvider;

    @PersistenceContext
    private EntityManager entityManager;

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

        log.info("Starting deployment bootstrap process");

        ExecutorService executor = Executors.newFixedThreadPool(bootstrapThreads, r -> {
            Thread thread = new Thread(r, "deployment-bootstrap");
            thread.setDaemon(true);
            return thread;
        });

        try {
            performInitialStateSynchronization(executor);

            executor.shutdown();
            if (!executor.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT, TimeUnit.MINUTES)) {
                log.warn("Deployment bootstrap did not complete within the timeout period");
                executor.shutdownNow();
            }

            log.info("Deployment bootstrap process completed");
        } catch (Exception e) {
            String message = "Error during deployment bootstrap: %s".formatted(e.getMessage());
            log.error(message, e);
            throw new RuntimeException(message);
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

                log.info("Processing batch of {} deployments (page: {})", deployments.size(), pageNumber);

                for (Deployment deployment : deployments) {
                    executor.execute(() -> synchronizeDeploymentState(deployment));
                }

                totalProcessed += deployments.size();
                pageNumber++;

            } while (page.hasNext());

            log.info("Submitted {} deployments for state synchronization", totalProcessed);

        } catch (Exception e) {
            log.error("Error during initial state synchronization: {}", e.getMessage(), e);
            throw e;
        }
    }

    public void synchronizeDeploymentState(Deployment deployment) {
        UUID deploymentId = deployment.getId();

        try {
            log.debug("Synchronizing state for deployment {}", deploymentId);
            deploymentManagerProvider.provide(deploymentId)
                    .reconcile(deploymentId, true);
            log.debug("Successfully synchronized state for deployment {}", deploymentId);
        } catch (Exception e) {
            log.error("Error synchronizing deployment {} state during bootstrap: {}",
                    deploymentId, e.getMessage(), e);
            throw e;
        }
    }
}