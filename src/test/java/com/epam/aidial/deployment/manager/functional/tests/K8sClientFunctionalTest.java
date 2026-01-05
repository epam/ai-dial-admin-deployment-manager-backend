package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.kubernetes.JobPhase;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.PodPhase;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class K8sClientFunctionalTest {

    private static final String TEST_NAMESPACE = "k8s-client-test";
    private static final String TEST_ID_LABEL = "test-id";

    @Autowired
    private K8sClient k8sClient;
    @Autowired
    private KubernetesClient kubernetesClient;

    private String testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID().toString().substring(0, 8);

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
        kubernetesClient.secrets().inNamespace(TEST_NAMESPACE).withLabel(TEST_ID_LABEL, testId).delete();
        kubernetesClient.configMaps().inNamespace(TEST_NAMESPACE).withLabel(TEST_ID_LABEL, testId).delete();
        kubernetesClient.batch().v1().jobs().inNamespace(TEST_NAMESPACE).withLabel(TEST_ID_LABEL, testId).delete();
        kubernetesClient.pods().inNamespace(TEST_NAMESPACE).withLabel(TEST_ID_LABEL, testId).delete();
    }

    @Test
    void testSecretOperations() {
        // Given
        String secretName = "test-secret-" + testId;
        Map<String, String> data = new HashMap<>();
        data.put("username", Base64.getEncoder().encodeToString("testuser".getBytes()));
        data.put("password", Base64.getEncoder().encodeToString("testpass".getBytes()));

        Secret secret = new SecretBuilder()
                .withNewMetadata()
                .withName(secretName)
                .addToLabels(TEST_ID_LABEL, testId)
                .endMetadata()
                .withData(data)
                .build();

        // When - Create
        Secret createdSecret = k8sClient.createSecret(TEST_NAMESPACE, secret);

        // Then - Create
        assertNotNull(createdSecret);
        assertEquals(secretName, createdSecret.getMetadata().getName());

        // When - Find
        Optional<Secret> foundSecret = k8sClient.findSecret(TEST_NAMESPACE, secretName);

        // Then - Find
        assertTrue(foundSecret.isPresent());
        assertEquals(secretName, foundSecret.get().getMetadata().getName());
        assertEquals(data.get("username"), foundSecret.get().getData().get("username"));
        assertEquals(data.get("password"), foundSecret.get().getData().get("password"));

        // When - Delete
        k8sClient.deleteSecret(TEST_NAMESPACE, secretName);

        // Then - Delete
        foundSecret = k8sClient.findSecret(TEST_NAMESPACE, secretName);
        assertFalse(foundSecret.isPresent());
    }

    @Test
    void testConfigMapOperations() {
        // Given
        String configMapName = "test-configmap-" + testId;
        Map<String, String> data = new HashMap<>();
        data.put("config.json", "{\"key\": \"value\"}");

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(configMapName)
                .addToLabels(TEST_ID_LABEL, testId)
                .endMetadata()
                .withData(data)
                .build();

        // When - Create
        ConfigMap createdConfigMap = k8sClient.createConfigMap(TEST_NAMESPACE, configMap);

        // Then - Create
        assertNotNull(createdConfigMap);
        assertEquals(configMapName, createdConfigMap.getMetadata().getName());
        assertEquals(data.get("config.json"), createdConfigMap.getData().get("config.json"));

        // When - Delete
        k8sClient.deleteConfigMap(TEST_NAMESPACE, configMapName);

        // Then - Delete
        ConfigMap deletedConfigMap = kubernetesClient.configMaps()
                .inNamespace(TEST_NAMESPACE)
                .withName(configMapName)
                .get();
        assertNull(deletedConfigMap);
    }

    @Test
    void testJobOperations() {
        // Given
        String jobName = "test-job-" + testId;

        Job job = new JobBuilder()
                .withNewMetadata()
                .withName(jobName)
                .addToLabels(TEST_ID_LABEL, testId)
                .endMetadata()
                .withNewSpec()
                .withBackoffLimit(0)
                .withNewTemplate()
                .withNewSpec()
                .addNewContainer()
                .withName("job-container")
                .withImage("busybox")
                .withCommand("sh", "-c", "echo Hello from test job && sleep 1")
                .endContainer()
                .withRestartPolicy("Never")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        // When - Create
        Job createdJob = k8sClient.createJob(TEST_NAMESPACE, job);

        // Then - Create
        assertNotNull(createdJob);
        assertEquals(jobName, createdJob.getMetadata().getName());

        // When - Wait
        Predicate<Job> jobIsFinished = j -> JobPhase.fromJob(j)
                .map(JobPhase::isFinal)
                .orElse(false);

        Job completedJob = k8sClient.waitJob(TEST_NAMESPACE, job, jobIsFinished, 300);

        // Then - Job completed
        assertNotNull(completedJob);
        assertNotNull(completedJob.getStatus());

        assertTrue(completedJob.getStatus().getSucceeded() > 0);

        // When - Delete
        k8sClient.deleteJob(TEST_NAMESPACE, jobName);

        // Then - Delete
        Job deletedJob = kubernetesClient.batch().v1().jobs()
                .inNamespace(TEST_NAMESPACE)
                .withName(jobName)
                .get();
        assertNull(deletedJob);

        // When - Events
        var eventsOperation = k8sClient.getAllEventsBase(TEST_NAMESPACE);
        var events = eventsOperation.list().getItems();

        // Then - Events
        assertFalse(events.isEmpty());
    }

    @Test
    void testPodOperations() {
        // Given - Create
        String jobName = "pod-test-job-" + testId;

        Job job = new JobBuilder()
                .withNewMetadata()
                .withName(jobName)
                .addToLabels(TEST_ID_LABEL, testId)
                .endMetadata()
                .withNewSpec()
                .withBackoffLimit(5)
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels(TEST_ID_LABEL, testId)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("job-container")
                .withImage("docker.io/library/busybox:latest")
                .withCommand("sh", "-c", "echo Hello from pod test && sleep 5")
                .endContainer()
                .withRestartPolicy("Never")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        k8sClient.createJob(TEST_NAMESPACE, job);

        // Wait for the pod
        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When - getJobPods
        PodList jobPods = k8sClient.getJobPods(TEST_NAMESPACE, jobName);

        // Then - getJobPods
        assertNotNull(jobPods);
        assertFalse(jobPods.getItems().isEmpty());

        Pod pod = jobPods.getItems().get(0);
        String podName = pod.getMetadata().getName();

        // When - getPod
        Map<String, String> labels = new HashMap<>();
        labels.put("job-name", jobName);
        Pod foundPod = k8sClient.getPod(TEST_NAMESPACE, podName, labels);

        // Then - getPod
        assertNotNull(foundPod);
        assertEquals(podName, foundPod.getMetadata().getName());

        // When - getPodResource
        PodResource podResource = k8sClient.getPodResource(TEST_NAMESPACE, podName);

        // Then - getPodResource
        assertNotNull(podResource);

        // When - waitPod
        Predicate<Pod> podIsRunning = p -> PodPhase.fromPod(p)
                .map(s -> s == PodPhase.RUNNING || s.isFinal())
                .orElse(false);

        Pod completedPod = k8sClient.waitPod(TEST_NAMESPACE, pod, podIsRunning, 300);

        // Then - waitPod
        assertNotNull(completedPod);

        String podPhase = completedPod.getStatus().getPhase();
        assertTrue("Failed".equals(podPhase) || "Running".equals(podPhase));

        // When - Delete
        boolean deleted = k8sClient.deletePod(TEST_NAMESPACE, podName);

        // Then - Pod
        assertTrue(deleted);

        // Cleanup
        k8sClient.deleteJob(TEST_NAMESPACE, jobName);
    }

    @Test
    void testGetPods() {
        // Given - Create a pod with specific labels
        String podName = "test-pod-" + testId;
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "test-app");
        labels.put(TEST_ID_LABEL, testId);

        Pod pod = new Pod();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(podName);
        metadata.setNamespace(TEST_NAMESPACE);
        metadata.setLabels(labels);
        pod.setMetadata(metadata);
        pod.setSpec(new PodSpecBuilder()
                .addNewContainer()
                .withName("test-container")
                .withImage("busybox")
                .withCommand("sh", "-c", "echo Hello from test pod && sleep 60")
                .endContainer()
                .withRestartPolicy("Never")
                .build());

        kubernetesClient.pods().inNamespace(TEST_NAMESPACE).resource(pod).create();

        // Wait for the pod
        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When
        PodList podList = k8sClient.getPods(TEST_NAMESPACE, labels);

        // Then
        assertNotNull(podList);
        assertFalse(podList.getItems().isEmpty());
        assertEquals(1, podList.getItems().size());
        assertEquals(podName, podList.getItems().get(0).getMetadata().getName());

        // Cleanup
        kubernetesClient.pods().inNamespace(TEST_NAMESPACE).withName(podName).delete();
    }
}
