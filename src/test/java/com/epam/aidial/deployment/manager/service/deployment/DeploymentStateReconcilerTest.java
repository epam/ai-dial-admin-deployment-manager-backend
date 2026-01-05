package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentStateReconcilerTest {

    private static final int MAX_BATCH_SIZE = 50;

    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private DeploymentManagerProvider deploymentManagerProvider;
    @SuppressWarnings("rawtypes")
    @Mock
    private DeploymentManager deploymentManager;

    @InjectMocks
    private DeploymentStateReconciler reconciler;

    @BeforeEach
    void setUp() {
        setField("reconcilePendingCutOffMinutes", 30);
    }

    private void setField(String fieldName, Object value) {
        try {
            Field field = DeploymentStateReconciler.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(reconciler, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    @Test
    void performFullReconciliation_whenMultiplePages_shouldProcessAllDeployments() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        Deployment dep1 = createDeployment(id1, DeploymentStatus.RUNNING);
        Deployment dep2 = createDeployment(id2, DeploymentStatus.PENDING);
        Deployment dep3 = createDeployment(id3, DeploymentStatus.PENDING);

        @SuppressWarnings("unchecked")
        Page<Deployment> page1 = mock(Page.class);
        when(page1.getContent()).thenReturn(List.of(dep1, dep2));
        when(page1.hasNext()).thenReturn(true);

        @SuppressWarnings("unchecked")
        Page<Deployment> page2 = mock(Page.class);
        when(page2.getContent()).thenReturn(List.of(dep3));
        when(page2.hasNext()).thenReturn(false);

        when(deploymentRepository.getAllActiveDeploymentsPaged(eq(MAX_BATCH_SIZE), eq(0))).thenReturn(page1);
        when(deploymentRepository.getAllActiveDeploymentsPaged(eq(MAX_BATCH_SIZE), eq(1))).thenReturn(page2);

        DeploymentManager<?> manager = (DeploymentManager<?>) deploymentManager;
        doReturn(manager).when(deploymentManagerProvider).provide(any(UUID.class));
        when(deploymentManager.reconcile(id1, true)).thenReturn(true);
        when(deploymentManager.reconcile(id2, true)).thenReturn(false);
        when(deploymentManager.reconcile(id3, true)).thenReturn(true);

        reconciler.performFullReconciliation();

        verify(deploymentRepository).getAllActiveDeploymentsPaged(eq(MAX_BATCH_SIZE), eq(0));
        verify(deploymentRepository).getAllActiveDeploymentsPaged(eq(MAX_BATCH_SIZE), eq(1));
        verify(deploymentManagerProvider, times(3)).provide(any(UUID.class));
        verify(deploymentManager).reconcile(id1, true);
        verify(deploymentManager).reconcile(id2, true);
        verify(deploymentManager).reconcile(id3, true);
    }

    @Test
    void performFullReconciliation_whenEmptyPage_shouldStopProcessing() {
        @SuppressWarnings("unchecked")
        Page<Deployment> page = mock(Page.class);
        when(page.getContent()).thenReturn(List.of());
        when(deploymentRepository.getAllActiveDeploymentsPaged(eq(MAX_BATCH_SIZE), eq(0))).thenReturn(page);

        reconciler.performFullReconciliation();

        verify(deploymentRepository).getAllActiveDeploymentsPaged(eq(MAX_BATCH_SIZE), eq(0));
        verify(deploymentManagerProvider, never()).provide(any(UUID.class));
    }

    @Test
    void performFullReconciliation_whenReconcileThrowsException_shouldContinueProcessing() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Deployment dep1 = createDeployment(id1, DeploymentStatus.RUNNING);
        Deployment dep2 = createDeployment(id2, DeploymentStatus.PENDING);

        @SuppressWarnings("unchecked")
        Page<Deployment> page = mock(Page.class);
        when(page.getContent()).thenReturn(List.of(dep1, dep2));
        when(page.hasNext()).thenReturn(false);
        when(deploymentRepository.getAllActiveDeploymentsPaged(eq(MAX_BATCH_SIZE), eq(0))).thenReturn(page);

        DeploymentManager<?> manager = (DeploymentManager<?>) deploymentManager;
        doReturn(manager).when(deploymentManagerProvider).provide(any(UUID.class));
        when(deploymentManager.reconcile(id1, true)).thenThrow(new RuntimeException("Reconciliation failed"));
        when(deploymentManager.reconcile(id2, true)).thenReturn(true);

        reconciler.performFullReconciliation();

        verify(deploymentManagerProvider, times(2)).provide(any(UUID.class));
        verify(deploymentManager).reconcile(id1, true);
        verify(deploymentManager).reconcile(id2, true);
    }

    @Test
    void reconcileDeploymentState_shouldCallReconcileAndReturnResult() {
        UUID id = UUID.randomUUID();
        Deployment deployment = createDeployment(id, DeploymentStatus.PENDING);

        DeploymentManager<?> manager = (DeploymentManager<?>) deploymentManager;
        doReturn(manager).when(deploymentManagerProvider).provide(id);
        when(deploymentManager.reconcile(id, true)).thenReturn(true);

        boolean updated = reconciler.reconcileDeploymentState(deployment, true);

        assertTrue(updated);
        verify(deploymentManagerProvider).provide(id);
        verify(deploymentManager).reconcile(id, true);
    }

    @Test
    void reconcileDeploymentState_whenReconcileReturnsFalse_shouldReturnFalse() {
        UUID id = UUID.randomUUID();
        Deployment deployment = createDeployment(id, DeploymentStatus.RUNNING);

        DeploymentManager<?> manager = (DeploymentManager<?>) deploymentManager;
        doReturn(manager).when(deploymentManagerProvider).provide(id);
        when(deploymentManager.reconcile(id, true)).thenReturn(false);

        boolean updated = reconciler.reconcileDeploymentState(deployment, true);

        assertFalse(updated);
        verify(deploymentManagerProvider).provide(id);
        verify(deploymentManager).reconcile(id, true);
    }

    @Test
    void reconcileDeploymentState_whenProviderThrowsException_shouldPropagateException() {
        UUID id = UUID.randomUUID();
        Deployment deployment = createDeployment(id, DeploymentStatus.PENDING);
        RuntimeException exception = new RuntimeException("Provider failed");

        when(deploymentManagerProvider.provide(id)).thenThrow(exception);

        assertThrows(RuntimeException.class, () -> reconciler.reconcileDeploymentState(deployment, true));

        verify(deploymentManagerProvider).provide(id);
        verify(deploymentManager, never()).reconcile(any(UUID.class), anyBoolean());
    }

    @Test
    void reconcileDeploymentState_whenReconcileThrowsException_shouldPropagateException() {
        UUID id = UUID.randomUUID();
        Deployment deployment = createDeployment(id, DeploymentStatus.PENDING);
        RuntimeException exception = new RuntimeException("Reconciliation failed");

        DeploymentManager<?> manager = (DeploymentManager<?>) deploymentManager;
        doReturn(manager).when(deploymentManagerProvider).provide(id);
        when(deploymentManager.reconcile(id, true)).thenThrow(exception);

        assertThrows(RuntimeException.class, () -> reconciler.reconcileDeploymentState(deployment, true));

        verify(deploymentManagerProvider).provide(id);
        verify(deploymentManager).reconcile(id, true);
    }

    @Test
    void checkPendingDeployments_whenMultiplePages_shouldProcessAllPendingDeployments() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        Deployment dep1 = createDeployment(id1, DeploymentStatus.PENDING);
        Deployment dep2 = createDeployment(id2, DeploymentStatus.PENDING);
        Deployment dep3 = createDeployment(id3, DeploymentStatus.PENDING);

        @SuppressWarnings("unchecked")
        Page<Deployment> page1 = mock(Page.class);
        when(page1.getContent()).thenReturn(List.of(dep1, dep2));
        when(page1.hasNext()).thenReturn(true);

        @SuppressWarnings("unchecked")
        Page<Deployment> page2 = mock(Page.class);
        when(page2.getContent()).thenReturn(List.of(dep3));
        when(page2.hasNext()).thenReturn(false);

        when(deploymentRepository.getPendingDeploymentsBeforePaged(any(), eq(MAX_BATCH_SIZE), eq(0))).thenReturn(page1);
        when(deploymentRepository.getPendingDeploymentsBeforePaged(any(), eq(MAX_BATCH_SIZE), eq(1))).thenReturn(page2);

        DeploymentManager<?> manager = (DeploymentManager<?>) deploymentManager;
        doReturn(manager).when(deploymentManagerProvider).provide(any(UUID.class));
        when(deploymentManager.reconcile(id1, false)).thenReturn(true);
        when(deploymentManager.reconcile(id2, false)).thenReturn(false);
        when(deploymentManager.reconcile(id3, false)).thenReturn(true);

        reconciler.checkPendingDeployments();

        verify(deploymentRepository).getPendingDeploymentsBeforePaged(any(), eq(MAX_BATCH_SIZE), eq(0));
        verify(deploymentRepository).getPendingDeploymentsBeforePaged(any(), eq(MAX_BATCH_SIZE), eq(1));
        verify(deploymentManagerProvider, times(3)).provide(any(UUID.class));
        verify(deploymentManager).reconcile(id1, false);
        verify(deploymentManager).reconcile(id2, false);
        verify(deploymentManager).reconcile(id3, false);
    }

    @Test
    void checkPendingDeployments_whenEmptyPage_shouldStopProcessing() {
        @SuppressWarnings("unchecked")
        Page<Deployment> page = mock(Page.class);
        when(page.getContent()).thenReturn(List.of());
        when(deploymentRepository.getPendingDeploymentsBeforePaged(any(), eq(MAX_BATCH_SIZE), eq(0))).thenReturn(page);

        reconciler.checkPendingDeployments();

        verify(deploymentRepository).getPendingDeploymentsBeforePaged(any(), eq(MAX_BATCH_SIZE), eq(0));
        verify(deploymentManagerProvider, never()).provide(any(UUID.class));
    }

    @Test
    void checkPendingDeployments_whenReconcileThrowsException_shouldContinueProcessing() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Deployment dep1 = createDeployment(id1, DeploymentStatus.PENDING);
        Deployment dep2 = createDeployment(id2, DeploymentStatus.PENDING);

        @SuppressWarnings("unchecked")
        Page<Deployment> page = mock(Page.class);
        when(page.getContent()).thenReturn(List.of(dep1, dep2));
        when(page.hasNext()).thenReturn(false);
        when(deploymentRepository.getPendingDeploymentsBeforePaged(any(), eq(MAX_BATCH_SIZE), eq(0))).thenReturn(page);

        DeploymentManager<?> manager = (DeploymentManager<?>) deploymentManager;
        doReturn(manager).when(deploymentManagerProvider).provide(any(UUID.class));
        when(deploymentManager.reconcile(id1, false)).thenThrow(new RuntimeException("Reconciliation failed"));
        when(deploymentManager.reconcile(id2, false)).thenReturn(true);

        reconciler.checkPendingDeployments();

        verify(deploymentManagerProvider, times(2)).provide(any(UUID.class));
        verify(deploymentManager).reconcile(id1, false);
        verify(deploymentManager).reconcile(id2, false);
    }

    private Deployment createDeployment(UUID id, DeploymentStatus status) {
        return InterceptorDeployment.builder()
                .id(id)
                .status(status)
                .updatedAt(Instant.now().minusSeconds(1200))
                .build();
    }
}