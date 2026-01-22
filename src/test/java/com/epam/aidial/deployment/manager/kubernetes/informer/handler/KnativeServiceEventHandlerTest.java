package com.epam.aidial.deployment.manager.kubernetes.informer.handler;

import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.service.deployment.KnativeDeploymentManager;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class KnativeServiceEventHandlerTest {

    private static final String TEST_NAMESPACE = "test-namespace";
    private static final String INITIATOR = "KnativeServiceWatcher";

    @Mock
    private KnativeDeploymentManager knativeDeploymentManager;
    @Mock
    private ExecutorService executorService;

    @InjectMocks
    private KnativeServiceEventHandler eventHandler;

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
        Service service = mockService(serviceName);

        eventHandler.onAdd(service);

        var configCaptor = ArgumentCaptor.forClass(ReconcileConfig.class);
        verify(knativeDeploymentManager, times(1)).reconcile(configCaptor.capture());

        var reconcileConfig = configCaptor.getValue();
        assertEquals(deploymentId.toString(), reconcileConfig.getDeploymentId());
        assertEquals(service, reconcileConfig.getService());
        assertEquals(INITIATOR, reconcileConfig.getInitiator());
        assertFalse(reconcileConfig.isServiceIsMissing());
    }

    @Test
    void testHandleModifiedResourceUpdatesStatusToCrashed() {
        setupExecutorServiceToRunSynchronously();
        UUID deploymentId = UUID.randomUUID();
        String serviceName = "mcp-" + deploymentId;
        Service service = mockService(serviceName);

        eventHandler.onUpdate(service, service);

        verify(knativeDeploymentManager, times(1)).reconcile(any(ReconcileConfig.class));
    }

    @Test
    void testHandleAddedResourceWithUnknownStatusUpdatesToPending() {
        setupExecutorServiceToRunSynchronously();
        UUID deploymentId = UUID.randomUUID();
        String serviceName = "mcp-" + deploymentId;
        Service service = mockService(serviceName);

        eventHandler.onAdd(service);

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

        eventHandler.onDelete(service, false);

        verify(knativeDeploymentManager, times(1)).reconcile(any(ReconcileConfig.class));
    }

    @Test
    void testHandleResourceWithInvalidNameDoesNothing() {
        var service = mock(Service.class);
        var meta = new ObjectMeta();
        meta.setName("invalid-name");
        meta.setNamespace(TEST_NAMESPACE);

        when(service.getMetadata()).thenReturn(meta);

        eventHandler.onAdd(service);

        verify(knativeDeploymentManager, never()).reconcile(any(ReconcileConfig.class));
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