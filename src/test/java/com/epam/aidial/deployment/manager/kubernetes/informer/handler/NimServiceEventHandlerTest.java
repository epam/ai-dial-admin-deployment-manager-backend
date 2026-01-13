package com.epam.aidial.deployment.manager.kubernetes.informer.handler;

import com.epam.aidial.deployment.manager.kubernetes.ServiceState;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.service.deployment.NimDeploymentManager;
import com.nvidia.apps.v1alpha1.NIMService;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class NimServiceEventHandlerTest {

    private static final String TEST_NAMESPACE = "test-namespace";
    private static final String INITIATOR = "NimServiceWatcher";

    @Mock
    private NimDeploymentManager nimDeploymentManager;
    @Mock
    private ExecutorService executorService;

    @InjectMocks
    private NimServiceEventHandler eventHandler;

    private void setupExecutorServiceToRunSynchronously() {
        doAnswer(invocation -> {
            Runnable command = invocation.getArgument(0);
            command.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
    }

    @Test
    void testHandleAddedResourceUpdatesStatusToRunning() {
        setupExecutorServiceToRunSynchronously();
        UUID deploymentId = UUID.randomUUID();
        String serviceName = "mcp-" + deploymentId;
        NIMService service = mockNimService(serviceName, ServiceState.READY);

        eventHandler.onAdd(service);

        var configCaptor = ArgumentCaptor.forClass(ReconcileConfig.class);
        verify(nimDeploymentManager, times(1)).reconcile(configCaptor.capture());

        var reconcileConfig = configCaptor.getValue();
        assertEquals(deploymentId.toString(), reconcileConfig.getDeploymentId());
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

        eventHandler.onUpdate(service, service);

        verify(nimDeploymentManager, times(1)).reconcile(any(ReconcileConfig.class));
    }

    @Test
    void testHandleAddedResourceWithNotReadyStateUpdatesToPending() {
        setupExecutorServiceToRunSynchronously();
        UUID deploymentId = UUID.randomUUID();
        String serviceName = "mcp-" + deploymentId;
        NIMService service = mockNimService(serviceName, ServiceState.NOT_READY);

        eventHandler.onAdd(service);

        verify(nimDeploymentManager, times(1)).reconcile(any(ReconcileConfig.class));
    }

    @Test
    void testHandleDeletedResourceUpdatesStatusToNotDeployed() {
        setupExecutorServiceToRunSynchronously();
        UUID deploymentId = UUID.randomUUID();
        String serviceName = "mcp-" + deploymentId;
        NIMService service = mockNimService(serviceName, ServiceState.READY);

        eventHandler.onDelete(service, false);

        verify(nimDeploymentManager, times(1)).reconcile(any(ReconcileConfig.class));
    }

    @Test
    void testHandleResourceWithInvalidNameDoesNothing() {
        NIMService service = mockNimService("invalid-name", ServiceState.READY);

        eventHandler.onAdd(service);

        verify(nimDeploymentManager, never()).reconcile(any(ReconcileConfig.class));
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