package com.epam.aidial.deployment.manager.kubernetes.watcher.nim;

import com.epam.aidial.deployment.manager.configuration.NimDeployProperties;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.kubernetes.ServiceState;
import com.epam.aidial.deployment.manager.kubernetes.nim.K8sNimClient;
import com.epam.aidial.deployment.manager.kubernetes.watcher.WatcherManager;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.service.deployment.NimDeploymentManager;
import com.nvidia.apps.v1alpha1.NIMService;
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

import java.util.HashMap;
import java.util.Map;
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
class NimServiceWatcherTest {

    private static final String TEST_NAMESPACE = "test-namespace";
    private static final String INITIATOR = "NimServiceWatcher";

    @Mock
    private K8sNimClient k8sNimClient;
    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private WatcherManager watcherManager;
    @Mock
    private Watch watch;
    @Mock
    private NimDeploymentManager nimDeploymentManager;
    @Mock
    private ExecutorService executorService;

    private NimServiceWatcher watcher;

    @BeforeEach
    void setUp() {
        var nimProperties = new NimDeployProperties();
        nimProperties.setNamespace(TEST_NAMESPACE);
        watcher = new NimServiceWatcher(k8sNimClient, deploymentRepository, watcherManager, nimDeploymentManager, executorService, nimProperties);
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
        when(k8sNimClient.watchServices(eq(TEST_NAMESPACE), any())).thenReturn(watch);

        watcher.start();
        verify(k8sNimClient, times(1)).watchServices(eq(TEST_NAMESPACE), eq(watcher));
    }

    @Test
    void testStopWatcherClosesWatch() {
        when(k8sNimClient.watchServices(eq(TEST_NAMESPACE), any())).thenReturn(watch);

        watcher.start();
        watcher.stop();
        verify(watch, times(1)).close();
    }

    @Test
    void testHandleAddedResourceUpdatesStatusToRunning() {
        setupExecutorServiceToRunSynchronously();
        UUID deploymentId = UUID.randomUUID();
        String serviceName = "mcp-" + deploymentId;
        NIMService service = mockNimService(serviceName, ServiceState.READY);

        watcher.eventReceived(Action.ADDED, service);

        var configCaptor = ArgumentCaptor.forClass(ReconcileConfig.class);
        verify(nimDeploymentManager, times(1)).reconcile(configCaptor.capture());

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
        NIMService service = mockNimService(serviceName, ServiceState.FAILED);

        watcher.eventReceived(Action.MODIFIED, service);

        verify(nimDeploymentManager, times(1)).reconcile(any(ReconcileConfig.class));
    }

    @Test
    void testHandleAddedResourceWithNotReadyStateUpdatesToPending() {
        setupExecutorServiceToRunSynchronously();
        UUID deploymentId = UUID.randomUUID();
        String serviceName = "mcp-" + deploymentId;
        NIMService service = mockNimService(serviceName, ServiceState.NOT_READY);

        watcher.eventReceived(Action.ADDED, service);

        verify(nimDeploymentManager, times(1)).reconcile(any(ReconcileConfig.class));
    }

    @Test
    void testHandleDeletedResourceUpdatesStatusToNotDeployed() {
        setupExecutorServiceToRunSynchronously();
        UUID deploymentId = UUID.randomUUID();
        String serviceName = "mcp-" + deploymentId;
        NIMService service = mockNimService(serviceName, ServiceState.READY);

        watcher.eventReceived(Action.DELETED, service);

        verify(nimDeploymentManager, times(1)).reconcile(any(ReconcileConfig.class));
    }

    @Test
    void testHandleResourceWithInvalidNameDoesNothing() {
        NIMService service = mockNimService("invalid-name", ServiceState.READY);

        watcher.eventReceived(Action.ADDED, service);

        verify(deploymentRepository, never()).updateStatus(any(), any());
    }

    @Test
    void testHandleWatcherClosedTriggersRestart() {
        WatcherException exception = new WatcherException("test");
        watcher.onClose(exception);

        verify(watcherManager, times(1)).restartWatcher(watcher);
    }

    private NIMService mockNimService(String name, ServiceState state) {
        var service = mock(NIMService.class);
        var meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace(TEST_NAMESPACE);

        Map<String, Object> statusMap = new HashMap<>();
        if (state == ServiceState.READY) {
            statusMap.put("state", ServiceState.READY.getStateName());
        } else if (state == ServiceState.FAILED) {
            statusMap.put("state", ServiceState.FAILED.getStateName());
        } else if (state == ServiceState.NOT_READY) {
            statusMap.put("state", ServiceState.NOT_READY.getStateName());
        } else {
            statusMap.put("state", null);
        }
        Map<String, Object> additionalProperties = new HashMap<>();
        additionalProperties.put("status", statusMap);
        meta.setAdditionalProperties(additionalProperties);

        when(service.getMetadata()).thenReturn(meta);

        return service;
    }
}