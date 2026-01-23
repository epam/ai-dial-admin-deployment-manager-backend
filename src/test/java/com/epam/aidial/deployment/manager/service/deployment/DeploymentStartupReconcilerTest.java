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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentStartupReconcilerTest {

    private static final int BATCH_SIZE = 2;
    private static final int BOOTSTRAP_THREADS = 2;

    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private DeploymentManagerProvider deploymentManagerProvider;
    @SuppressWarnings("rawtypes")
    @Mock
    private DeploymentManager deploymentManager;
    @Mock
    private Page<Deployment> page;

    @InjectMocks
    private DeploymentStartupReconciler startupReconciler;

    @BeforeEach
    void setUp() {
        setField("bootstrapEnabled", true);
        setField("batchSize", BATCH_SIZE);
        setField("bootstrapThreads", BOOTSTRAP_THREADS);
    }

    private void setField(String fieldName, Object value) {
        try {
            Field field = DeploymentStartupReconciler.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(startupReconciler, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    @Test
    void init_whenBootstrapDisabled_shouldNotProcessDeployments() {
        setField("bootstrapEnabled", false);

        startupReconciler.init();

        verify(deploymentRepository, never()).getAllActiveDeploymentsPaged(anyInt(), anyInt());
        verify(deploymentManagerProvider, never()).provide(anyString());
    }

    @Test
    void init_whenBootstrapEnabled_shouldProcessAllDeployments() {
        String id1 = String.valueOf(UUID.randomUUID());
        String id2 = String.valueOf(UUID.randomUUID());
        Deployment dep1 = createDeployment(id1, DeploymentStatus.RUNNING);
        Deployment dep2 = createDeployment(id2, DeploymentStatus.PENDING);
        List<Deployment> deployments = List.of(dep1, dep2);

        when(page.getContent()).thenReturn(deployments);
        when(page.hasNext()).thenReturn(false);
        when(deploymentRepository.getAllActiveDeploymentsPaged(eq(BATCH_SIZE), eq(0))).thenReturn(page);
        DeploymentManager<?> manager = (DeploymentManager<?>) deploymentManager;
        doReturn(manager).when(deploymentManagerProvider).provide(anyString());

        startupReconciler.init();

        verify(deploymentRepository).getAllActiveDeploymentsPaged(eq(BATCH_SIZE), eq(0));
        verify(deploymentManagerProvider, times(2)).provide(anyString());
        verify(deploymentManager).stopOnServiceNotFound(id1);
        verify(deploymentManager).stopOnServiceNotFound(id2);
    }

    @Test
    void init_whenMultiplePages_shouldProcessAllBatches() {
        String id1 = String.valueOf(UUID.randomUUID());
        String id2 = String.valueOf(UUID.randomUUID());
        String id3 = String.valueOf(UUID.randomUUID());
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

        when(deploymentRepository.getAllActiveDeploymentsPaged(eq(BATCH_SIZE), eq(0))).thenReturn(page1);
        when(deploymentRepository.getAllActiveDeploymentsPaged(eq(BATCH_SIZE), eq(1))).thenReturn(page2);
        DeploymentManager<?> manager = (DeploymentManager<?>) deploymentManager;
        doReturn(manager).when(deploymentManagerProvider).provide(anyString());

        startupReconciler.init();

        verify(deploymentRepository).getAllActiveDeploymentsPaged(eq(BATCH_SIZE), eq(0));
        verify(deploymentRepository).getAllActiveDeploymentsPaged(eq(BATCH_SIZE), eq(1));
        verify(deploymentManagerProvider, times(3)).provide(anyString());
        verify(deploymentManager).stopOnServiceNotFound(id1);
        verify(deploymentManager).stopOnServiceNotFound(id2);
        verify(deploymentManager).stopOnServiceNotFound(id3);
    }

    @Test
    void init_whenEmptyPage_shouldStopProcessing() {
        when(page.getContent()).thenReturn(List.of());
        when(deploymentRepository.getAllActiveDeploymentsPaged(eq(BATCH_SIZE), eq(0))).thenReturn(page);

        startupReconciler.init();

        verify(deploymentRepository).getAllActiveDeploymentsPaged(eq(BATCH_SIZE), eq(0));
        verify(deploymentManagerProvider, never()).provide(anyString());
    }

    @Test
    void init_whenRepositoryThrowsException_shouldPropagateException() {
        when(deploymentRepository.getAllActiveDeploymentsPaged(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () -> startupReconciler.init());
    }

    private Deployment createDeployment(String id, DeploymentStatus status) {
        return InterceptorDeployment.builder()
                .id(id)
                .status(status)
                .build();
    }
}