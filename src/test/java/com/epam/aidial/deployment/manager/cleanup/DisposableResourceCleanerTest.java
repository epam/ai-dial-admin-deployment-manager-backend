package com.epam.aidial.deployment.manager.cleanup;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ContainerRegistryResourceReference;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceKind;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceReference;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceReference;
import com.epam.aidial.deployment.manager.docker.DockerRegistryClient;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.knative.K8sKnativeClient;
import com.epam.aidial.deployment.manager.kubernetes.kserve.K8sKserveClient;
import com.epam.aidial.deployment.manager.kubernetes.nim.K8sNimClient;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DisposableResourceCleanerTest {

    private static final String NAMESPACE = "some-namespace";
    private static final String GROUP_ID = String.valueOf(UUID.randomUUID());
    private static final Instant NOW = Instant.now();

    @Mock
    private DisposableResourceManager disposableResourceManager;
    @Mock
    private K8sClient k8sClient;
    @Mock
    private K8sKnativeClient k8sKnativeClient;
    @Mock
    private K8sNimClient k8sNimClient;
    @Mock
    private K8sKserveClient k8sKserveClient;
    @Mock
    private DockerRegistryClient registryClient;

    @InjectMocks
    private DisposableResourceCleaner disposableResourceCleaner;

    @Test
    @SneakyThrows
    void testClean() {
        // Given
        List<DisposableResource> resources = List.of(
                buildDisposableResource(buildK8sResourceReference(K8sResourceKind.SECRET, "secret-1")),
                buildDisposableResource(buildK8sResourceReference(K8sResourceKind.SECRET, "secret-2")),
                buildDisposableResource(buildK8sResourceReference(K8sResourceKind.CONFIGMAP, "cm-0")),
                buildDisposableResource(buildK8sResourceReference(K8sResourceKind.JOB, "job-1")),
                buildDisposableResource(buildK8sResourceReference(K8sResourceKind.JOB, "job-2")),
                buildDisposableResource(buildK8sResourceReference(K8sResourceKind.NIM_SERVICE, "nim-1")),
                buildDisposableResource(buildK8sResourceReference(K8sResourceKind.NIM_SERVICE, "nim-2")),
                buildDisposableResource(buildK8sResourceReference(K8sResourceKind.INFERENCE_SERVICE, "inference-1")),
                buildDisposableResource(buildK8sResourceReference(K8sResourceKind.INFERENCE_SERVICE, "inference-2")),
                buildDisposableResource(buildK8sResourceReference(K8sResourceKind.KNATIVE_SERVICE, "knative-1")),
                buildDisposableResource(buildK8sResourceReference(K8sResourceKind.KNATIVE_SERVICE, "knative-2")),
                buildDisposableResource(buildK8sResourceReference(K8sResourceKind.CILIUM_NETWORK_POLICY, "cnp-1")),
                buildDisposableResource(buildK8sResourceReference(K8sResourceKind.CILIUM_NETWORK_POLICY, "cnp-2")),
                buildDisposableResource(buildImageResourceReference("image-1")),
                buildDisposableResource(buildImageResourceReference("image-2"))
        );

        Field field = DisposableResourceCleaner.class.getDeclaredField("takeSize");
        field.setAccessible(true);
        field.set(disposableResourceCleaner, 20);

        when(disposableResourceManager.getAllCleanable(anyInt())).thenReturn(resources);

        // When
        disposableResourceCleaner.cleanAllCleanable();

        // Then
        verify(k8sKnativeClient).deleteService(NAMESPACE, "knative-1");
        verify(k8sKnativeClient).deleteService(NAMESPACE, "knative-2");
        verify(k8sNimClient).deleteService(NAMESPACE, "nim-1");
        verify(k8sNimClient).deleteService(NAMESPACE, "nim-2");
        verify(k8sKserveClient).deleteService(NAMESPACE, "inference-1");
        verify(k8sKserveClient).deleteService(NAMESPACE, "inference-2");
        verify(k8sClient).deleteJob(NAMESPACE, "job-1");
        verify(k8sClient).deleteJob(NAMESPACE, "job-2");
        verify(k8sClient).deleteConfigMap(NAMESPACE, "cm-0");
        verify(k8sClient).deleteSecret(NAMESPACE, "secret-1");
        verify(k8sClient).deleteSecret(NAMESPACE, "secret-2");
        verify(k8sClient).deleteCiliumNetworkPolicy(NAMESPACE, "cnp-1");
        verify(k8sClient).deleteCiliumNetworkPolicy(NAMESPACE, "cnp-2");

        verify(registryClient).deleteImage("image-1");
        verify(registryClient).deleteImage("image-2");

        verify(disposableResourceManager).deleteAll(any());

        verifyNoMoreInteractions(disposableResourceManager, k8sClient, k8sKnativeClient, k8sNimClient, k8sKserveClient, registryClient);
    }

    private static DisposableResource buildDisposableResource(ResourceReference reference) {
        return DisposableResource.builder()
                .groupId(GROUP_ID)
                .reference(reference)
                .createdAt(NOW)
                .build();
    }

    private static K8sResourceReference buildK8sResourceReference(K8sResourceKind kind, String name) {
        return K8sResourceReference.builder()
                .kind(kind)
                .namespace(NAMESPACE)
                .name(name)
                .build();
    }

    private static ContainerRegistryResourceReference buildImageResourceReference(String name) {
        return ContainerRegistryResourceReference.builder()
                .name(name)
                .build();
    }
}
