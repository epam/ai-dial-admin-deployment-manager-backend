package com.epam.aidial.deployment.manager.cleanup.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentScheduledCleaner {

    private final ComponentCleaner cleaner;

    @Scheduled(cron = "${app.component-cleaner-cron}")
    @SchedulerLock(name = "cleanComponents", lockAtMostFor = "${app.component-cleaner-scheduler-lock-at-most-for:10m}")
    public void cleanDisposableResources() {
        log.info("Starting scheduled component clean-up...");
        cleaner.deleteAllPersisted();
        log.info("Finished scheduled component clean-up.");
    }

}
