package com.epam.aidial.deployment.manager.cleanup.component;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class ScheduledComponentCleanup {

    private final ComponentCleanupService componentCleanupService;

    @Scheduled(cron = "${app.component-cleaner-cron}")
    @SchedulerLock(name = "cleanComponents", lockAtMostFor = "${app.component-cleaner-scheduler-lock-at-most-for:10m}")
    public void cleanComponents() {
        log.info("Starting scheduled component clean-up...");
        componentCleanupService.deleteAllPersisted();
        log.info("Finished scheduled component clean-up.");
    }

}
