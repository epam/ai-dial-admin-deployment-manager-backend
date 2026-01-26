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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentStateScheduledReconcilerTest {

    private static final int MAX_BATCH_SIZE = 50;

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private DeploymentManagerProvider deploymentManagerProvider;

    @SuppressWarnings("rawtypes")
    @Mock
    private DeploymentManager deploymentManager;

    @InjectMocks
    private DeploymentStateScheduledReconciler reconciler;

    @BeforeEach
    void setUp() {
        setField("staleThreshold", 30);
    }

    private void setField(String fieldName, Object value) {
        try {
            Field field = DeploymentStateScheduledReconciler.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(reconciler, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    @Test
    void checkPendingDeployments_whenMultiplePages_shouldProcessAllPendingDeployments() {
        var id1 = String.valueOf(UUID.randomUUID());
        var id2 = String.valueOf(UUID.randomUUID());
        var id3 = String.valueOf(UUID.randomUUID());
        Deployment dep1 = createDeployment(id1);
        Deployment dep2 = createDeployment(id2);
        Deployment dep3 = createDeployment(id3);

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
        doReturn(manager).when(deploymentManagerProvider).provide(anyString());
        when(deploymentManager.reconcile(id1, false)).thenReturn(true);
        when(deploymentManager.reconcile(id2, false)).thenReturn(false);
        when(deploymentManager.reconcile(id3, false)).thenReturn(true);

        reconciler.checkPendingDeployments();

        verify(deploymentRepository).getPendingDeploymentsBeforePaged(any(), eq(MAX_BATCH_SIZE), eq(0));
        verify(deploymentRepository).getPendingDeploymentsBeforePaged(any(), eq(MAX_BATCH_SIZE), eq(1));
        verify(deploymentManagerProvider, times(3)).provide(anyString());
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
        verify(deploymentManagerProvider, never()).provide(anyString());
    }

    @Test
    void checkPendingDeployments_whenReconcileThrowsException_shouldContinueProcessing() {
        var id1 = String.valueOf(UUID.randomUUID());
        var id2 = String.valueOf(UUID.randomUUID());
        Deployment dep1 = createDeployment(id1);
        Deployment dep2 = createDeployment(id2);

        @SuppressWarnings("unchecked")
        Page<Deployment> page = mock(Page.class);
        when(page.getContent()).thenReturn(List.of(dep1, dep2));
        when(page.hasNext()).thenReturn(false);
        when(deploymentRepository.getPendingDeploymentsBeforePaged(any(), eq(MAX_BATCH_SIZE), eq(0))).thenReturn(page);

        DeploymentManager<?> manager = (DeploymentManager<?>) deploymentManager;
        doReturn(manager).when(deploymentManagerProvider).provide(anyString());
        when(deploymentManager.reconcile(id1, false)).thenThrow(new RuntimeException("Reconciliation failed"));
        when(deploymentManager.reconcile(id2, false)).thenReturn(true);

        reconciler.checkPendingDeployments();

        verify(deploymentManagerProvider, times(2)).provide(anyString());
        verify(deploymentManager).reconcile(id1, false);
        verify(deploymentManager).reconcile(id2, false);
    }

    private Deployment createDeployment(String id) {
        return InterceptorDeployment.builder()
                .id(id)
                .status(DeploymentStatus.PENDING)
                .updatedAt(Instant.now().minusSeconds(1200))
                .build();
    }
}