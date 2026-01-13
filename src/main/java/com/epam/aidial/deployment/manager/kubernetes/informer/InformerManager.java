package com.epam.aidial.deployment.manager.kubernetes.informer;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.informer.registration.InformerRegistration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages the lifecycle of all Kubernetes resource informers.
 * Responsible for starting informers during application initialization
 * and gracefully shutting them down when the application stops.
 */
@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class InformerManager {

    private final List<InformerRegistration> informerRegistrations;

    private ExecutorService informerExecutor;

    @PostConstruct
    public void init() {
        log.info("Initializing Kubernetes informer manager");
        informerExecutor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "k8s-informer-thread");
            thread.setDaemon(true);
            return thread;
        });

        log.info("Starting {} informer registration(s)", informerRegistrations.size());
        for (var registration : informerRegistrations) {
            startInformer(registration);
        }
        log.info("All informer registrations started");
    }

    private void startInformer(InformerRegistration registration) {
        log.info("Starting informer for {}", registration.getResourceType());
        informerExecutor.execute(() -> {
            try {
                registration.start();
            } catch (Exception e) {
                log.error("Failed to start informer for {}: {}", registration.getResourceType(), e.getMessage(), e);
                throw e;
            }
        });
    }

    private void stopInformer(InformerRegistration registration) {
        log.info("Stopping informer for {}", registration.getResourceType());
        try {
            registration.stop();
        } catch (Exception e) {
            log.error("Error stopping informer for {}: {}",
                    registration.getResourceType(), e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Kubernetes informer manager");

        for (var registration : informerRegistrations) {
            stopInformer(registration);
        }

        informerExecutor.shutdown();
        try {
            if (!informerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                informerExecutor.shutdownNow();
                if (!informerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.error("Informer executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            informerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("Kubernetes informer manager shutdown complete");
    }
}