package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.kubernetes.knative.K8sKnativeClient;
import io.fabric8.knative.client.KnativeClient;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.knative.serving.v1.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.micrometer.common.util.StringUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class K8sKnativeClientFunctionalTest {

    private static final String TEST_NAMESPACE = "k8s-knative-test";
    private static final String TEST_IMAGE = "gcr.io/knative-samples/helloworld-go";

    @Autowired
    private KnativeClient knativeClient;
    @Autowired
    private KubernetesClient kubernetesClient;
    @Autowired
    private K8sKnativeClient k8sKnativeClient;

    private String testId;

    @BeforeEach
    void setUp() {
        testId = "test-service-" + UUID.randomUUID().toString().substring(0, 8);

        if (!kubernetesClient.namespaces().withName(TEST_NAMESPACE).isReady()) {
            Namespace ns = new NamespaceBuilder()
                    .withNewMetadata()
                    .withName(TEST_NAMESPACE)
                    .endMetadata()
                    .build();
            kubernetesClient.namespaces().resource(ns).create();
        }
    }

    @AfterEach
    void tearDown() {
        knativeClient.services().inNamespace(TEST_NAMESPACE).delete();
        kubernetesClient.pods().inNamespace(TEST_NAMESPACE).delete();
    }

    @SneakyThrows
    @Test
    void testKnativeServiceLifecycle() {
        // Given - Create
        var service = createTestService();

        Predicate<Service> readyPredicate = s -> {
            if (s.getStatus() == null || s.getStatus().getConditions() == null) {
                return false;
            }
            return s.getStatus().getConditions().stream()
                    .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
        };

        // When - Create
        k8sKnativeClient.createService(TEST_NAMESPACE, service);
        var createdService = k8sKnativeClient.waitService(TEST_NAMESPACE, testId, readyPredicate, 120);

        // Then - Create
        assertNotNull(createdService, "Service should be created");
        assertNotNull(createdService.getStatus(), "Service status should not be null");
        assertNull(createdService.getMetadata().getDeletionTimestamp(), "Service deletion timestamp should be null");

        // When - Get pods
        var podList = k8sKnativeClient.getServicePods(TEST_NAMESPACE, testId);

        // Then - Get pods
        assertNotNull(podList, "PodList should not be null");
        assertTrue(podList.getItems().size() > 0, "There should be at least one pod for the service");

        // When - Delete
        k8sKnativeClient.deleteService(TEST_NAMESPACE, testId);

        // Then - Delete
        Thread.sleep(10_000);

        var serviceAfterDeletion = knativeClient.services()
                .inNamespace(TEST_NAMESPACE)
                .withName(testId)
                .get();
        if (serviceAfterDeletion != null) {
            boolean isMarkedForDeletion = StringUtils.isNotEmpty(serviceAfterDeletion.getMetadata().getDeletionTimestamp());
            assertTrue(isMarkedForDeletion);
        }

        var podListAfterDeletion = k8sKnativeClient.getServicePods(TEST_NAMESPACE, testId);
        if (podListAfterDeletion != null) {
            boolean isMarkedForDeletion = podListAfterDeletion.getItems().stream()
                    .allMatch(pod -> StringUtils.isNotEmpty(pod.getMetadata().getDeletionTimestamp()));
            assertTrue(isMarkedForDeletion);
        }
    }

    private Service createTestService() {
        Map<String, Quantity> limits = new HashMap<>();
        limits.put("cpu", new Quantity("500m"));
        limits.put("memory", new Quantity("512Mi"));

        Map<String, Quantity> requests = new HashMap<>();
        requests.put("cpu", new Quantity("250m"));
        requests.put("memory", new Quantity("256Mi"));

        var containerBuilder = new ContainerBuilder()
                .withName(testId)
                .withImage(TEST_IMAGE)
                .withResources(
                    new ResourceRequirementsBuilder()
                        .withLimits(limits)
                        .withRequests(requests)
                        .build()
                )
                .withPorts(
                    Collections.singletonList(
                        new ContainerPortBuilder()
                            .withContainerPort(8080)
                            .build()
                    )
                );

        return new ServiceBuilder()
                .withNewMetadata()
                .withName(testId)
                .addToLabels("app", testId)
                .endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .withContainers(containerBuilder.build())
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }
}
