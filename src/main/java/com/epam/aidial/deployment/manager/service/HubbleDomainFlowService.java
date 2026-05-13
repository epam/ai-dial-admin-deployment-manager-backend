package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.HubbleRelayProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentDomainEntryRepository;
import com.epam.aidial.deployment.manager.dao.repository.ImageBuildDomainEntryRepository;
import com.epam.aidial.deployment.manager.kubernetes.hubble.HubbleFlowObserver;
import com.epam.aidial.deployment.manager.model.DomainEntry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.channels.ClosedByInterruptException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Future;

/**
 * Manages the lifecycle of Hubble flow observation sessions for image builds and deployments.
 *
 * <p>Each observation session runs on a dedicated virtual thread from a per-service executor.
 * Using a <em>dedicated</em> virtual-thread executor (rather than the {@code sse-streamer} executor)
 * ensures that long-lived blocking gRPC streams do not starve SSE HTTP threads.
 *
 * <p>If Hubble Relay is disabled ({@code HUBBLE_RELAY_ENABLED=false}) or Cilium network policies
 * are disabled ({@code CILIUM_NETWORK_POLICIES_ENABLED=false}), all observation methods are no-ops.
 * Cilium must be enforcing DNS policies for Hubble to emit meaningful DNS flows; if Cilium is
 * disabled, Hubble observes no policy-affected flows and domain streaming produces no events.
 */
@Slf4j
@Service
@LogExecution
public class HubbleDomainFlowService {

    private final HubbleFlowObserver hubbleFlowObserver;
    private final ImageBuildDomainEntryRepository imageBuildDomainEntryRepository;
    private final DeploymentDomainEntryRepository deploymentDomainEntryRepository;
    private final HubbleRelayProperties properties;
    private final boolean ciliumNetworkPoliciesEnabled;

    // Dedicated virtual-thread executor — NOT the sse-streamer executor.
    // Hubble observation threads block on gRPC for the entire build/deployment duration;
    // sharing the SSE executor would starve HTTP response threads.
    private final ExecutorService hubbleExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final ConcurrentHashMap<String, Future<?>> activeBuildObservations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Future<?>> activeDeploymentObservations = new ConcurrentHashMap<>();

    public HubbleDomainFlowService(
            HubbleFlowObserver hubbleFlowObserver,
            ImageBuildDomainEntryRepository imageBuildDomainEntryRepository,
            DeploymentDomainEntryRepository deploymentDomainEntryRepository,
            HubbleRelayProperties properties,
            @Value("${app.cilium-network-policies-enabled}") boolean ciliumNetworkPoliciesEnabled) {
        this.hubbleFlowObserver = hubbleFlowObserver;
        this.imageBuildDomainEntryRepository = imageBuildDomainEntryRepository;
        this.deploymentDomainEntryRepository = deploymentDomainEntryRepository;
        this.properties = properties;
        this.ciliumNetworkPoliciesEnabled = ciliumNetworkPoliciesEnabled;
    }

    @PostConstruct
    void warnIfMisconfigured() {
        if (properties.isEnabled() && !ciliumNetworkPoliciesEnabled) {
            log.warn("Hubble Relay is enabled but CILIUM_NETWORK_POLICIES_ENABLED=false — "
                    + "Cilium is not enforcing DNS policies so no DNS flows will be visible; "
                    + "domain streaming will produce no events");
        }
    }

