package com.epam.aidial.deployment.manager.kubernetes.nim;

import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.nvidia.apps.v1alpha1.NIMService;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class K8sNimClientTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String SERVICE_NAME = "test-nim-service";
    private static final String POD_NAME = "test-nim-pod";
    private static final int TIMEOUT_SEC = 60;

    @Mock
    private NimClient nimClient;
    @Mock
    private K8sClient k8sClient;
    @Mock
    private MixedOperation<NIMService, KubernetesResourceList<NIMService>, Resource<NIMService>> nimServiceOperation;
    @Mock
    private NonNamespaceOperation<NIMService, KubernetesResourceList<NIMService>, Resource<NIMService>> namespacedNimServiceOperation;
    @Mock
    private Resource<NIMService> nimServiceResource;

    private K8sNimClient k8sNimClient;

    @BeforeEach
    void setUp() {
        k8sNimClient = new K8sNimClient(nimClient, k8sClient);
    }

    @Test
    void getServicePods_shouldReturnPodsForService() {
        // Given
        PodList expectedPodList = new PodList();
        Map<String, String> serviceLabels = Map.of("app.kubernetes.io/name", SERVICE_NAME);

        when(k8sClient.getPods(NAMESPACE, serviceLabels)).thenReturn(expectedPodList);

        // When
        PodList result = k8sNimClient.getServicePods(NAMESPACE, SERVICE_NAME);

        // Then
        assertSame(expectedPodList, result);
        verify(k8sClient).getPods(NAMESPACE, serviceLabels);
    }

    @Test
    void getServicePod_shouldReturnPodForService() {
        // Given
        Pod expectedPod = new Pod();
        Map<String, String> serviceLabels = Map.of("app.kubernetes.io/name", SERVICE_NAME);

        when(k8sClient.getPod(NAMESPACE, POD_NAME, serviceLabels)).thenReturn(expectedPod);

        // When
        Pod result = k8sNimClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME);

        // Then
        assertSame(expectedPod, result);
        verify(k8sClient).getPod(NAMESPACE, POD_NAME, serviceLabels);
    }

    @Test
    void createNimService_shouldCreateService() {
        // Given
        NIMService service = new NIMService();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(SERVICE_NAME);
        service.setMetadata(metadata);

        when(nimClient.services()).thenReturn(nimServiceOperation);
        when(nimServiceOperation.inNamespace(NAMESPACE)).thenReturn(namespacedNimServiceOperation);
        when(namespacedNimServiceOperation.resource(service)).thenReturn(nimServiceResource);

        // When
        k8sNimClient.createService(NAMESPACE, service);

        // Then
        verify(nimClient).services();
        verify(nimServiceOperation).inNamespace(NAMESPACE);
        verify(namespacedNimServiceOperation).resource(service);
        verify(nimServiceResource).create();
    }

    @SuppressWarnings("unchecked")
    @Test
    void waitService_shouldWaitForServiceCondition() {
        // Given
        NIMService expectedService = new NIMService();
        Predicate<NIMService> predicate = service -> true;

        when(nimClient.services()).thenReturn(nimServiceOperation);
        when(nimServiceOperation.inNamespace(NAMESPACE)).thenReturn(namespacedNimServiceOperation);
        when(namespacedNimServiceOperation.withName(SERVICE_NAME)).thenReturn(nimServiceResource);
        when(nimServiceResource.waitUntilCondition(any(Predicate.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(expectedService);

        // When
        NIMService result = k8sNimClient.waitService(NAMESPACE, SERVICE_NAME, predicate, TIMEOUT_SEC);

        // Then
        assertSame(expectedService, result);
        verify(nimClient).services();
        verify(nimServiceOperation).inNamespace(NAMESPACE);
        verify(namespacedNimServiceOperation).withName(SERVICE_NAME);
        verify(nimServiceResource).waitUntilCondition(eq(predicate), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void deleteService_shouldReturnTrueWhenServiceDeleted() {
        // Given
        StatusDetails statusDetails = new StatusDetails();
        statusDetails.setName(SERVICE_NAME);
        statusDetails.setUid("test-uid");

        when(nimClient.services()).thenReturn(nimServiceOperation);
        when(nimServiceOperation.inNamespace(NAMESPACE)).thenReturn(namespacedNimServiceOperation);
        when(namespacedNimServiceOperation.withName(SERVICE_NAME)).thenReturn(nimServiceResource);
        when(nimServiceResource.delete()).thenReturn(List.of(statusDetails));

        // When
        k8sNimClient.deleteService(NAMESPACE, SERVICE_NAME);

        // Then
        verify(nimClient).services();
        verify(nimServiceOperation).inNamespace(NAMESPACE);
        verify(namespacedNimServiceOperation).withName(SERVICE_NAME);
        verify(nimServiceResource).delete();
    }

    @Test
    void deleteService_shouldReturnTrueWhenServiceNotFound() {
        // Given
        when(nimClient.services()).thenReturn(nimServiceOperation);
        when(nimServiceOperation.inNamespace(NAMESPACE)).thenReturn(namespacedNimServiceOperation);
        when(namespacedNimServiceOperation.withName(SERVICE_NAME)).thenReturn(nimServiceResource);
        when(nimServiceResource.delete()).thenReturn(List.of()); // Empty list means resource not found

        // When
        k8sNimClient.deleteService(NAMESPACE, SERVICE_NAME);

        // Then
        verify(nimClient).services();
        verify(nimServiceOperation).inNamespace(NAMESPACE);
        verify(namespacedNimServiceOperation).withName(SERVICE_NAME);
        verify(nimServiceResource).delete();
    }

    @Test
    void deleteService_shouldReturnFalseWhenExceptionOccurs() {
        // Given
        when(nimClient.services()).thenReturn(nimServiceOperation);
        when(nimServiceOperation.inNamespace(NAMESPACE)).thenReturn(namespacedNimServiceOperation);
        when(namespacedNimServiceOperation.withName(SERVICE_NAME)).thenReturn(nimServiceResource);
        when(nimServiceResource.delete()).thenThrow(new KubernetesClientException("Test exception", 500, null));

        // When

        assertThrows(KubernetesClientException.class, () -> k8sNimClient.deleteService(NAMESPACE, SERVICE_NAME));

        // Then
        verify(nimClient).services();
        verify(nimServiceOperation).inNamespace(NAMESPACE);
        verify(namespacedNimServiceOperation).withName(SERVICE_NAME);
        verify(nimServiceResource).delete();
    }

    @Test
    void deleteService_shouldReturnFalseWhenGenericExceptionOccurs() {
        // Given
        when(nimClient.services()).thenReturn(nimServiceOperation);
        when(nimServiceOperation.inNamespace(NAMESPACE)).thenReturn(namespacedNimServiceOperation);
        when(namespacedNimServiceOperation.withName(SERVICE_NAME)).thenReturn(nimServiceResource);
        when(nimServiceResource.delete()).thenThrow(new RuntimeException("Unexpected error"));

        // When
        assertThrows(RuntimeException.class, () -> k8sNimClient.deleteService(NAMESPACE, SERVICE_NAME));

        // Then
        verify(nimClient).services();
        verify(nimServiceOperation).inNamespace(NAMESPACE);
        verify(namespacedNimServiceOperation).withName(SERVICE_NAME);
        verify(nimServiceResource).delete();
    }
}