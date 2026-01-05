package com.epam.aidial.deployment.manager.kubernetes.watcher;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages the lifecycle of all Kubernetes resource watchers.
 * Responsible for starting watchers during application initialization
 * and gracefully shutting them down when the application stops.
 */
@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class WatcherManager {
    
    private final List<WatcherRegistration> watcherRegistrations = new CopyOnWriteArrayList<>();
    private ExecutorService watcherExecutor;

    @PostConstruct
    public void init() {
        log.info("Initializing Kubernetes watcher manager");
        watcherExecutor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "k8s-watcher-thread");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void registerWatcher(WatcherRegistration registration) {
        log.info("Registering watcher for {}", registration.getResourceType());
        watcherRegistrations.add(registration);
        startWatcher(registration);
    }

    private void startWatcher(WatcherRegistration registration) {
        log.info("Starting watcher for {}", registration.getResourceType());
        watcherExecutor.execute(() -> {
            try {
                registration.start();
            } catch (Exception e) {
                log.error("Failed to start watcher for {}: {}", 
                        registration.getResourceType(), e.getMessage(), e);
            }
        });
    }

    private void stopWatcher(WatcherRegistration registration) {
        log.info("Stopping watcher for {}", registration.getResourceType());
        try {
            registration.stop();
        } catch (Exception e) {
            log.error("Error stopping watcher for {}: {}", 
                    registration.getResourceType(), e.getMessage(), e);
        }
    }

    public void restartWatcher(WatcherRegistration registration) {
        log.info("Restarting watcher for {}", registration.getResourceType());
        stopWatcher(registration);
        startWatcher(registration);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Kubernetes watcher manager");

        for (var registration : watcherRegistrations) {
            stopWatcher(registration);
        }

        watcherExecutor.shutdown();
        try {
            if (!watcherExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                watcherExecutor.shutdownNow();
                if (!watcherExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.error("Watcher executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            watcherExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("Kubernetes watcher manager shutdown complete");
    }
}