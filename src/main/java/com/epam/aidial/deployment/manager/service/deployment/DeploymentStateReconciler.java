package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * Service responsible for periodic full reconciliation of deployment states.
 * Acts as a safety net to ensure database state remains consistent with Kubernetes,
 * even if some events are missed by the watchers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentStateReconciler {

    private static final int MAX_RECONCILIATION_BATCH_SIZE = 50;

    @Value("${app.deployment-reconcile-pending-cut-off-mins}")
    private int reconcilePendingCutOffMinutes;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentManagerProvider deploymentManagerProvider;

    @Scheduled(cron = "${app.deployment-reconcile-cron}")
    @SchedulerLock(name = "fullDeploymentReconciliation", lockAtMostFor = "${app.deployment-reconcile-scheduler-lock-at-most-for:30m}")
    @Transactional
    public void performFullReconciliation() {
        log.info("Starting full deployment state reconciliation with batch size {}", MAX_RECONCILIATION_BATCH_SIZE);

        var result = processDeployments(
                pageNumber -> deploymentRepository.getAllActiveDeploymentsPaged(MAX_RECONCILIATION_BATCH_SIZE, pageNumber),
                deployment -> reconcileDeploymentState(deployment, true),
                "Processing batch of active deployments",
                "Failed to reconcile deployment state for deployment {}"
        );

        log.info("Full deployment state reconciliation completed. Processed {} deployments, updated {}",
                result.processed(), result.updated());
    }

    /**
     * Periodically reconciles deployments stuck in PENDING to determine their actual state
     * using the deployment manager, acting as a safety net when event updates are missed.
     */
    @Scheduled(cron = "${app.deployment-pending-check-cron}")
    @SchedulerLock(name = "pendingDeploymentCheck", lockAtMostFor = "${app.deployment-pending-check-scheduler-lock-at-most-for:5m}")
    @Transactional
    public void checkPendingDeployments() {
        log.info("Checking for stuck pending deployments");

        var cutoffTime = Instant.now().minus(reconcilePendingCutOffMinutes, ChronoUnit.MINUTES);

        var result = processDeployments(
                pageNumber -> deploymentRepository.getPendingDeploymentsBeforePaged(
                        cutoffTime, MAX_RECONCILIATION_BATCH_SIZE, pageNumber),
                deployment -> {
                    log.info("Deployment {} has been in PENDING state since {}. Re-running reconciliation to determine actual state",
                            deployment.getId(), deployment.getUpdatedAt());
                    return reconcileDeploymentState(deployment, false);
                },
                "Processing batch of stuck pending deployments",
                "Failed to update stuck pending deployment {}"
        );

        log.info("Pending deployment check completed. Processed {} deployments, updated {}",
                result.processed(), result.updated());
    }

    @Transactional
    public boolean reconcileDeploymentState(Deployment deployment, boolean ignorePendingOnServiceNotFound) {
        return deploymentManagerProvider.provide(deployment.getId())
                .reconcile(deployment.getId(), ignorePendingOnServiceNotFound);
    }

    private ProcessingResult processDeployments(IntFunction<Page<Deployment>> pageSupplier,
                                                Predicate<Deployment> reconciler,
                                                String batchLogContext,
                                                String errorLogTemplate) {
        int totalProcessed = 0;
        int updatedCount = 0;
        int pageNumber = 0;
        Page<Deployment> page;

        do {
            page = pageSupplier.apply(pageNumber);
            var deployments = page.getContent();

            if (deployments.isEmpty()) {
                break;
            }

            log.info("{} (size: {}, page: {})", batchLogContext, deployments.size(), pageNumber);

            for (var deployment : deployments) {
                try {
                    if (reconciler.test(deployment)) {
                        updatedCount++;
                    }
                } catch (Exception e) {
                    log.error(errorLogTemplate, deployment.getId(), e);
                }
            }

            totalProcessed += deployments.size();
            pageNumber++;

        } while (page.hasNext());

        return new ProcessingResult(totalProcessed, updatedCount);
    }

    private record ProcessingResult(int processed, int updated) {
    }
}