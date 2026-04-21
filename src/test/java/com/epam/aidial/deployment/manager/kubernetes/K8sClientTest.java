package com.epam.aidial.deployment.manager.kubernetes;

import io.cilium.v2.CiliumNetworkPolicy;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobList;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.BatchAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import io.fabric8.kubernetes.client.dsl.V1BatchAPIGroupDSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("rawtypes")
@ExtendWith(MockitoExtension.class)
class K8sClientTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String CONFIGMAP_NAME = "test-configmap";
    private static final String SECRET_NAME = "test-secret";
    private static final String JOB_NAME = "test-job";
    private static final String POD_NAME = "test-pod";
    private static final String CNP_NAME = "test-cnp";
    private static final long TIMEOUT_SEC = 60;

    @Mock
    private KubernetesClient kubernetesClient;
    @Mock
    private MixedOperation<Pod, PodList, PodResource> podOperation;
    @Mock
    private MixedOperation<Secret, SecretList, Resource<Secret>> secretOperation;
    @Mock
    private MixedOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> configMapOperation;
    @Mock
    private BatchAPIGroupDSL batchApiGroupDsl;
    @Mock
    private V1BatchAPIGroupDSL v1BatchApiGroupDsl;
    @Mock
    private MixedOperation<Job, JobList, ScalableResource<Job>> jobOperation;
    @Mock
    private NonNamespaceOperation<Pod, PodList, PodResource> namespacedPodOperation;
    @Mock
    private NonNamespaceOperation<Secret, SecretList, Resource<Secret>> namespacedSecretOperation;
    @Mock
    private NonNamespaceOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> namespacedConfigMapOperation;
    @Mock
    private NonNamespaceOperation<Job, JobList, ScalableResource<Job>> namespacedJobOperation;
    @Mock
    private PodResource podResource;
    @Mock
    private Resource<Secret> secretResource;
    @Mock
    private Resource<ConfigMap> configMapResource;
    @Mock
    private ScalableResource<Job> scalableJobResource;
    @Mock
    private MixedOperation cnpOperation;
    @Mock
    private NonNamespaceOperation namespacedCnpOperation;
    @Mock
    private Resource cnpResource;

    private K8sClient k8sClient;

    @BeforeEach
    void setUp() {
        k8sClient = new K8sClient(kubernetesClient);
    }

    @Test
    void getJobPods_shouldReturnPodsForJob() {
        // Given
        PodList expectedPodList = new PodList();
        Map<String, String> jobLabels = Map.of("job-name", JOB_NAME);

        when(kubernetesClient.pods()).thenReturn(podOperation);
        when(podOperation.inNamespace(NAMESPACE)).thenReturn(namespacedPodOperation);
        when(namespacedPodOperation.withLabels(eq(jobLabels))).thenReturn(namespacedPodOperation);
        when(namespacedPodOperation.list()).thenReturn(expectedPodList);

        // When
        PodList result = k8sClient.getJobPods(NAMESPACE, JOB_NAME);

        // Then
        assertThat(result).isSameAs(expectedPodList);
        verify(namespacedPodOperation).withLabels(eq(jobLabels));
        verify(namespacedPodOperation).list();
    }

    @Test
    void getPods_shouldReturnPodsWithLabels() {
        // Given
        PodList expectedPodList = new PodList();
        Map<String, String> labels = Map.of("app", "test-app", "tier", "backend");

        when(kubernetesClient.pods()).thenReturn(podOperation);
        when(podOperation.inNamespace(NAMESPACE)).thenReturn(namespacedPodOperation);
        when(namespacedPodOperation.withLabels(eq(labels))).thenReturn(namespacedPodOperation);
        when(namespacedPodOperation.list()).thenReturn(expectedPodList);

        // When
        PodList result = k8sClient.getPods(NAMESPACE, labels);

        // Then
        assertThat(result).isSameAs(expectedPodList);
        verify(namespacedPodOperation).withLabels(eq(labels));
        verify(namespacedPodOperation).list();
    }

    @Test
    void getPod_shouldReturnPodWhenNameAndLabelsMatch() {
        // Given
        Pod expectedPod = new Pod();
        ObjectMeta metadata = new ObjectMeta();
        Map<String, String> labels = Map.of("app", "test-app");
        metadata.setLabels(labels);
        expectedPod.setMetadata(metadata);

        when(kubernetesClient.pods()).thenReturn(podOperation);
        when(podOperation.inNamespace(NAMESPACE)).thenReturn(namespacedPodOperation);
        when(namespacedPodOperation.withName(POD_NAME)).thenReturn(podResource);
        when(podResource.get()).thenReturn(expectedPod);

        // When
        Pod result = k8sClient.getPod(NAMESPACE, POD_NAME, labels);

        // Then
        assertThat(result).isSameAs(expectedPod);
        verify(podResource).get();
    }

    @Test
    void getPod_shouldReturnNullWhenPodNotFound() {
        // Given
        when(kubernetesClient.pods()).thenReturn(podOperation);
        when(podOperation.inNamespace(NAMESPACE)).thenReturn(namespacedPodOperation);
        when(namespacedPodOperation.withName(POD_NAME)).thenReturn(podResource);
        when(podResource.get()).thenReturn(null);

        Map<String, String> labels = Map.of("app", "test-app");

        // When
        Pod result = k8sClient.getPod(NAMESPACE, POD_NAME, labels);

        // Then
        assertThat(result).isNull();
        verify(podResource).get();
    }

    @Test
    void getPod_shouldReturnNullWhenLabelsDoNotMatch() {
        // Given
        Pod pod = new Pod();
        ObjectMeta metadata = new ObjectMeta();
        Map<String, String> actualLabels = Map.of("app", "different-app");
        metadata.setLabels(actualLabels);
        pod.setMetadata(metadata);

        when(kubernetesClient.pods()).thenReturn(podOperation);
        when(podOperation.inNamespace(NAMESPACE)).thenReturn(namespacedPodOperation);
        when(namespacedPodOperation.withName(POD_NAME)).thenReturn(podResource);
        when(podResource.get()).thenReturn(pod);

        Map<String, String> requestedLabels = Map.of("app", "test-app");

        // When
        Pod result = k8sClient.getPod(NAMESPACE, POD_NAME, requestedLabels);

        // Then
        assertThat(result).isNull();
        verify(podResource).get();
    }

    @Test
    void getPodResource_shouldReturnPodResource() {
        // Given
        when(kubernetesClient.pods()).thenReturn(podOperation);
        when(podOperation.inNamespace(NAMESPACE)).thenReturn(namespacedPodOperation);
        when(namespacedPodOperation.withName(POD_NAME)).thenReturn(podResource);

        // When
        PodResource result = k8sClient.getPodResource(NAMESPACE, POD_NAME);

        // Then
        assertThat(result).isSameAs(podResource);
        verify(podOperation).inNamespace(NAMESPACE);
        verify(namespacedPodOperation).withName(POD_NAME);
    }

    @Test
    void deletePod_shouldReturnTrueWhenPodDeleted() {
        // Given
        when(kubernetesClient.pods()).thenReturn(podOperation);
        when(podOperation.inNamespace(NAMESPACE)).thenReturn(namespacedPodOperation);
        when(namespacedPodOperation.withName(POD_NAME)).thenReturn(podResource);
        when(podResource.withGracePeriod(0)).thenAnswer(invocation -> podResource);
        when(podResource.delete()).thenReturn(List.of(new StatusDetails()));

        // When
        boolean result = k8sClient.deletePod(NAMESPACE, POD_NAME);

        // Then
        assertThat(result).isTrue();
        verify(podResource).withGracePeriod(0);
        verify(podResource).delete();
    }

    @Test
    void deletePod_shouldReturnFalseWhenPodNotFound() {
        // Given
        when(kubernetesClient.pods()).thenReturn(podOperation);
        when(podOperation.inNamespace(NAMESPACE)).thenReturn(namespacedPodOperation);
        when(namespacedPodOperation.withName(POD_NAME)).thenReturn(podResource);
        when(podResource.withGracePeriod(0)).thenAnswer(invocation -> podResource);
        when(podResource.delete()).thenReturn(List.of());

        // When
        boolean result = k8sClient.deletePod(NAMESPACE, POD_NAME);

        // Then
        assertThat(result).isFalse();
        verify(podResource).withGracePeriod(0);
        verify(podResource).delete();
    }

    @SuppressWarnings("unchecked")
    @Test
    void waitPod_shouldWaitForPodCondition() {
        // Given
        Pod inputPod = new Pod();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(POD_NAME);
        inputPod.setMetadata(metadata);

        Pod expectedPod = new Pod();
        PodStatus status = new PodStatus();
        status.setPhase("Running");
        expectedPod.setStatus(status);

        Predicate<Pod> predicate = pod -> "Running".equals(pod.getStatus().getPhase());

        when(kubernetesClient.pods()).thenReturn(podOperation);
        when(podOperation.inNamespace(NAMESPACE)).thenReturn(namespacedPodOperation);
        when(namespacedPodOperation.withName(POD_NAME)).thenReturn(podResource);
        when(podResource.waitUntilCondition((Predicate<Pod>) any(Predicate.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(expectedPod);

        // When
        Pod result = k8sClient.waitPod(NAMESPACE, inputPod, predicate, 60);

        // Then
        assertThat(result).isSameAs(expectedPod);
        verify(podOperation).inNamespace(NAMESPACE);
        verify(namespacedPodOperation).withName(POD_NAME);
        verify(podResource).waitUntilCondition(eq(predicate), eq(TIMEOUT_SEC), eq(TimeUnit.SECONDS));
    }

    @Test
    void findSecret_shouldReturnEmptyOptionalWhenSecretNotFound() {
        // Given
        when(kubernetesClient.secrets()).thenReturn(secretOperation);
        when(secretOperation.inNamespace(NAMESPACE)).thenReturn(namespacedSecretOperation);
        when(namespacedSecretOperation.withName(SECRET_NAME)).thenReturn(secretResource);
        when(secretResource.get()).thenReturn(null);

        // When
        Optional<Secret> result = k8sClient.findSecret(NAMESPACE, SECRET_NAME);

        // Then
        assertThat(result.isPresent()).isFalse();
        verify(secretOperation).inNamespace(NAMESPACE);
        verify(namespacedSecretOperation).withName(SECRET_NAME);
        verify(secretResource).get();
    }

    @Test
    void findSecret_shouldReturnSecretWhenFound() {
        // Given
        Secret expectedSecret = new Secret();

        when(kubernetesClient.secrets()).thenReturn(secretOperation);
        when(secretOperation.inNamespace(NAMESPACE)).thenReturn(namespacedSecretOperation);
        when(namespacedSecretOperation.withName(SECRET_NAME)).thenReturn(secretResource);
        when(secretResource.get()).thenReturn(expectedSecret);

        // When
        Optional<Secret> result = k8sClient.findSecret(NAMESPACE, SECRET_NAME);

        // Then
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isSameAs(expectedSecret);
        verify(secretOperation).inNamespace(NAMESPACE);
        verify(namespacedSecretOperation).withName(SECRET_NAME);
        verify(secretResource).get();
    }

    @Test
    void createSecret_shouldCreateAndReturnSecret() {
        // Given
        Secret inputSecret = new Secret();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(SECRET_NAME);
        inputSecret.setMetadata(metadata);

        Secret createdSecret = new Secret();

        when(kubernetesClient.secrets()).thenReturn(secretOperation);
        when(secretOperation.inNamespace(NAMESPACE)).thenReturn(namespacedSecretOperation);
        when(namespacedSecretOperation.resource(inputSecret)).thenReturn(secretResource);
        when(secretResource.create()).thenReturn(createdSecret);

        // When
        Secret result = k8sClient.createSecret(NAMESPACE, inputSecret);

        // Then
        assertThat(result).isSameAs(createdSecret);
        verify(secretOperation).inNamespace(NAMESPACE);
        verify(namespacedSecretOperation).resource(inputSecret);
        verify(secretResource).create();
    }

    @Test
    void deleteSecret_shouldDeleteSecret() {
        // Given
        when(kubernetesClient.secrets()).thenReturn(secretOperation);
        when(secretOperation.inNamespace(NAMESPACE)).thenReturn(namespacedSecretOperation);
        when(namespacedSecretOperation.withName(SECRET_NAME)).thenReturn(secretResource);

        // When
        k8sClient.deleteSecret(NAMESPACE, SECRET_NAME);

        // Then
        verify(secretOperation).inNamespace(NAMESPACE);
        verify(namespacedSecretOperation).withName(SECRET_NAME);
        verify(secretResource).delete();
    }

    @Test
    void createConfigMap_shouldCreateAndReturnConfigMap() {
        // Given
        ConfigMap inputConfigMap = new ConfigMap();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(CONFIGMAP_NAME);
        inputConfigMap.setMetadata(metadata);

        ConfigMap createdConfigMap = new ConfigMap();

        when(kubernetesClient.configMaps()).thenReturn(configMapOperation);
        when(configMapOperation.inNamespace(NAMESPACE)).thenReturn(namespacedConfigMapOperation);
        when(namespacedConfigMapOperation.resource(inputConfigMap)).thenReturn(configMapResource);
        when(configMapResource.create()).thenReturn(createdConfigMap);

        // When
        ConfigMap result = k8sClient.createConfigMap(NAMESPACE, inputConfigMap);

        // Then
        assertThat(result).isSameAs(createdConfigMap);
        verify(configMapOperation).inNamespace(NAMESPACE);
        verify(namespacedConfigMapOperation).resource(inputConfigMap);
        verify(configMapResource).create();
    }

    @Test
    void deleteConfigMap_shouldDeleteConfigMap() {
        // Given
        when(kubernetesClient.configMaps()).thenReturn(configMapOperation);
        when(configMapOperation.inNamespace(NAMESPACE)).thenReturn(namespacedConfigMapOperation);
        when(namespacedConfigMapOperation.withName(CONFIGMAP_NAME)).thenReturn(configMapResource);

        // When
        k8sClient.deleteConfigMap(NAMESPACE, CONFIGMAP_NAME);

        // Then
        verify(configMapOperation).inNamespace(NAMESPACE);
        verify(namespacedConfigMapOperation).withName(CONFIGMAP_NAME);
        verify(configMapResource).delete();
    }

    @Test
    void createJob_shouldCreateAndReturnJob() {
        // Given
        Job inputJob = new Job();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(JOB_NAME);
        inputJob.setMetadata(metadata);

        Job createdJob = new Job();

        when(kubernetesClient.batch()).thenReturn(batchApiGroupDsl);
        when(batchApiGroupDsl.v1()).thenReturn(v1BatchApiGroupDsl);
        when(v1BatchApiGroupDsl.jobs()).thenReturn(jobOperation);
        when(jobOperation.inNamespace(NAMESPACE)).thenReturn(namespacedJobOperation);
        when(namespacedJobOperation.resource(inputJob)).thenReturn(scalableJobResource);
        when(scalableJobResource.create()).thenReturn(createdJob);

        // When
        Job result = k8sClient.createJob(NAMESPACE, inputJob);

        // Then
        assertThat(result).isSameAs(createdJob);
        verify(v1BatchApiGroupDsl.jobs()).inNamespace(NAMESPACE);
        verify(namespacedJobOperation).resource(inputJob);
        verify(scalableJobResource).create();
    }

    @Test
    void deleteJob_shouldDeleteJob() {
        // Given
        when(kubernetesClient.batch()).thenReturn(batchApiGroupDsl);
        when(batchApiGroupDsl.v1()).thenReturn(v1BatchApiGroupDsl);
        when(v1BatchApiGroupDsl.jobs()).thenReturn(jobOperation);
        when(jobOperation.inNamespace(NAMESPACE)).thenReturn(namespacedJobOperation);
        when(namespacedJobOperation.withName(JOB_NAME)).thenReturn(scalableJobResource);

        // When
        k8sClient.deleteJob(NAMESPACE, JOB_NAME);

        // Then
        verify(v1BatchApiGroupDsl.jobs()).inNamespace(NAMESPACE);
        verify(namespacedJobOperation).withName(JOB_NAME);
        verify(scalableJobResource).delete();
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteCiliumNetworkPolicy_shouldDeleteSuccessfully() {
        // Given
        when(kubernetesClient.resources(CiliumNetworkPolicy.class)).thenReturn(cnpOperation);
        when(cnpOperation.inNamespace(NAMESPACE)).thenReturn(namespacedCnpOperation);
        when(namespacedCnpOperation.withName(CNP_NAME)).thenReturn(cnpResource);

        // When
        k8sClient.deleteCiliumNetworkPolicy(NAMESPACE, CNP_NAME);

        // Then
        verify(cnpResource).delete();
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteCiliumNetworkPolicy_shouldTreatNotFoundAsDeleted() {
        // Given
        when(kubernetesClient.resources(CiliumNetworkPolicy.class)).thenReturn(cnpOperation);
        when(cnpOperation.inNamespace(NAMESPACE)).thenReturn(namespacedCnpOperation);
        when(namespacedCnpOperation.withName(CNP_NAME)).thenReturn(cnpResource);
        when(cnpResource.delete()).thenThrow(new KubernetesClientException("Not Found", 404, null));

        // When — should not throw
        assertThatCode(() -> k8sClient.deleteCiliumNetworkPolicy(NAMESPACE, CNP_NAME))
                .doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteCiliumNetworkPolicy_shouldRethrowNon404KubernetesException() {
        // Given
        when(kubernetesClient.resources(CiliumNetworkPolicy.class)).thenReturn(cnpOperation);
        when(cnpOperation.inNamespace(NAMESPACE)).thenReturn(namespacedCnpOperation);
        when(namespacedCnpOperation.withName(CNP_NAME)).thenReturn(cnpResource);
        when(cnpResource.delete()).thenThrow(new KubernetesClientException("Internal Server Error", 500, null));

        // When & Then
        assertThatThrownBy(() -> k8sClient.deleteCiliumNetworkPolicy(NAMESPACE, CNP_NAME))
                .isInstanceOf(KubernetesClientException.class);
    }

    @Test
    void deleteJobsByLabelAndWait_shouldBeNoOpWhenNoJobsMatch() {
        // Given
        JobList emptyList = new JobList();
        emptyList.setItems(List.of());

        when(kubernetesClient.batch()).thenReturn(batchApiGroupDsl);
        when(batchApiGroupDsl.v1()).thenReturn(v1BatchApiGroupDsl);
        when(v1BatchApiGroupDsl.jobs()).thenReturn(jobOperation);
        when(jobOperation.inNamespace(NAMESPACE)).thenReturn(namespacedJobOperation);
        when(namespacedJobOperation.withLabel("image-definition-id", "group-1"))
                .thenAnswer(inv -> namespacedJobOperation);
        when(namespacedJobOperation.list()).thenReturn(emptyList);

        // When
        k8sClient.deleteJobsByLabelAndWait(NAMESPACE, "image-definition-id", "group-1", 30);

        // Then
        verify(namespacedJobOperation).list();
        verify(namespacedJobOperation, never()).withName(anyString());
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteJobsByLabelAndWait_shouldDeleteWithForegroundAndWaitForEachMatchingJob() {
        // Given
        Job job1 = jobNamed("job-1");
        Job job2 = jobNamed("job-2");
        JobList matchingList = new JobList();
        matchingList.setItems(List.of(job1, job2));

        when(kubernetesClient.batch()).thenReturn(batchApiGroupDsl);
        when(batchApiGroupDsl.v1()).thenReturn(v1BatchApiGroupDsl);
        when(v1BatchApiGroupDsl.jobs()).thenReturn(jobOperation);
        when(jobOperation.inNamespace(NAMESPACE)).thenReturn(namespacedJobOperation);
        when(namespacedJobOperation.withLabel("image-definition-id", "group-1"))
                .thenAnswer(inv -> namespacedJobOperation);
        when(namespacedJobOperation.list()).thenReturn(matchingList);

        ScalableResource<Job> res1 = mock(ScalableResource.class);
        ScalableResource<Job> res2 = mock(ScalableResource.class);
        when(namespacedJobOperation.withName("job-1")).thenReturn(res1);
        when(namespacedJobOperation.withName("job-2")).thenReturn(res2);
        doReturn(res1).when(res1).withPropagationPolicy(DeletionPropagation.FOREGROUND);
        doReturn(res1).when(res1).withGracePeriod(0L);
        doReturn(res2).when(res2).withPropagationPolicy(DeletionPropagation.FOREGROUND);
        doReturn(res2).when(res2).withGracePeriod(0L);
        when(res1.waitUntilCondition((Predicate<Job>) any(Predicate.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(null);
        when(res2.waitUntilCondition((Predicate<Job>) any(Predicate.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(null);

        // When
        k8sClient.deleteJobsByLabelAndWait(NAMESPACE, "image-definition-id", "group-1", 30);

        // Then
        verify(res1).withPropagationPolicy(DeletionPropagation.FOREGROUND);
        verify(res1).withGracePeriod(0L);
        verify(res1).delete();
        verify(res2).withPropagationPolicy(DeletionPropagation.FOREGROUND);
        verify(res2).withGracePeriod(0L);
        verify(res2).delete();
        ArgumentCaptor<Predicate<Job>> predicateCaptor = ArgumentCaptor.forClass(Predicate.class);
        verify(res1).waitUntilCondition(predicateCaptor.capture(), eq(30L), eq(TimeUnit.SECONDS));
        verify(res2).waitUntilCondition(predicateCaptor.capture(), eq(30L), eq(TimeUnit.SECONDS));
        // "Job is gone" → predicate returns true on null
        assertThat(predicateCaptor.getAllValues()).allSatisfy(p -> {
            assertThat(p.test(null)).isTrue();
            assertThat(p.test(new Job())).isFalse();
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteJobsByLabelAndWait_shouldPropagateKubernetesClientException() {
        // Given
        Job job1 = jobNamed("job-1");
        JobList matchingList = new JobList();
        matchingList.setItems(List.of(job1));

        when(kubernetesClient.batch()).thenReturn(batchApiGroupDsl);
        when(batchApiGroupDsl.v1()).thenReturn(v1BatchApiGroupDsl);
        when(v1BatchApiGroupDsl.jobs()).thenReturn(jobOperation);
        when(jobOperation.inNamespace(NAMESPACE)).thenReturn(namespacedJobOperation);
        when(namespacedJobOperation.withLabel("image-definition-id", "group-1"))
                .thenAnswer(inv -> namespacedJobOperation);
        when(namespacedJobOperation.list()).thenReturn(matchingList);

        ScalableResource<Job> res1 = mock(ScalableResource.class);
        when(namespacedJobOperation.withName("job-1")).thenReturn(res1);
        doReturn(res1).when(res1).withPropagationPolicy(DeletionPropagation.FOREGROUND);
        doReturn(res1).when(res1).withGracePeriod(0L);
        when(res1.delete()).thenThrow(new KubernetesClientException("cluster boom"));

        // When / Then
        assertThatThrownBy(() ->
                k8sClient.deleteJobsByLabelAndWait(NAMESPACE, "image-definition-id", "group-1", 30))
                .isInstanceOf(KubernetesClientException.class)
                .hasMessageContaining("cluster boom");

        verify(res1, never()).waitUntilCondition(any(Predicate.class), anyLong(), eq(TimeUnit.SECONDS));
    }

    private static Job jobNamed(String name) {
        Job job = new Job();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(name);
        job.setMetadata(metadata);
        return job;
    }

    @SuppressWarnings("unchecked")
    @Test
    void waitJob_shouldWaitForJobCondition() {
        // Given
        Job inputJob = new Job();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(JOB_NAME);
        inputJob.setMetadata(metadata);

        Job expectedJob = new Job();
        JobStatus status = new JobStatus();
        status.setActive(0);
        status.setSucceeded(1);
        expectedJob.setStatus(status);

        Predicate<Job> predicate = j -> j.getStatus().getActive() == 0 && j.getStatus().getSucceeded() == 1;

        when(kubernetesClient.batch()).thenReturn(batchApiGroupDsl);
        when(batchApiGroupDsl.v1()).thenReturn(v1BatchApiGroupDsl);
        when(v1BatchApiGroupDsl.jobs()).thenReturn(jobOperation);
        when(jobOperation.inNamespace(NAMESPACE)).thenReturn(namespacedJobOperation);
        when(namespacedJobOperation.withName(JOB_NAME)).thenReturn(scalableJobResource);
        when(scalableJobResource.waitUntilCondition((Predicate<Job>) any(Predicate.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(expectedJob);

        // When
        Job result = k8sClient.waitJob(NAMESPACE, inputJob, predicate, 60);

        // Then
        assertThat(result).isSameAs(expectedJob);
        verify(v1BatchApiGroupDsl.jobs()).inNamespace(NAMESPACE);
        verify(namespacedJobOperation).withName(JOB_NAME);
        verify(scalableJobResource).waitUntilCondition(eq(predicate), eq(TIMEOUT_SEC), eq(TimeUnit.SECONDS));
    }
}