package com.epam.aidial.deployment.manager.kubernetes.watcher.knative;

import com.epam.aidial.deployment.manager.configuration.KnativeDeployProperties;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.kubernetes.knative.K8sKnativeClient;
import com.epam.aidial.deployment.manager.kubernetes.watcher.WatcherManager;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.service.deployment.KnativeDeploymentManager;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.WatcherException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class KnativeServiceWatcherTest {

    private static final String TEST_NAMESPACE = "test-namespace";
    private static final String INITIATOR = "KnativeServiceWatcher";

    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private WatcherManager watcherManager;
    @Mock
    private K8sKnativeClient knativeClient;
    @Mock
    private Watch watch;
    @Mock
    private KnativeDeploymentManager knativeDeploymentManager;
    @Mock
    private ExecutorService executorService;

    private KnativeServiceWatcher watcher;

    @BeforeEach
    void setUp() {
        var knativeProperties = new KnativeDeployProperties();
        knativeProperties.setNamespace(TEST_NAMESPACE);
        watcher = new KnativeServiceWatcher(knativeClient, deploymentRepository, watcherManager, knativeDeploymentManager, executorService, knativeProperties);
    }

    private void setupExecutorServiceToRunSynchronously() {
        doAnswer(invocation -> {
            Runnable command = invocation.getArgument(0);
            command.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
    }

    @Test
    void testWatcherRegistersOnConstruction() {
        verify(watcherManager, times(1)).registerWatcher(watcher);
    }

    @Test
    void testStartWatcherClosesOldWatchAndStartsNew() {
        when(knativeClient.watchServices(eq(TEST_NAMESPACE), any())).thenReturn(watch);

        watcher.start();
        verify(knativeClient, times(1)).watchServices(eq(TEST_NAMESPACE), eq(watcher));
    }

    @Test
    void testStopWatcherClosesWatch() {
        when(knativeClient.watchServices(eq(TEST_NAMESPACE), any())).thenReturn(watch);

        watcher.start();
        watcher.stop();
        verify(watch, times(1)).close();
    }

    @Test
    void testHandleAddedResourceUpdatesStatusToRunning() {
        setupExecutorServiceToRunSynchronously();
        UUID deploymentId = UUID.randomUUID();
        String serviceName = "mcp-" + deploymentId;
        Service service = mockService(serviceName);

        watcher.eventReceived(Action.ADDED, service);

        var configCaptor = ArgumentCaptor.forClass(ReconcileConfig.class);
        verify(knativeDeploymentManager, times(1)).reconcile(configCaptor.capture());

        var reconcileConfig = configCaptor.getValue();
        assertEquals(deploymentId, reconcileConfig.getDeploymentId());
        assertEquals(service, reconcileConfig.getService());
        assertEquals(INITIATOR, reconcileConfig.getInitiator());
        assertFalse(reconcileConfig.isServiceIsMissing());
        assertTrue(reconcileConfig.isIgnorePendingOnServiceNotFound());
    }

    @Test
    void testHandleModifiedResourceUpdatesStatusToCrashed() {
        setupExecutorServiceToRunSynchronously();
        UUID deploymentId = UUID.randomUUID();
        String serviceName = "mcp-" + deploymentId;
        Service service = mockService(serviceName);

        watcher.eventReceived(Action.MODIFIED, service);

        verify(knativeDeploymentManager, times(1)).reconcile(any(ReconcileConfig.class));
    }

    @Test
    void testHandleAddedResourceWithUnknownStatusUpdatesToPending() {
        setupExecutorServiceToRunSynchronously();
        UUID deploymentId = UUID.randomUUID();
        String serviceName = "mcp-" + deploymentId;
        Service service = mockService(serviceName);

        watcher.eventReceived(Action.ADDED, service);

        verify(knativeDeploymentManager, times(1)).reconcile(any(ReconcileConfig.class));
    }

    @Test
    void testHandleDeletedResourceUpdatesStatusToNotDeployed() {
        setupExecutorServiceToRunSynchronously();
        UUID deploymentId = UUID.randomUUID();

        var service = mock(Service.class);
        var meta = new ObjectMeta();
        meta.setName("mcp-" + deploymentId);
        meta.setNamespace(TEST_NAMESPACE);

        when(service.getMetadata()).thenReturn(meta);

        watcher.eventReceived(Action.DELETED, service);

        verify(knativeDeploymentManager, times(1)).reconcile(any(ReconcileConfig.class));
    }

    @Test
    void testHandleResourceWithInvalidNameDoesNothing() {
        var service = mock(Service.class);
        var meta = new ObjectMeta();
        meta.setName("invalid-name");
        meta.setNamespace(TEST_NAMESPACE);

        when(service.getMetadata()).thenReturn(meta);

        watcher.eventReceived(Action.ADDED, service);

        verify(deploymentRepository, never()).updateStatus(any(), any());
    }

    @Test
    void testHandleWatcherClosedTriggersRestart() {
        WatcherException exception = new WatcherException("test");
        watcher.onClose(exception);

        verify(watcherManager, times(1)).restartWatcher(watcher);
    }

    private Service mockService(String name) {
        var service = mock(Service.class);
        var meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace(TEST_NAMESPACE);
        when(service.getMetadata()).thenReturn(meta);
        return service;
    }
}