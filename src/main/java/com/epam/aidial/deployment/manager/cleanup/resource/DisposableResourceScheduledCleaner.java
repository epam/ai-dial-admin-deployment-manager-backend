package com.epam.aidial.deployment.manager.cleanup.resource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisposableResourceScheduledCleaner {

    private final DisposableResourceCleaner cleaner;

    @Scheduled(cron = "${app.resource-cleaner-cron}")
    @SchedulerLock(name = "cleanDisposableResources", lockAtMostFor = "${app.resource-cleaner-scheduler-lock-at-most-for:10m}")
    public void cleanDisposableResources() {
        log.info("Starting disposable resources scheduled clean-up...");
        cleaner.cleanAllCleanable();
        log.info("Finished disposable resources scheduled clean-up.");
    }

}
