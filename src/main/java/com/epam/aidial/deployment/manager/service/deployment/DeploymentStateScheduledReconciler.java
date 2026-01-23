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

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentStateScheduledReconciler {

    private static final int MAX_BATCH_SIZE = 50;

    @Value("${app.deployment-pending-check-cut-off-mins}")
    private int reconcilePendingCutOffMinutes;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentManagerProvider deploymentManagerProvider;

    /**
     * Periodically reconciles deployments stuck in PENDING to determine their actual state.
     * Acting as a safety net for cases when event updates are missed.
     */
    @Scheduled(cron = "${app.deployment-pending-check-cron}")
    @SchedulerLock(name = "pendingDeploymentCheck", lockAtMostFor = "${app.deployment-pending-check-scheduler-lock-at-most-for:5m}")
    @Transactional
    public void checkPendingDeployments() {
        log.info("Checking for stuck pending deployments");

        var cutoffTime = Instant.now().minus(reconcilePendingCutOffMinutes, ChronoUnit.MINUTES);

        var result = processDeployments(
                pageNumber -> deploymentRepository.getPendingDeploymentsBeforePaged(
                        cutoffTime, MAX_BATCH_SIZE, pageNumber),
                deployment -> {
                log.info("Deployment {} has been in PENDING state since {}. Re-running reconciliation to determine actual state",
                        deployment.getId(), deployment.getUpdatedAt());
                return deploymentManagerProvider.provide(deployment.getId())
                        .reconcile(deployment.getId(), false);
            });

        log.info("Pending deployment check completed. Processed {} deployments, updated {}",
                result.processed(), result.updated());
    }

    private ProcessingResult processDeployments(IntFunction<Page<Deployment>> pageSupplier, Predicate<Deployment> reconciler) {
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

            log.info("{} (size: {}, page: {})", "Processing batch of stuck pending deployments", deployments.size(), pageNumber);

            for (var deployment : deployments) {
                try {
                    if (reconciler.test(deployment)) {
                        updatedCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to update stuck pending deployment {}", deployment.getId(), e);
                }
            }

            totalProcessed += deployments.size();
            pageNumber++;

        } while (page.hasNext());

        return new ProcessingResult(totalProcessed, updatedCount);
    }

    private record ProcessingResult(int processed, int updated) { }
}