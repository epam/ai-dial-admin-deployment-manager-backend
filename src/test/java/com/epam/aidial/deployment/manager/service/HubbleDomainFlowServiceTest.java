package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.HubbleRelayProperties;
import com.epam.aidial.deployment.manager.dao.entity.ImageBuildDomainEntryEntity;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentDomainEntryRepository;
import com.epam.aidial.deployment.manager.dao.repository.ImageBuildDomainEntryRepository;
import com.epam.aidial.deployment.manager.kubernetes.hubble.HubbleFlowObserver;
import com.epam.aidial.deployment.manager.model.CiliumVerdict;
import com.epam.aidial.deployment.manager.model.DomainEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HubbleDomainFlowServiceTest {

    private static final UUID IMAGE_DEF_ID = UUID.randomUUID();
    private static final String NAMESPACE = "build-ns";
    private static final String SELECTOR = "image-definition-id=test";

    @Mock
    private HubbleFlowObserver hubbleFlowObserver;
    @Mock
    private ImageBuildDomainEntryRepository imageBuildDomainEntryRepository;
    @Mock
    private DeploymentDomainEntryRepository deploymentDomainEntryRepository;
    @Mock
    private HubbleRelayProperties properties;

    private HubbleDomainFlowService service;

    @BeforeEach
    void setUp() {
        // lenient: not all tests exercise the full observation path
        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(properties.getConnectRetryCount()).thenReturn(3);
        lenient().when(properties.getConnectRetryIntervalMs()).thenReturn(0L);
        service = new HubbleDomainFlowService(
                hubbleFlowObserver,
                imageBuildDomainEntryRepository,
                deploymentDomainEntryRepository,
                properties,
                true
        );
    }

    // --- No-op guards ---

    @Test
    void startBuildObservation_isNoOp_whenHubbleDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        service.startBuildObservation(IMAGE_DEF_ID, NAMESPACE, SELECTOR);

        verifyNoInteractions(hubbleFlowObserver, imageBuildDomainEntryRepository);
    }

    @Test
    void startBuildObservation_isNoOp_whenCiliumDisabled() {
        service = new HubbleDomainFlowService(
                hubbleFlowObserver, imageBuildDomainEntryRepository, deploymentDomainEntryRepository,
                properties, false);

        service.startBuildObservation(IMAGE_DEF_ID, NAMESPACE, SELECTOR);

        verifyNoInteractions(hubbleFlowObserver, imageBuildDomainEntryRepository);
    }

    // --- Lifecycle ---

    @Test
    void startBuildObservation_clearsEntriesAndStartsObserver() throws Exception {
        var observerStarted = new CountDownLatch(1);
        doAnswer(inv -> {
            observerStarted.countDown();
            Thread.sleep(Long.MAX_VALUE);
            return null;
        }).when(hubbleFlowObserver).observe(eq(NAMESPACE), eq(SELECTOR), any());

        service.startBuildObservation(IMAGE_DEF_ID, NAMESPACE, SELECTOR);
        assertThat(observerStarted.await(2, TimeUnit.SECONDS)).isTrue();

        verify(imageBuildDomainEntryRepository).deleteAllByImageDefinitionId(IMAGE_DEF_ID);
        verify(hubbleFlowObserver).observe(eq(NAMESPACE), eq(SELECTOR), any());

        service.stopBuildObservation(IMAGE_DEF_ID);
    }

    @Test
    void stopBuildObservation_interruptsRunningObserver() throws Exception {
        var observerStarted = new CountDownLatch(1);
        var observerInterrupted = new CountDownLatch(1);
        doAnswer(inv -> {
            observerStarted.countDown();
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                observerInterrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return null;
        }).when(hubbleFlowObserver).observe(any(), any(), any());

        service.startBuildObservation(IMAGE_DEF_ID, NAMESPACE, SELECTOR);
        assertThat(observerStarted.await(2, TimeUnit.SECONDS)).isTrue();

        service.stopBuildObservation(IMAGE_DEF_ID);

        assertThat(observerInterrupted.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void startBuildObservation_cancelsExistingObserver_onRebuild() throws Exception {
        var firstStarted = new CountDownLatch(1);
        var firstCancelled = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);

        doAnswer(inv -> {
            firstStarted.countDown();
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                firstCancelled.countDown();
                Thread.currentThread().interrupt();
            }
            return null;
        }).doAnswer(inv -> {
            secondStarted.countDown();
            Thread.sleep(Long.MAX_VALUE);
            return null;
        }).when(hubbleFlowObserver).observe(eq(NAMESPACE), eq(SELECTOR), any());

        service.startBuildObservation(IMAGE_DEF_ID, NAMESPACE, SELECTOR);
        assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();

        service.startBuildObservation(IMAGE_DEF_ID, NAMESPACE, SELECTOR);
        assertThat(firstCancelled.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(secondStarted.await(2, TimeUnit.SECONDS)).isTrue();

        verify(imageBuildDomainEntryRepository, times(2)).deleteAllByImageDefinitionId(IMAGE_DEF_ID);

        service.stopBuildObservation(IMAGE_DEF_ID);
    }

    // --- Retry exhaustion ---

    @Test
    void observeWithRetry_attemptsExactly_connectRetryCount_times() throws Exception {
        int maxAttempts = 3;
        when(properties.getConnectRetryCount()).thenReturn(maxAttempts);

        var allAttemptsExhausted = new CountDownLatch(1);
        var callCount = new AtomicInteger();
        doAnswer(inv -> {
            if (callCount.incrementAndGet() >= maxAttempts) {
                allAttemptsExhausted.countDown();
            }
            throw new RuntimeException("connection refused");
        }).when(hubbleFlowObserver).observe(any(), any(), any());

        service.startBuildObservation(IMAGE_DEF_ID, NAMESPACE, SELECTOR);
        assertThat(allAttemptsExhausted.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50); // allow task to finish logging and exit

        verify(hubbleFlowObserver, times(maxAttempts)).observe(any(), any(), any());
    }

    @Test
    void observeWithRetry_exitsAfterOneAttempt_onInterruptionSignal() throws Exception {
        var firstAttemptDone = new CountDownLatch(1);
        doAnswer(inv -> {
            firstAttemptDone.countDown();
            throw new RuntimeException(new InterruptedException("interrupted"));
        }).when(hubbleFlowObserver).observe(any(), any(), any());

        service.startBuildObservation(IMAGE_DEF_ID, NAMESPACE, SELECTOR);
        assertThat(firstAttemptDone.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50); // ensure no retries follow

        verify(hubbleFlowObserver, times(1)).observe(any(), any(), any());
    }

    // --- Dedup / entry flow ---

    @Test
    void startBuildObservation_forwardsDomainEntriesToRepository() throws Exception {
        var entryRecorded = new CountDownLatch(1);
        doAnswer(inv -> {
            Consumer<DomainEntry> callback = inv.getArgument(2);
            callback.accept(new DomainEntry("auth.docker.io", CiliumVerdict.ALLOWED, 1000L));
            entryRecorded.countDown();
            return null; // stream ends normally after one entry
        }).when(hubbleFlowObserver).observe(any(), any(), any());

        service.startBuildObservation(IMAGE_DEF_ID, NAMESPACE, SELECTOR);
        assertThat(entryRecorded.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50);

        verify(imageBuildDomainEntryRepository)
                .saveIgnoreDuplicate(IMAGE_DEF_ID, "auth.docker.io", CiliumVerdict.ALLOWED, 1000L);
    }

    @Test
    void getDomainEntriesForBuild_mapsEntitiesToDomainModel() {
        var entity = new ImageBuildDomainEntryEntity();
        entity.setImageDefinitionId(IMAGE_DEF_ID);
        entity.setDomain("registry.hub.docker.com");
        entity.setVerdict(CiliumVerdict.BLOCKED);
        entity.setObservedAt(2000L);
        when(imageBuildDomainEntryRepository.findAllByImageDefinitionId(IMAGE_DEF_ID))
                .thenReturn(List.of(entity));

        var result = service.getDomainEntriesForBuild(IMAGE_DEF_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).domain()).isEqualTo("registry.hub.docker.com");
        assertThat(result.get(0).verdict()).isEqualTo(CiliumVerdict.BLOCKED);
        assertThat(result.get(0).observedAt()).isEqualTo(2000L);
    }
}