    /**
     * Starts a Hubble observation session for an image build pod.
     * No-op when Hubble Relay is disabled or Cilium is not enforcing policies.
     */
    public void startBuildObservation(UUID imageDefinitionId, String podNamespace, String podLabelSelector) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!ciliumNetworkPoliciesEnabled) {
            return;
        }

        imageBuildDomainEntryRepository.deleteAllByImageDefinitionId(imageDefinitionId);

        var key = imageDefinitionId.toString();
        var selfRef = new AtomicReference<Future<?>>();
        var future = hubbleExecutor.submit(() -> {
            try {
                observeWithRetry(
                        podNamespace, podLabelSelector,
                        entry -> imageBuildDomainEntryRepository.saveIgnoreDuplicate(
                                imageDefinitionId, entry.domain(), entry.verdict(), entry.observedAt()),
                        "build " + key);
            } finally {
                var self = selfRef.get();
                if (self != null) {
                    activeBuildObservations.remove(key, self);
                }
            }
        });
        selfRef.set(future);
        var prev = activeBuildObservations.put(key, future);
        if (prev != null) {
            prev.cancel(true);
        }
    }

    /**
     * Stops the Hubble observation session for an image build pod.
     */
    public void stopBuildObservation(UUID imageDefinitionId) {
        var future = activeBuildObservations.remove(imageDefinitionId.toString());
        if (future != null) {
            future.cancel(true);
        }
    }

    /**
     * Starts a Hubble observation session for a deployment pod.
     * No-op when Hubble Relay is disabled or Cilium is not enforcing policies.
     */
    public void startDeploymentObservation(String deploymentId, String podNamespace, String podLabelSelector) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!ciliumNetworkPoliciesEnabled) {
            return;
        }

        var selfRef = new AtomicReference<Future<?>>();
        var future = hubbleExecutor.submit(() -> {
            try {
                observeWithRetry(
                        podNamespace, podLabelSelector,
                        entry -> deploymentDomainEntryRepository.saveIgnoreDuplicate(
                                deploymentId, entry.domain(), entry.verdict(), entry.observedAt()),
                        "deployment " + deploymentId);
            } finally {
                var self = selfRef.get();
                if (self != null) {
                    activeDeploymentObservations.remove(deploymentId, self);
                }
            }
        });
        selfRef.set(future);
        var prev = activeDeploymentObservations.put(deploymentId, future);
        if (prev != null) {
            prev.cancel(true);
        }
    }

    /**
     * Stops the Hubble observation session for a deployment pod.
     */
    public void stopDeploymentObservation(String deploymentId) {
        var future = activeDeploymentObservations.remove(deploymentId);
        if (future != null) {
            future.cancel(true);
        }
    }

    /**
     * Returns all domain entries captured for a specific image build, converted to model objects.
     * Called by {@code ImageBuildController} via the service layer (never the DAO directly).
     */
    public List<DomainEntry> getDomainEntriesForBuild(UUID imageDefinitionId) {
        return imageBuildDomainEntryRepository.findAllByImageDefinitionId(imageDefinitionId)
                .stream()
                .map(e -> new DomainEntry(e.getDomain(), e.getVerdict(), e.getObservedAt()))
                .toList();
    }

    /**
     * Calls {@code HubbleFlowObserver.observe()} with retry logic.
     * Makes up to {@code connectRetryCount} total attempts with {@code connectRetryIntervalMs} delay
     * between them. If all attempts are exhausted, logs a warning and returns.
     * Observation failures MUST NOT propagate to the caller — builds and deployments must not fail
     * due to Hubble Relay unavailability (FR-011).
     */
    private void observeWithRetry(String podNamespace, String podLabelSelector,
                                   java.util.function.Consumer<com.epam.aidial.deployment.manager.model.DomainEntry> onEntry,
                                   String scopeLabel) {
        int maxAttempts = properties.getConnectRetryCount();
        long retryIntervalMs = properties.getConnectRetryIntervalMs();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            try {
                hubbleFlowObserver.observe(podNamespace, podLabelSelector, onEntry);
                return; // stream ended normally
            } catch (Exception e) {
                if (isInterruptionSignal(e)) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (attempt < maxAttempts - 1) {
                    log.warn("Hubble Relay connection failed for {} (attempt {}/{}): {}. Retrying in {} ms...",
                            scopeLabel, attempt + 1, maxAttempts, e.getMessage(), retryIntervalMs);
                    try {
                        Thread.sleep(retryIntervalMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    log.warn("Hubble Relay connection failed for {} after {} attempts: {}. "
                                    + "Domain streaming disabled for this scope. "
                                    + "Build/deployment will continue without domain events.",
                            scopeLabel, maxAttempts, e.getMessage());
                }
            }
        }
    }

    // A RuntimeException wrapping an InterruptedException will not set the thread interrupt flag,
    // so checking the flag alone is insufficient — inspect the exception and its cause directly.
    private static boolean isInterruptionSignal(Throwable t) {
        return t instanceof InterruptedException
                || t instanceof ClosedByInterruptException
                || t.getCause() instanceof InterruptedException
                || t.getCause() instanceof ClosedByInterruptException;
    }
}
