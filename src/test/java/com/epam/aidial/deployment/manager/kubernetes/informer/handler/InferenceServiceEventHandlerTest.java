package com.epam.aidial.deployment.manager.kubernetes.informer.handler;

import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.service.deployment.InferenceDeploymentManager;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.kserve.serving.v1beta1.InferenceService;
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
class InferenceServiceEventHandlerTest {

    private static final String TEST_NAMESPACE = "test-namespace";
    private static final String INITIATOR = "InferenceServiceWatcher";

    @Mock
    private InferenceDeploymentManager inferenceDeploymentManager;
    @Mock
    private ExecutorService executorService;

    @InjectMocks
    private InferenceServiceEventHandler eventHandler;

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
        String deploymentId = UUID.randomUUID().toString();
        String serviceName = "dm-" + deploymentId;
        InferenceService service = mockInferenceService(serviceName);

        eventHandler.onAdd(service);

        var configCaptor = ArgumentCaptor.forClass(ReconcileConfig.class);
        verify(inferenceDeploymentManager, times(1)).reconcile(configCaptor.capture());

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
        String deploymentId = UUID.randomUUID().toString();
        String serviceName = "dm-" + deploymentId;
        InferenceService service = mockInferenceService(serviceName);

        eventHandler.onUpdate(service, service);

        verify(inferenceDeploymentManager, times(1)).reconcile(any(ReconcileConfig.class));
    }

    @Test
    void testHandleAddedResourceWithNotReadyStateUpdatesToPending() {
        setupExecutorServiceToRunSynchronously();
        String deploymentId = UUID.randomUUID().toString();
        String serviceName = "dm-" + deploymentId;
        InferenceService service = mockInferenceService(serviceName);

        eventHandler.onAdd(service);

        verify(inferenceDeploymentManager, times(1)).reconcile(any(ReconcileConfig.class));
    }

    @Test
    void testHandleDeletedResourceUpdatesStatusToNotDeployed() {
        setupExecutorServiceToRunSynchronously();
        String deploymentId = UUID.randomUUID().toString();
        String serviceName = "dm-" + deploymentId;
        InferenceService service = mockInferenceService(serviceName);

        eventHandler.onDelete(service, false);

        verify(inferenceDeploymentManager, times(1)).reconcile(any(ReconcileConfig.class));
    }

    @Test
    void testHandleResourceWithInvalidNameDoesNothing() {
        InferenceService service = mockInferenceService("invalid-name");

        eventHandler.onAdd(service);

        verify(inferenceDeploymentManager, never()).reconcile(any(ReconcileConfig.class));
    }


    private InferenceService mockInferenceService(String name) {
        var service = mock(InferenceService.class);
        var meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace(TEST_NAMESPACE);
        when(service.getMetadata()).thenReturn(meta);
        return service;
    }
}

