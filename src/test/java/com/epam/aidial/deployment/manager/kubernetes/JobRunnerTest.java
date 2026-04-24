package com.epam.aidial.deployment.manager.kubernetes;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.K8sResourceKind;
import com.epam.aidial.deployment.manager.service.GlobalDomainWhitelistService;
import com.epam.aidial.deployment.manager.service.JobSpecification;
import com.epam.aidial.deployment.manager.service.pipeline.specification.CiliumNetworkPolicyCreator;
import io.cilium.v2.CiliumNetworkPolicy;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobRunnerTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String LABEL_NAME = "job-name";
    private static final String LABEL_VALUE = "test-job-id";
    private static final String JOB_NAME = "test-job";
    private static final String POD_NAME = "test-pod";

    private static final int TIMEOUT_SEC = 60;
    private static final int STOP_TIMEOUT_SEC = 30;

    private static final List<String> CONTAINER_NAMES = List.of("builder-container");
    private static final List<String> GLOBAL_ALLOWED_DOMAINS = List.of("github.com", "docker.com");
    private static final List<String> ALLOWED_DOMAINS = new ArrayList<>(List.of("epam.com", "dial.com"));

    @Spy
    private CiliumNetworkPolicyCreator ciliumNetworkPolicyCreator;
    @Mock
    private GlobalDomainWhitelistService globalDomainWhitelistService;
    @Mock
    private DisposableResourceManager disposableResourceManager;
    @Mock
    private K8sClient k8sClient;
    @Mock
    private PodLogReader podLogReader;
    @Mock
    private JobCallback jobCallback;
    @Mock
    private JobSpecification jobSpecification;

    private JobRunner jobRunner;
    private UUID groupId;

    private CiliumNetworkPolicy ciliumNetworkPolicy;

    @BeforeEach
    void setUp() {
        jobRunner = new JobRunner(globalDomainWhitelistService, ciliumNetworkPolicyCreator, disposableResourceManager,
                k8sClient, podLogReader, NAMESPACE, TIMEOUT_SEC, STOP_TIMEOUT_SEC);
        groupId = UUID.randomUUID();

        when(jobSpecification.getJobId()).thenReturn(LABEL_VALUE);
        when(jobSpecification.getNamespace()).thenReturn(NAMESPACE);

        List<String> allowedDomains = new ArrayList<>(ALLOWED_DOMAINS);
        allowedDomains.addAll(GLOBAL_ALLOWED_DOMAINS);

        ciliumNetworkPolicy = ciliumNetworkPolicyCreator.create(NAMESPACE, LABEL_NAME, LABEL_VALUE, allowedDomains, null);
    }

    @Test
    void run_shouldSuccessfullyExecuteJob() {
        // Given
        setupMocksForSuccessfulExecution();

        // When
        boolean result = jobRunner.run(jobSpecification, jobCallback, groupId, CONTAINER_NAMES, ALLOWED_DOMAINS);

        // Then
        assertThat(result).isTrue();
        verifyResourcesCreated(true);
        verifyJobPhaseCallbacks(JobPhase.CREATED, JobPhase.RUNNING, JobPhase.SUCCEEDED);
    }

    @Test
    void run_shouldHandleFailedJob() {
        // Given
        setupMocksForFailedExecution();

        // When
        boolean result = jobRunner.run(jobSpecification, jobCallback, groupId, CONTAINER_NAMES, ALLOWED_DOMAINS);

        // Then
        assertThat(result).isFalse();
        verifyResourcesCreated(true);
        verifyJobPhaseCallbacks(JobPhase.CREATED, JobPhase.RUNNING, JobPhase.FAILED);
    }

    @Test
    void run_shouldHandleFailedJobWithDisabledCilium() {
        // Given
        setupMocksForFailedExecutionWithDisabledCilium();

        // When
        boolean result = jobRunner.run(jobSpecification, jobCallback, groupId, CONTAINER_NAMES, ALLOWED_DOMAINS);

        // Then
        assertThat(result).isFalse();
        verifyResourcesCreated(false);
        verifyJobPhaseCallbacks(JobPhase.CREATED, JobPhase.RUNNING, JobPhase.FAILED);
    }

    @SuppressWarnings("unchecked")
    @Test
    void run_shouldThrowJobExternallyDeletedException_whenFinishWaitReturnsNull() {
        // Given — Job starts running but is deleted before reaching a terminal state (simulates admin stop action)
        List<Secret> secrets = List.of(createSecret("secret1"));
        List<ConfigMap> configMaps = List.of(createConfigMap("cm1"));
        Job job = createJob(JOB_NAME);

        when(jobSpecification.getSecrets()).thenReturn(secrets);
        when(jobSpecification.getConfigMaps()).thenReturn(configMaps);
        when(jobSpecification.getJob()).thenReturn(job);

        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(false);

        when(k8sClient.createSecret(eq(NAMESPACE), any(Secret.class))).thenReturn(secrets.getFirst());
        when(k8sClient.createConfigMap(eq(NAMESPACE), any(ConfigMap.class))).thenReturn(configMaps.getFirst());
        when(k8sClient.createJob(eq(NAMESPACE), any(Job.class))).thenReturn(job);

        Job runningJob = createJobWithStatus(JOB_NAME, 1, 0, null, null);
        // First waitJob (for running) returns a running Job; second waitJob (for finished) returns null → externally deleted
        when(k8sClient.waitJob(eq(NAMESPACE), eq(job), any(Predicate.class), eq(TIMEOUT_SEC)))
                .thenReturn(runningJob, (Job) null);

        Pod pod = createPod(POD_NAME, "Running");
        PodList podList = new PodList();
        podList.setItems(List.of(pod));
        when(k8sClient.getJobPods(NAMESPACE, JOB_NAME)).thenReturn(podList);
        when(k8sClient.waitPod(eq(NAMESPACE), eq(pod), any(Predicate.class), eq(TIMEOUT_SEC))).thenReturn(pod);

        PodResource podResource = mock(PodResource.class);
        ContainerResource containerResource = mock(ContainerResource.class);
        when(k8sClient.getPodResource(NAMESPACE, POD_NAME)).thenReturn(podResource);
        when(podResource.inContainer(CONTAINER_NAMES.getFirst())).thenReturn(containerResource);

        // When / Then
        assertThatThrownBy(() -> jobRunner.run(jobSpecification, jobCallback, groupId, CONTAINER_NAMES, ALLOWED_DOMAINS))
                .isInstanceOf(JobExternallyDeletedException.class)
                .hasMessageContaining(LABEL_VALUE);
    }

    @SuppressWarnings("unchecked")
    @Test
    void run_shouldHandleLogStreaming() {
        // Given
        setupMocksForSuccessfulExecution();
        
        // Setup log reading
        ArgumentCaptor<Consumer<List<String>>> logConsumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        doAnswer(invocation -> {
            Consumer<List<String>> logConsumer = logConsumerCaptor.getValue();
            logConsumer.accept(List.of("Log line 1", "Log line 2"));
            return null;
        }).when(podLogReader).readLogs(any(ContainerResource.class), logConsumerCaptor.capture());

        // When
        jobRunner.run(jobSpecification, jobCallback, groupId, CONTAINER_NAMES, new ArrayList<>());

        // Then
        verify(jobCallback).onNewLog(List.of("Log line 1", "Log line 2"));
    }

    @SuppressWarnings("unchecked")
    private void setupMocksForSuccessfulExecution() {
        // Setup resources
        List<Secret> secrets = List.of(createSecret("secret1"), createSecret("secret2"));
        List<ConfigMap> configMaps = List.of(createConfigMap("cm1"), createConfigMap("cm2"));
        Job job = createJob(JOB_NAME);
        
        when(jobSpecification.getSecrets()).thenReturn(secrets);
        when(jobSpecification.getConfigMaps()).thenReturn(configMaps);
        when(jobSpecification.getJob()).thenReturn(job);

        when(globalDomainWhitelistService.getDomainWhitelist()).thenReturn(GLOBAL_ALLOWED_DOMAINS);
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), anyString(), anyList(), eq(null))).thenReturn(ciliumNetworkPolicy);

        // Setup K8s client responses
        when(k8sClient.createSecret(eq(NAMESPACE), any(Secret.class))).thenReturn(secrets.get(0), secrets.get(1));
        when(k8sClient.createConfigMap(eq(NAMESPACE), any(ConfigMap.class))).thenReturn(configMaps.get(0), configMaps.get(1));
        when(k8sClient.createJob(eq(NAMESPACE), any(Job.class))).thenReturn(job);
        when(k8sClient.createCiliumNetworkPolicy(eq(NAMESPACE), any(CiliumNetworkPolicy.class))).thenReturn(ciliumNetworkPolicy);

        // Setup job waiting
        Job runningJob = createJobWithStatus(JOB_NAME, 1, 0, null, null);
        Job succeededJob = createJobWithStatus(JOB_NAME, 0, 0, "Complete", "True");
        
        when(k8sClient.waitJob(eq(NAMESPACE), eq(job), any(Predicate.class), eq(TIMEOUT_SEC)))
                .thenReturn(runningJob, succeededJob);

        // Setup pod
        Pod pod = createPod(POD_NAME, "Running");
        PodList podList = new PodList();
        podList.setItems(List.of(pod));
        
        when(k8sClient.getJobPods(NAMESPACE, JOB_NAME)).thenReturn(podList);
        when(k8sClient.waitPod(eq(NAMESPACE), eq(pod), any(Predicate.class), eq(TIMEOUT_SEC))).thenReturn(pod);

        // Setup pod resource for logs
        PodResource podResource = mock(PodResource.class);
        ContainerResource containerResource = mock(ContainerResource.class);
        
        when(k8sClient.getPodResource(NAMESPACE, POD_NAME)).thenReturn(podResource);
        when(podResource.inContainer(CONTAINER_NAMES.get(0))).thenReturn(containerResource);
    }

    @SuppressWarnings("unchecked")
    private void setupMocksForFailedExecution() {
        // Setup resources
        List<Secret> secrets = List.of(createSecret("secret1"));
        List<ConfigMap> configMaps = List.of(createConfigMap("cm1"));
        Job job = createJob(JOB_NAME);
        
        when(jobSpecification.getSecrets()).thenReturn(secrets);
        when(jobSpecification.getConfigMaps()).thenReturn(configMaps);
        when(jobSpecification.getJob()).thenReturn(job);

        when(globalDomainWhitelistService.getDomainWhitelist()).thenReturn(GLOBAL_ALLOWED_DOMAINS);
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), anyString(), anyList(), eq(null))).thenReturn(ciliumNetworkPolicy);

        // Setup K8s client responses
        when(k8sClient.createSecret(eq(NAMESPACE), any(Secret.class))).thenReturn(secrets.get(0));
        when(k8sClient.createConfigMap(eq(NAMESPACE), any(ConfigMap.class))).thenReturn(configMaps.get(0));
        when(k8sClient.createJob(eq(NAMESPACE), any(Job.class))).thenReturn(job);
        when(k8sClient.createCiliumNetworkPolicy(eq(NAMESPACE), any(CiliumNetworkPolicy.class))).thenReturn(ciliumNetworkPolicy);

        // Setup job waiting
        Job runningJob = createJobWithStatus(JOB_NAME, 1, 0, null, null);
        Job failedJob = createJobWithStatus(JOB_NAME, 0, 1, "Failed", "True");
        
        when(k8sClient.waitJob(eq(NAMESPACE), eq(job), any(Predicate.class), eq(TIMEOUT_SEC)))
                .thenReturn(runningJob, failedJob);

        // Setup pod
        Pod pod = createPod(POD_NAME, "Running");
        PodList podList = new PodList();
        podList.setItems(List.of(pod));
        
        when(k8sClient.getJobPods(NAMESPACE, JOB_NAME)).thenReturn(podList);
        when(k8sClient.waitPod(eq(NAMESPACE), eq(pod), any(Predicate.class), eq(TIMEOUT_SEC))).thenReturn(pod);

        // Setup pod resource for logs
        PodResource podResource = mock(PodResource.class);
        ContainerResource containerResource = mock(ContainerResource.class);
        
        when(k8sClient.getPodResource(NAMESPACE, POD_NAME)).thenReturn(podResource);
        when(podResource.inContainer(CONTAINER_NAMES.get(0))).thenReturn(containerResource);
    }

    @SuppressWarnings("unchecked")
    private void setupMocksForFailedExecutionWithDisabledCilium() {
        // Setup resources
        List<Secret> secrets = List.of(createSecret("secret1"));
        List<ConfigMap> configMaps = List.of(createConfigMap("cm1"));
        Job job = createJob(JOB_NAME);

        when(jobSpecification.getSecrets()).thenReturn(secrets);
        when(jobSpecification.getConfigMaps()).thenReturn(configMaps);
        when(jobSpecification.getJob()).thenReturn(job);

        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(false);

        // Setup K8s client responses
        when(k8sClient.createSecret(eq(NAMESPACE), any(Secret.class))).thenReturn(secrets.get(0));
        when(k8sClient.createConfigMap(eq(NAMESPACE), any(ConfigMap.class))).thenReturn(configMaps.get(0));
        when(k8sClient.createJob(eq(NAMESPACE), any(Job.class))).thenReturn(job);

        // Setup job waiting
        Job runningJob = createJobWithStatus(JOB_NAME, 1, 0, null, null);
        Job failedJob = createJobWithStatus(JOB_NAME, 0, 1, "Failed", "True");

        when(k8sClient.waitJob(eq(NAMESPACE), eq(job), any(Predicate.class), eq(TIMEOUT_SEC)))
                .thenReturn(runningJob, failedJob);

        // Setup pod
        Pod pod = createPod(POD_NAME, "Running");
        PodList podList = new PodList();
        podList.setItems(List.of(pod));

        when(k8sClient.getJobPods(NAMESPACE, JOB_NAME)).thenReturn(podList);
        when(k8sClient.waitPod(eq(NAMESPACE), eq(pod), any(Predicate.class), eq(TIMEOUT_SEC))).thenReturn(pod);

        // Setup pod resource for logs
        PodResource podResource = mock(PodResource.class);
        ContainerResource containerResource = mock(ContainerResource.class);

        when(k8sClient.getPodResource(NAMESPACE, POD_NAME)).thenReturn(podResource);
        when(podResource.inContainer(CONTAINER_NAMES.get(0))).thenReturn(containerResource);
    }

    private void verifyResourcesCreated(boolean ciliumNetworkPoliciesEnabled) {
        // Verify secrets created and tracked
        verify(disposableResourceManager).saveK8sResources(
                eq(jobSpecification.getSecrets()),
                eq(K8sResourceKind.SECRET),
                eq(groupId),
                eq(NAMESPACE)
        );
        
        // Verify configmaps created and tracked
        verify(disposableResourceManager).saveK8sResources(
                eq(jobSpecification.getConfigMaps()),
                eq(K8sResourceKind.CONFIGMAP),
                eq(groupId),
                eq(NAMESPACE)
        );
        
        // Verify job created and tracked
        verify(disposableResourceManager).saveK8sResources(
                eq(List.of(jobSpecification.getJob())),
                eq(K8sResourceKind.JOB),
                eq(groupId),
                eq(NAMESPACE)
        );

        // Verify cilium network policy created and tracked
        if (ciliumNetworkPoliciesEnabled) {
            verify(disposableResourceManager).saveK8sResources(
                    eq(List.of(ciliumNetworkPolicy)),
                    eq(K8sResourceKind.CILIUM_NETWORK_POLICY),
                    eq(groupId),
                    eq(NAMESPACE)
            );
        }

        verify(k8sClient).createJob(eq(NAMESPACE), any(Job.class));
    }

    private void verifyJobPhaseCallbacks(JobPhase... expectedPhases) {
        for (JobPhase phase : expectedPhases) {
            verify(jobCallback).onJobPhaseChange(phase);
        }
    }

    private Secret createSecret(String name) {
        Secret secret = new Secret();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(name);
        secret.setMetadata(metadata);
        return secret;
    }

    private ConfigMap createConfigMap(String name) {
        ConfigMap configMap = new ConfigMap();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(name);
        configMap.setMetadata(metadata);
        return configMap;
    }

    private Job createJob(String name) {
        Job job = new Job();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(name);
        job.setMetadata(metadata);
        
        JobSpec spec = new JobSpec();
        job.setSpec(spec);
        
        return job;
    }

    private Job createJobWithStatus(String name, Integer active, Integer failed, String conditionType, String conditionStatus) {
        Job job = createJob(name);
        JobStatus status = new JobStatus();
        status.setActive(active);
        status.setFailed(failed);
        
        if (conditionType != null && conditionStatus != null) {
            JobCondition condition = new JobCondition();
            condition.setType(conditionType);
            condition.setStatus(conditionStatus);
            status.setConditions(List.of(condition));
        }
        
        job.setStatus(status);
        return job;
    }

    private Pod createPod(String name, String phase) {
        Pod pod = new Pod();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(name);
        pod.setMetadata(metadata);
        
        PodStatus status = new PodStatus();
        status.setPhase(phase);
        pod.setStatus(status);
        
        return pod;
    }
}