package com.epam.aidial.deployment.manager.kubernetes.knative;

import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import io.fabric8.knative.client.KnativeClient;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.knative.serving.v1.ServiceList;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
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
class K8sKnativeClientTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String SERVICE_NAME = "test-knative-service";
    private static final String POD_NAME = "test-knative-pod";
    private static final int TIMEOUT_SEC = 60;

    @Mock
    private KnativeClient knativeClient;
    @Mock
    private K8sClient k8sClient;
    @Mock
    private MixedOperation<Service, ServiceList, Resource<Service>> knativeServiceOperation;
    @Mock
    private NonNamespaceOperation<Service, ServiceList, Resource<Service>> namespacedKnativeServiceOperation;
    @Mock
    private Resource<Service> knativeServiceResource;

    private K8sKnativeClient k8sKnativeClient;

    @BeforeEach
    void setUp() {
        k8sKnativeClient = new K8sKnativeClient(knativeClient, k8sClient);
    }

    @Test
    void getServicePods_shouldReturnPodsForService() {
        // Given
        PodList expectedPodList = new PodList();
        Map<String, String> serviceLabels = Map.of("serving.knative.dev/service", SERVICE_NAME);

        when(k8sClient.getPods(NAMESPACE, serviceLabels)).thenReturn(expectedPodList);

        // When
        PodList result = k8sKnativeClient.getServicePods(NAMESPACE, SERVICE_NAME);

        // Then
        assertSame(expectedPodList, result);
        verify(k8sClient).getPods(NAMESPACE, serviceLabels);
    }

    @Test
    void getServicePod_shouldReturnPodForService() {
        // Given
        Pod expectedPod = new Pod();
        Map<String, String> serviceLabels = Map.of("serving.knative.dev/service", SERVICE_NAME);

        when(k8sClient.getPod(NAMESPACE, POD_NAME, serviceLabels)).thenReturn(expectedPod);

        // When
        Pod result = k8sKnativeClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME);

        // Then
        assertSame(expectedPod, result);
        verify(k8sClient).getPod(NAMESPACE, POD_NAME, serviceLabels);
    }

    @Test
    void createKnativeService_shouldCreateService() {
        // Given
        Service service = new Service();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(SERVICE_NAME);
        service.setMetadata(metadata);

        when(knativeClient.services()).thenReturn(knativeServiceOperation);
        when(knativeServiceOperation.inNamespace(NAMESPACE)).thenReturn(namespacedKnativeServiceOperation);
        when(namespacedKnativeServiceOperation.resource(service)).thenReturn(knativeServiceResource);

        // When
        k8sKnativeClient.createService(NAMESPACE, service);

        // Then
        verify(knativeClient).services();
        verify(knativeServiceOperation).inNamespace(NAMESPACE);
        verify(namespacedKnativeServiceOperation).resource(service);
        verify(knativeServiceResource).create();
    }

    @SuppressWarnings("unchecked")
    @Test
    void waitService_shouldWaitForServiceCondition() {
        // Given
        Service expectedService = new Service();
        Predicate<Service> predicate = service -> true;

        when(knativeClient.services()).thenReturn(knativeServiceOperation);
        when(knativeServiceOperation.inNamespace(NAMESPACE)).thenReturn(namespacedKnativeServiceOperation);
        when(namespacedKnativeServiceOperation.withName(SERVICE_NAME)).thenReturn(knativeServiceResource);
        when(knativeServiceResource.waitUntilCondition(any(Predicate.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(expectedService);

        // When
        Service result = k8sKnativeClient.waitService(NAMESPACE, SERVICE_NAME, predicate, TIMEOUT_SEC);

        // Then
        assertSame(expectedService, result);
        verify(knativeClient).services();
        verify(knativeServiceOperation).inNamespace(NAMESPACE);
        verify(namespacedKnativeServiceOperation).withName(SERVICE_NAME);
        verify(knativeServiceResource).waitUntilCondition(eq(predicate), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void deleteService_shouldReturnTrueWhenServiceDeleted() {
        // Given
        StatusDetails statusDetails = new StatusDetails();
        statusDetails.setName(SERVICE_NAME);
        statusDetails.setUid("test-uid");

        when(knativeClient.services()).thenReturn(knativeServiceOperation);
        when(knativeServiceOperation.inNamespace(NAMESPACE)).thenReturn(namespacedKnativeServiceOperation);
        when(namespacedKnativeServiceOperation.withName(SERVICE_NAME)).thenReturn(knativeServiceResource);
        when(knativeServiceResource.withPropagationPolicy(any())).thenAnswer(invocation -> knativeServiceResource);
        when(knativeServiceResource.withGracePeriod(0L)).thenAnswer(invocation -> knativeServiceResource);
        when(knativeServiceResource.delete()).thenReturn(List.of(statusDetails));

        // When
        k8sKnativeClient.deleteService(NAMESPACE, SERVICE_NAME);

        // Then
        verify(knativeClient).services();
        verify(knativeServiceOperation).inNamespace(NAMESPACE);
        verify(namespacedKnativeServiceOperation).withName(SERVICE_NAME);
        verify(knativeServiceResource).withPropagationPolicy(eq(DeletionPropagation.FOREGROUND));
        verify(knativeServiceResource).withGracePeriod(0L);
        verify(knativeServiceResource).delete();
    }

    @Test
    void deleteService_shouldReturnTrueWhenServiceNotFound() {
        // Given
        when(knativeClient.services()).thenReturn(knativeServiceOperation);
        when(knativeServiceOperation.inNamespace(NAMESPACE)).thenReturn(namespacedKnativeServiceOperation);
        when(namespacedKnativeServiceOperation.withName(SERVICE_NAME)).thenReturn(knativeServiceResource);
        when(knativeServiceResource.withPropagationPolicy(any())).thenAnswer(invocation -> knativeServiceResource);
        when(knativeServiceResource.withGracePeriod(0L)).thenAnswer(invocation -> knativeServiceResource);
        when(knativeServiceResource.delete()).thenReturn(List.of()); // Empty list means resource not found

        // When
        k8sKnativeClient.deleteService(NAMESPACE, SERVICE_NAME);

        // Then
        verify(knativeClient).services();
        verify(knativeServiceOperation).inNamespace(NAMESPACE);
        verify(namespacedKnativeServiceOperation).withName(SERVICE_NAME);
        verify(knativeServiceResource).withPropagationPolicy(eq(DeletionPropagation.FOREGROUND));
        verify(knativeServiceResource).withGracePeriod(0L);
        verify(knativeServiceResource).delete();
    }

    @Test
    void deleteService_shouldTreatNotFoundAsDeleted() {
        // Given
        when(knativeClient.services()).thenReturn(knativeServiceOperation);
        when(knativeServiceOperation.inNamespace(NAMESPACE)).thenReturn(namespacedKnativeServiceOperation);
        when(namespacedKnativeServiceOperation.withName(SERVICE_NAME)).thenReturn(knativeServiceResource);
        when(knativeServiceResource.withPropagationPolicy(any())).thenAnswer(invocation -> knativeServiceResource);
        when(knativeServiceResource.withGracePeriod(0L)).thenAnswer(invocation -> knativeServiceResource);
        when(knativeServiceResource.delete()).thenThrow(new KubernetesClientException("Not Found", 404, null));

        // When — should not throw
        k8sKnativeClient.deleteService(NAMESPACE, SERVICE_NAME);

        // Then
        verify(knativeClient).services();
        verify(knativeServiceOperation).inNamespace(NAMESPACE);
        verify(namespacedKnativeServiceOperation).withName(SERVICE_NAME);
        verify(knativeServiceResource).withPropagationPolicy(eq(DeletionPropagation.FOREGROUND));
        verify(knativeServiceResource).withGracePeriod(0L);
        verify(knativeServiceResource).delete();
    }

    @Test
    void deleteService_shouldRethrowWhenKubernetesExceptionOccurs() {
        // Given
        when(knativeClient.services()).thenReturn(knativeServiceOperation);
        when(knativeServiceOperation.inNamespace(NAMESPACE)).thenReturn(namespacedKnativeServiceOperation);
        when(namespacedKnativeServiceOperation.withName(SERVICE_NAME)).thenReturn(knativeServiceResource);
        when(knativeServiceResource.withPropagationPolicy(any())).thenAnswer(invocation -> knativeServiceResource);
        when(knativeServiceResource.withGracePeriod(0L)).thenAnswer(invocation -> knativeServiceResource);
        when(knativeServiceResource.delete()).thenThrow(new KubernetesClientException("Test exception", 500, null));

        // When & Then
        assertThrows(KubernetesClientException.class, () -> k8sKnativeClient.deleteService(NAMESPACE, SERVICE_NAME));

        // Then
        verify(knativeClient).services();
        verify(knativeServiceOperation).inNamespace(NAMESPACE);
        verify(namespacedKnativeServiceOperation).withName(SERVICE_NAME);
        verify(knativeServiceResource).withPropagationPolicy(eq(DeletionPropagation.FOREGROUND));
        verify(knativeServiceResource).withGracePeriod(0L);
        verify(knativeServiceResource).delete();
    }

    @Test
    void deleteService_shouldRethrowWhenGenericExceptionOccurs() {
        // Given
        when(knativeClient.services()).thenReturn(knativeServiceOperation);
        when(knativeServiceOperation.inNamespace(NAMESPACE)).thenReturn(namespacedKnativeServiceOperation);
        when(namespacedKnativeServiceOperation.withName(SERVICE_NAME)).thenReturn(knativeServiceResource);
        when(knativeServiceResource.withPropagationPolicy(any())).thenAnswer(invocation -> knativeServiceResource);
        when(knativeServiceResource.withGracePeriod(0L)).thenAnswer(invocation -> knativeServiceResource);
        when(knativeServiceResource.delete()).thenThrow(new RuntimeException("Unexpected error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> k8sKnativeClient.deleteService(NAMESPACE, SERVICE_NAME));

        // Then
        verify(knativeClient).services();
        verify(knativeServiceOperation).inNamespace(NAMESPACE);
        verify(namespacedKnativeServiceOperation).withName(SERVICE_NAME);
        verify(knativeServiceResource).withPropagationPolicy(eq(DeletionPropagation.FOREGROUND));
        verify(knativeServiceResource).withGracePeriod(0L);
        verify(knativeServiceResource).delete();
    }
}