package com.epam.aidial.deployment.manager.cleanup.resource;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class ContextRefreshedEventBasedDisposableResourceCleaner {

    private final DisposableResourceCleaner disposableResourceCleaner;

    @EventListener
    public void cleanTemporaryDisposableResourcesOnEvent(ContextRefreshedEvent event) {
        log.info("Triggered temporary disposable resources clean-up");
        disposableResourceCleaner.cleanAllTemporary();
    }
}
