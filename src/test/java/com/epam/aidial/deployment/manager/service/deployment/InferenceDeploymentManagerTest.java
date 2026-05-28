package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.configuration.KserveDeployProperties;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.DeploymentException;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.exception.ValidationException;
import com.epam.aidial.deployment.manager.huggingface.properties.HuggingFaceProperties;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.kserve.K8sKserveClient;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.HuggingFaceSource;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.service.detection.InferenceTaskDetectionResult;
import com.epam.aidial.deployment.manager.service.detection.InferenceTaskDetector;
import com.epam.aidial.deployment.manager.service.manifest.InferenceManifestGenerator;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.service.pipeline.specification.CiliumNetworkPolicyCreator;
import io.cilium.v2.CiliumNetworkPolicy;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStateBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.kserve.serving.v1beta1.InferenceService;
import io.kserve.serving.v1beta1.InferenceServiceStatus;
import io.kserve.serving.v1beta1.inferenceservicestatus.Components;
import io.kserve.serving.v1beta1.inferenceservicestatus.ModelStatus;
import io.kserve.serving.v1beta1.inferenceservicestatus.components.Address;
import io.kserve.serving.v1beta1.inferenceservicestatus.modelstatus.States;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferenceDeploymentManagerTest {

    private static final String DEPLOYMENT_ID = String.valueOf(UUID.randomUUID());
    private static final int STARTUP_TIMEOUT = 60;
    private static final String NAMESPACE = "test-namespace";
    private static final String SERVICE_NAME = "dm-" + DEPLOYMENT_ID;
    private static final String CONTAINER_NAME = "test-container";
    private static final String POD_NAME = "test-pod";
    private static final String SERVICE_URL = "http://service-name.test.com";
    private static final String INTERNAL_SERVICE_URL = "http://service-name.test-namespace.svc.cluster.local";
    private static final int DEFAULT_KSERVE_SERVICE_PORT = 8080;

    @Mock
    private DisposableResourceManager disposableResourceManager;
    @Mock
    private ManifestGenerator manifestGenerator;
    @Mock
    private InferenceManifestGenerator inferenceManifestGenerator;
    @Mock
    private ContainerPortResolver containerPortResolver;
    @Mock
    private CiliumNetworkPolicyCreator ciliumNetworkPolicyCreator;
    @Mock
    private NodePoolProperties nodePoolProperties;
    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private K8sClient k8sClient;
    @Mock
    private K8sKserveClient k8sKserveClient;
    @Mock
    private PodResource podResource;
    @Mock
    private ContainerResource containerResource;
    @Mock
    private CiliumNetworkPolicy ciliumNetworkPolicy;
    @Mock
    private InferenceTaskDetector inferenceTaskDetector;

    private InferenceDeploymentManager inferenceDeploymentManager;

    @BeforeEach
    void setUp() {
        var kserveDeployProperties = new KserveDeployProperties();
        kserveDeployProperties.setNamespace(NAMESPACE);
        kserveDeployProperties.setStartupTimeout(STARTUP_TIMEOUT);
        kserveDeployProperties.setUseClusterInternalUrl(false);

        var huggingFaceProperties = new HuggingFaceProperties();
        inferenceDeploymentManager = new InferenceDeploymentManager(
                k8sClient,
                disposableResourceManager,
                manifestGenerator,
                inferenceManifestGenerator,
                containerPortResolver,
                ciliumNetworkPolicyCreator,
                nodePoolProperties,
                deploymentRepository,
                k8sKserveClient,
                kserveDeployProperties,
                huggingFaceProperties,
                inferenceTaskDetector
        );
        lenient().when(inferenceTaskDetector.detect(any())).thenReturn(InferenceTaskDetectionResult.none());

        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void getActiveInstances_shouldReturnListOfReadyPods() {
        // Given
        var podList = new PodList();
        var readyPod = createPod("ready-pod", true);
        var notReadyPod = createPod("not-ready-pod", false);
        podList.setItems(List.of(readyPod, notReadyPod));

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePods(NAMESPACE, SERVICE_NAME)).thenReturn(podList);

        // When
        var result = inferenceDeploymentManager.getActiveInstances(DEPLOYMENT_ID);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("ready-pod");
    }

    @Test
    void getActiveInstances_shouldReturnEmptyListWhenNoPodsFound() {
        // Given
        var emptyPodList = new PodList();
        emptyPodList.setItems(Collections.emptyList());

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePods(NAMESPACE, SERVICE_NAME)).thenReturn(emptyPodList);

        // When
        var result = inferenceDeploymentManager.getActiveInstances(DEPLOYMENT_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getActiveInstances_shouldThrowExceptionWhenContainerMissing() {
        // Given
        var podList = new PodList();
        var pod = new Pod();
        pod.setMetadata(new ObjectMeta());
        pod.getMetadata().setName("pod-without-container");
        pod.getMetadata().setCreationTimestamp(Instant.now().toString());
        var status = new PodStatus();
        status.setContainerStatuses(Collections.emptyList());
        pod.setStatus(status);
        podList.setItems(List.of(pod));

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePods(NAMESPACE, SERVICE_NAME)).thenReturn(podList);

        // When/Then
        assertThatThrownBy(() -> inferenceDeploymentManager.getActiveInstances(DEPLOYMENT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A container is missing in the service pod");
    }

    @Test
    void getInstances_shouldReturnPodsWithRestartInfo() {
        // Given
        var podList = new PodList();
        var pod = createPodWithRestartInfo("pod-with-restarts", 5, "OOMKilled", 137, 9);
        podList.setItems(List.of(pod));

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePods(NAMESPACE, SERVICE_NAME)).thenReturn(podList);

        // When
        var result = inferenceDeploymentManager.getInstances(DEPLOYMENT_ID);

        // Then
        assertThat(result).hasSize(1);
        var podInfo = result.getFirst();
        assertThat(podInfo.getName()).isEqualTo("pod-with-restarts");
        assertThat(podInfo.getRestartCount()).isEqualTo(5);
        assertThat(podInfo.getLastTerminationReason()).isEqualTo("OOMKilled");
        assertThat(podInfo.getLastExitCode()).isEqualTo(137);
        assertThat(podInfo.getLastSignal()).isEqualTo(9);
    }

    @Test
    void getInstances_shouldReturnPodsWithoutTerminationInfo() {
        // Given
        var podList = new PodList();
        var pod = createPod("pod-no-restarts", true);
        podList.setItems(List.of(pod));

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePods(NAMESPACE, SERVICE_NAME)).thenReturn(podList);

        // When
        var result = inferenceDeploymentManager.getInstances(DEPLOYMENT_ID);

        // Then
        assertThat(result).hasSize(1);
        var podInfo = result.getFirst();
        assertThat(podInfo.getName()).isEqualTo("pod-no-restarts");
        assertThat(podInfo.getRestartCount()).isEqualTo(0);
        assertThat(podInfo.getLastTerminationReason()).isNull();
        assertThat(podInfo.getLastExitCode()).isNull();
        assertThat(podInfo.getLastSignal()).isNull();
    }

    @Test
    void getInstances_shouldAggregateRestartCountsFromMultipleContainers() {
        // Given
        var podList = new PodList();
        var pod = createPodWithMultipleContainers("pod-multi-container", 3, 2);
        podList.setItems(List.of(pod));

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePods(NAMESPACE, SERVICE_NAME)).thenReturn(podList);

        // When
        var result = inferenceDeploymentManager.getInstances(DEPLOYMENT_ID);

        // Then
        assertThat(result).hasSize(1);
        var podInfo = result.getFirst();
        assertThat(podInfo.getName()).isEqualTo("pod-multi-container");
        assertThat(podInfo.getRestartCount()).isEqualTo(5); // 3 + 2
    }

    @Test
    void getContainerResourceForLogs_shouldReturnContainerResourceForRunningPod() {
        // Given
        Pod pod = createPod(POD_NAME, true);
        pod.getStatus().getContainerStatuses().getFirst()
                .setState(new ContainerStateBuilder().withNewRunning().endRunning().build());

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME)).thenReturn(pod);
        when(k8sClient.getPodResource(NAMESPACE, POD_NAME)).thenReturn(podResource);
        when(podResource.inContainer(CONTAINER_NAME)).thenReturn(containerResource);

        // When
        ContainerResource result = inferenceDeploymentManager.getContainerResourceForLogs(DEPLOYMENT_ID, POD_NAME, false);

        // Then
        assertThat(result).isEqualTo(containerResource);
        verify(k8sKserveClient).getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME);
        verify(k8sClient).getPodResource(NAMESPACE, POD_NAME);
        verify(podResource).inContainer(CONTAINER_NAME);
    }

    @Test
    void getContainerResourceForLogs_shouldThrowExceptionWhenPodNotFound() {
        // Given
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME)).thenReturn(null);

        // When / Then
        assertThatThrownBy(() -> inferenceDeploymentManager.getContainerResourceForLogs(DEPLOYMENT_ID, POD_NAME, false))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Pod is not found for deployment '%s'".formatted(DEPLOYMENT_ID));
    }

    @Test
    void getContainerResourceForLogs_shouldThrowExceptionWhenContainerNotFound() {
        // Given
        Pod pod = new Pod();
        pod.setMetadata(new ObjectMeta());
        pod.setSpec(new PodSpec());
        pod.getSpec().setContainers(Collections.emptyList());

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME)).thenReturn(pod);

        // When/Then
        assertThatThrownBy(() -> inferenceDeploymentManager.getContainerResourceForLogs(DEPLOYMENT_ID, POD_NAME, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Container not found for pod");
    }

    @Test
    void getContainerResourceForLogs_shouldThrowWhenContainerStatusIsEmpty() {
        // Given
        Pod pod = createPod(POD_NAME, true);
        pod.getStatus().setContainerStatuses(Collections.emptyList());

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME)).thenReturn(pod);

        // When / Then
        assertThatThrownBy(() -> inferenceDeploymentManager.getContainerResourceForLogs(DEPLOYMENT_ID, POD_NAME, false))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Container is not ready for log streaming for deployment '%s'".formatted(DEPLOYMENT_ID));
    }

    @Test
    void getContainerResourceForLogs_shouldThrowWhenContainerStatusNotFoundByName() {
        // Given
        Pod pod = createPod(POD_NAME, true);
        pod.getStatus().getContainerStatuses().getFirst().setName("other-container");

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME)).thenReturn(pod);

        // When / Then
        assertThatThrownBy(() -> inferenceDeploymentManager.getContainerResourceForLogs(DEPLOYMENT_ID, POD_NAME, false))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Container is not found for deployment '%s'".formatted(DEPLOYMENT_ID));
    }

    @Test
    void getContainerResourceForLogs_shouldThrowWhenContainerIsNotRunning() {
        // Given
        Pod pod = createPod(POD_NAME, false); // waiting state

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME)).thenReturn(pod);

        // When / Then
        assertThatThrownBy(() -> inferenceDeploymentManager.getContainerResourceForLogs(DEPLOYMENT_ID, POD_NAME, false))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Container is not running for deployment '%s'".formatted(DEPLOYMENT_ID));
    }

    @Test
    void getContainerResourceForLogs_shouldThrowWhenContainerIsTerminated() {
        // Given
        Pod pod = createPod(POD_NAME, true);
        pod.getStatus().getContainerStatuses().getFirst()
                .setState(new ContainerStateBuilder().withNewTerminated().endTerminated().build());

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME)).thenReturn(pod);

        // When / Then
        assertThatThrownBy(() -> inferenceDeploymentManager.getContainerResourceForLogs(DEPLOYMENT_ID, POD_NAME, false))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Container is not running for deployment '%s'".formatted(DEPLOYMENT_ID));
    }

    @Test
    void getContainerResourceForLogs_shouldThrowWhenPreviousLogsNotAvailable() {
        // Given
        Pod pod = createPod(POD_NAME, true);
        pod.getStatus().getContainerStatuses().getFirst()
                .setState(new ContainerStateBuilder().withNewRunning().endRunning().build());

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME)).thenReturn(pod);

        // When / Then
        assertThatThrownBy(() -> inferenceDeploymentManager.getContainerResourceForLogs(DEPLOYMENT_ID, POD_NAME, true))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Previous logs are not available for container in deployment '%s'".formatted(DEPLOYMENT_ID));
    }

    @Test
    void getContainerResourceForLogs_shouldReturnContainerResourceForPreviousLogs() {
        // Given
        Pod pod = createPod(POD_NAME, true);
        var containerStatus = pod.getStatus().getContainerStatuses().getFirst();
        containerStatus.setState(new ContainerStateBuilder().withNewRunning().endRunning().build());
        containerStatus.setLastState(new ContainerStateBuilder().withNewTerminated().endTerminated().build());

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(createDeployment(DeploymentStatus.RUNNING)));
        when(k8sKserveClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME)).thenReturn(pod);
        when(k8sClient.getPodResource(NAMESPACE, POD_NAME)).thenReturn(podResource);
        when(podResource.inContainer(CONTAINER_NAME)).thenReturn(containerResource);

        // When
        ContainerResource result = inferenceDeploymentManager.getContainerResourceForLogs(DEPLOYMENT_ID, POD_NAME, true);

        // Then
        assertThat(result).isEqualTo(containerResource);
    }

    @Test
    void deploy_shouldDeployInferenceService() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.STOPPED);
        deployment.setServiceName(null);
        InferenceService serviceSpec = new InferenceService();
        serviceSpec.setMetadata(new ObjectMeta());
        serviceSpec.getMetadata().setName(SERVICE_NAME);
        Integer containerPort = 8080;

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(containerPortResolver.resolveContainerPort(any(), eq(DEFAULT_KSERVE_SERVICE_PORT)))
                .thenReturn(containerPort);
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), eq(SERVICE_NAME), anyList(), any())).thenReturn(ciliumNetworkPolicy);
        when(inferenceManifestGenerator.serviceConfig(eq(DEPLOYMENT_ID), eq(SERVICE_NAME), any(), any(), any(), any(), any(), any(),
                any(), any(), eq(containerPort), any(), anyInt(), any(), any(), any())).thenReturn(serviceSpec);

        // When
        Deployment result = inferenceDeploymentManager.deploy(DEPLOYMENT_ID);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        assertThat(result).isEqualTo(deployment);
        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.PENDING);
        assertThat(result.getServiceName()).isEqualTo(SERVICE_NAME);

        verify(disposableResourceManager).saveInferenceServiceResource(eq(DEPLOYMENT_ID), eq(SERVICE_NAME), eq(NAMESPACE));
        verify(k8sKserveClient).createService(eq(NAMESPACE), eq(serviceSpec));
        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.PENDING));
        verify(deploymentRepository).updateServiceName(eq(DEPLOYMENT_ID), eq(SERVICE_NAME));

        // Cilium policy created with deployment domains list (no default domains)
        verify(ciliumNetworkPolicyCreator).create(
                eq(NAMESPACE),
                anyString(),
                eq(SERVICE_NAME),
                argThat((List<String> domains) ->
                    domains.contains("test-domain-1")
                        && domains.contains("test-domain-2")
                        && domains.size() == 2),
                any()
        );
    }

    @Test
    void deploy_shouldPassChainedFlagToCiliumPolicyCreator_whenTransformerEmitted() {
        // Given: detection returns TEXT_CLASSIFICATION → manifest will be chained
        Deployment deployment = createDeployment(DeploymentStatus.STOPPED);
        deployment.setServiceName(null);
        InferenceService serviceSpec = new InferenceService();
        serviceSpec.setMetadata(new ObjectMeta());
        serviceSpec.getMetadata().setName(SERVICE_NAME);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(containerPortResolver.resolveContainerPort(any(), eq(DEFAULT_KSERVE_SERVICE_PORT))).thenReturn(8080);
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), eq(SERVICE_NAME), anyList(), any(), eq(true)))
                .thenReturn(ciliumNetworkPolicy);
        when(inferenceManifestGenerator.serviceConfig(eq(DEPLOYMENT_ID), eq(SERVICE_NAME), any(), any(), any(), any(), any(), any(),
                any(), any(), eq(8080), any(), anyInt(), any(), any(), any())).thenReturn(serviceSpec);
        when(inferenceTaskDetector.detect(any()))
                .thenReturn(InferenceTaskDetectionResult.textClassification(Map.of(0, "NEGATIVE", 1, "POSITIVE")));

        // When
        inferenceDeploymentManager.deploy(DEPLOYMENT_ID);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then: chained-mode creator overload called with chainedTransformer=true
        verify(ciliumNetworkPolicyCreator).create(
                eq(NAMESPACE),
                anyString(),
                eq(SERVICE_NAME),
                anyList(),
                any(),
                eq(true)
        );
    }

    @Test
    void updateCiliumNetworkPolicy_shouldPreserveChainedAugmentation_onAllowedDomainsUpdate() {
        // Given: a chained-mode inference deployment whose allowedDomains the operator is updating.
        // The chained signal is sourced from the live K8s InferenceService — no HuggingFace Hub call.
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(k8sKserveClient.getService(NAMESPACE, SERVICE_NAME))
                .thenReturn(createInferenceServiceWithTransformer());
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), eq(SERVICE_NAME), anyList(), any(), eq(true)))
                .thenReturn(ciliumNetworkPolicy);
        when(ciliumNetworkPolicy.getMetadata()).thenReturn(new ObjectMeta());

        // When
        inferenceDeploymentManager.updateCiliumNetworkPolicy(DEPLOYMENT_ID);

        // Then: chained-mode augmentation preserved (6-arg overload with chainedTransformer=true)
        verify(ciliumNetworkPolicyCreator).create(
                eq(NAMESPACE),
                anyString(),
                eq(SERVICE_NAME),
                anyList(),
                any(),
                eq(true)
        );
        // Spec 022 FR-005: the chained signal must come from cluster state, not a separate HF Hub fetch.
        verify(inferenceTaskDetector, never()).detect(any());
        // The K8s update actually went through.
        verify(k8sClient).updateCiliumNetworkPolicy(eq(NAMESPACE), eq(ciliumNetworkPolicy));
    }

    @Test
    void updateCiliumNetworkPolicy_shouldEmitBaselinePolicy_whenClusterServicePredictorOnly() {
        // Given: cluster InferenceService has no transformer block → baseline (non-chained) policy.
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(k8sKserveClient.getService(NAMESPACE, SERVICE_NAME))
                .thenReturn(createInferenceServiceWithoutTransformer());
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), eq(SERVICE_NAME), anyList(), any()))
                .thenReturn(ciliumNetworkPolicy);
        when(ciliumNetworkPolicy.getMetadata()).thenReturn(new ObjectMeta());

        // When
        inferenceDeploymentManager.updateCiliumNetworkPolicy(DEPLOYMENT_ID);

        // Then: 5-arg overload is used (baseline shape) — chained-mode 6-arg overload is NOT invoked.
        verify(ciliumNetworkPolicyCreator).create(
                eq(NAMESPACE), anyString(), eq(SERVICE_NAME), anyList(), any());
        verify(ciliumNetworkPolicyCreator, never()).create(
                anyString(), anyString(), anyString(), anyList(), any(), eq(true));
        verify(inferenceTaskDetector, never()).detect(any());
    }

    @Test
    void updateCiliumNetworkPolicy_shouldFail_whenLiveServiceMissing() {
        // Given: the K8s InferenceService is unexpectedly absent (delete race, RBAC, etc.).
        // The endpoint MUST NOT silently downgrade the policy to non-chained — that would
        // re-create the very failure mode spec 022 fixes.
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(k8sKserveClient.getService(NAMESPACE, SERVICE_NAME)).thenReturn(null);

        assertThatThrownBy(() -> inferenceDeploymentManager.updateCiliumNetworkPolicy(DEPLOYMENT_ID))
                .isInstanceOf(DeploymentException.class)
                .hasMessageContaining(DEPLOYMENT_ID)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("InferenceService '%s' not found".formatted(SERVICE_NAME));

        // No policy was written.
        verify(ciliumNetworkPolicyCreator, never()).create(
                anyString(), anyString(), anyString(), anyList(), any());
        verify(ciliumNetworkPolicyCreator, never()).create(
                anyString(), anyString(), anyString(), anyList(), any(), anyBoolean());
        verify(k8sClient, never()).updateCiliumNetworkPolicy(anyString(), any());
    }

    @Test
    void updateCiliumNetworkPolicy_shouldPropagate_whenK8sClientThrows() {
        // Given: the cluster read fails transiently. The endpoint must surface the failure
        // wrapped as DeploymentException so the operator retries instead of seeing a 200.
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(k8sKserveClient.getService(NAMESPACE, SERVICE_NAME))
                .thenThrow(new RuntimeException("kube-apiserver unreachable"));

        assertThatThrownBy(() -> inferenceDeploymentManager.updateCiliumNetworkPolicy(DEPLOYMENT_ID))
                .isInstanceOf(DeploymentException.class)
                .hasRootCauseInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("kube-apiserver unreachable");

        verify(k8sClient, never()).updateCiliumNetworkPolicy(anyString(), any());
    }

    @Test
    void updateCiliumNetworkPolicy_shouldNoOp_whenServiceNameBlank() {
        // Given: deployment has never reached the create-service step (no serviceName yet).
        // There is no live K8s resource to read and no CNP to update — short-circuit silently
        // rather than attempting a cluster read with a null/blank name.
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        deployment.setServiceName(null);
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        inferenceDeploymentManager.updateCiliumNetworkPolicy(DEPLOYMENT_ID);

        verify(k8sKserveClient, never()).getService(anyString(), anyString());
        verify(ciliumNetworkPolicyCreator, never()).create(
                anyString(), anyString(), anyString(), anyList(), any());
        verify(ciliumNetworkPolicyCreator, never()).create(
                anyString(), anyString(), anyString(), anyList(), any(), anyBoolean());
        verify(k8sClient, never()).updateCiliumNetworkPolicy(anyString(), any());
    }

    @Test
    void rollingUpdate_shouldRefreshCiliumPolicy_whenTopologyFlipsToChained() {
        // Given: a running deployment whose modelName flip causes prepareServiceSpec to emit a chained
        // InferenceService — the CNP must be refreshed in lock-step inside rollingUpdate's afterCommit.
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        InferenceService serviceSpec = new InferenceService();
        serviceSpec.setMetadata(new ObjectMeta());
        serviceSpec.getMetadata().setName(SERVICE_NAME);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(containerPortResolver.resolveContainerPort(any(), eq(DEFAULT_KSERVE_SERVICE_PORT))).thenReturn(8080);
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), eq(SERVICE_NAME), anyList(), any(), eq(true)))
                .thenReturn(ciliumNetworkPolicy);
        when(ciliumNetworkPolicy.getMetadata()).thenReturn(new ObjectMeta());
        when(inferenceManifestGenerator.serviceConfig(eq(DEPLOYMENT_ID), eq(SERVICE_NAME), any(), any(), any(), any(), any(), any(),
                any(), any(), eq(8080), any(), anyInt(), any(), any(), any())).thenReturn(serviceSpec);
        when(inferenceTaskDetector.detect(any()))
                .thenReturn(InferenceTaskDetectionResult.textClassification(Map.of(0, "A", 1, "B")));

        // When
        inferenceDeploymentManager.rollingUpdate(DEPLOYMENT_ID);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then: service was updated AND the CNP was refreshed with chainedTransformer=true (spec 022 FR-007).
        verify(k8sKserveClient).updateService(eq(NAMESPACE), eq(serviceSpec));
        verify(ciliumNetworkPolicyCreator).create(
                eq(NAMESPACE), anyString(), eq(SERVICE_NAME), anyList(), any(), eq(true));
        verify(k8sClient).updateCiliumNetworkPolicy(eq(NAMESPACE), eq(ciliumNetworkPolicy));

        // CNP refresh MUST happen before the service update — half-applied flips that update the
        // service first and the policy second can re-create bug #87 if the CNP write fails.
        InOrder order = inOrder(k8sClient, k8sKserveClient);
        order.verify(k8sClient).updateCiliumNetworkPolicy(eq(NAMESPACE), eq(ciliumNetworkPolicy));
        order.verify(k8sKserveClient).updateService(eq(NAMESPACE), eq(serviceSpec));
    }

    private InferenceService createInferenceServiceWithTransformer() {
        InferenceService service = new InferenceService();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(SERVICE_NAME);
        service.setMetadata(metadata);
        var spec = new io.kserve.serving.v1beta1.InferenceServiceSpec();
        spec.setTransformer(new io.kserve.serving.v1beta1.inferenceservicespec.Transformer());
        service.setSpec(spec);
        return service;
    }

    private InferenceService createInferenceServiceWithoutTransformer() {
        InferenceService service = new InferenceService();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(SERVICE_NAME);
        service.setMetadata(metadata);
        service.setSpec(new io.kserve.serving.v1beta1.InferenceServiceSpec());
        return service;
    }

    @Test
    void deploy_shouldPassUnchainedFlag_whenPredictorOnly() {
        // Given: detection returns NONE (default lenient stub in setUp) → predictor-only manifest
        Deployment deployment = createDeployment(DeploymentStatus.STOPPED);
        deployment.setServiceName(null);
        InferenceService serviceSpec = new InferenceService();
        serviceSpec.setMetadata(new ObjectMeta());
        serviceSpec.getMetadata().setName(SERVICE_NAME);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(containerPortResolver.resolveContainerPort(any(), eq(DEFAULT_KSERVE_SERVICE_PORT))).thenReturn(8080);
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), eq(SERVICE_NAME), anyList(), any()))
                .thenReturn(ciliumNetworkPolicy);
        when(inferenceManifestGenerator.serviceConfig(eq(DEPLOYMENT_ID), eq(SERVICE_NAME), any(), any(), any(), any(), any(), any(),
                any(), any(), eq(8080), any(), anyInt(), any(), any(), any())).thenReturn(serviceSpec);

        // When
        inferenceDeploymentManager.deploy(DEPLOYMENT_ID);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then: legacy 5-arg overload still called (chainedTransformer=false path preserves baseline call shape)
        verify(ciliumNetworkPolicyCreator).create(
                eq(NAMESPACE),
                anyString(),
                eq(SERVICE_NAME),
                anyList(),
                any()
        );
        // …and the 6-arg overload was NOT invoked
        verify(ciliumNetworkPolicyCreator, never()).create(
                anyString(), anyString(), anyString(), anyList(), any(), eq(true));
        verify(ciliumNetworkPolicyCreator, never()).create(
                anyString(), anyString(), anyString(), anyList(), any(), eq(false));
    }

    @Test
    void deploy_shouldMergeDefaultAllowedDomainsWithDeploymentDomainsForHuggingFaceSource() {
        // Given: HuggingFace source with deployment-specific domains and config default domains
        var huggingFaceProperties = createHuggingFacePropertiesWithDefaultDomains();
        var managerWithDefaults = getInferenceDeploymentManager(huggingFaceProperties);

        InferenceDeployment deployment = (InferenceDeployment) createDeployment(DeploymentStatus.STOPPED);
        deployment.setSource(new HuggingFaceSource("org/model"));
        deployment.setAllowedDomains(List.of("custom.com"));

        InferenceService serviceSpec = new InferenceService();
        serviceSpec.setMetadata(new ObjectMeta());
        serviceSpec.getMetadata().setName(SERVICE_NAME);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(containerPortResolver.resolveContainerPort(any(), eq(DEFAULT_KSERVE_SERVICE_PORT))).thenReturn(8080);
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), eq(SERVICE_NAME), anyList(), any())).thenReturn(ciliumNetworkPolicy);
        when(inferenceManifestGenerator.serviceConfig(eq(DEPLOYMENT_ID), eq(SERVICE_NAME), any(), any(), any(), any(), any(), any(),
                any(), any(), eq(8080), any(), anyInt(), any(), any(), any())).thenReturn(serviceSpec);

        // When
        managerWithDefaults.deploy(DEPLOYMENT_ID);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then: Cilium policy created with merged list (deployment domains + default domains, no duplicates)
        verify(ciliumNetworkPolicyCreator).create(
                eq(NAMESPACE),
                anyString(),
                eq(SERVICE_NAME),
                argThat((List<String> domains) ->
                        domains.contains("custom.com")
                                && domains.contains("huggingface.co")
                                && domains.contains("cdn.huggingface.co")
                                && domains.size() == 3),
                any()
        );
    }

    @Test
    void deploy_shouldUseOnlyDefaultAllowedDomainsWhenDeploymentDomainsEmptyWithHuggingFaceSource() {
        // Given: HuggingFace source with empty deployment allowedDomains
        var huggingFaceProperties = createHuggingFacePropertiesWithDefaultDomains();
        var managerWithDefaults = getInferenceDeploymentManager(huggingFaceProperties);

        InferenceDeployment deployment = (InferenceDeployment) createDeployment(DeploymentStatus.STOPPED);
        deployment.setSource(new HuggingFaceSource("org/model"));
        deployment.setAllowedDomains(Collections.emptyList());

        InferenceService serviceSpec = new InferenceService();
        serviceSpec.setMetadata(new ObjectMeta());
        serviceSpec.getMetadata().setName(SERVICE_NAME);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(containerPortResolver.resolveContainerPort(any(), eq(DEFAULT_KSERVE_SERVICE_PORT))).thenReturn(8080);
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), eq(SERVICE_NAME), anyList(), any())).thenReturn(ciliumNetworkPolicy);
        when(inferenceManifestGenerator.serviceConfig(eq(DEPLOYMENT_ID), eq(SERVICE_NAME), any(), any(), any(), any(), any(), any(),
                any(), any(), eq(8080), any(), anyInt(), any(), any(), any())).thenReturn(serviceSpec);

        // When
        managerWithDefaults.deploy(DEPLOYMENT_ID);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then: only default allowed domains appear in Cilium policy
        verify(ciliumNetworkPolicyCreator).create(
                eq(NAMESPACE),
                anyString(),
                eq(SERVICE_NAME),
                argThat((List<String> domains) ->
                        domains.contains("huggingface.co")
                                && domains.contains("cdn.huggingface.co")
                                && domains.size() == 2),
                any()
        );
    }

    private InferenceDeploymentManager getInferenceDeploymentManager(HuggingFaceProperties huggingFaceProperties) {
        var kserveDeployProperties = new KserveDeployProperties();
        kserveDeployProperties.setNamespace(NAMESPACE);
        kserveDeployProperties.setStartupTimeout(STARTUP_TIMEOUT);
        kserveDeployProperties.setUseClusterInternalUrl(false);
        return new InferenceDeploymentManager(
                k8sClient,
                disposableResourceManager,
                manifestGenerator,
                inferenceManifestGenerator,
                containerPortResolver,
                ciliumNetworkPolicyCreator,
                nodePoolProperties,
                deploymentRepository,
                k8sKserveClient,
                kserveDeployProperties, huggingFaceProperties,
                inferenceTaskDetector
        );
    }

    @Test
    void deploy_shouldReturnExistingDeploymentIfAlreadyActive() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        // When
        Deployment result = inferenceDeploymentManager.deploy(DEPLOYMENT_ID);

        // Then
        assertThat(result).isEqualTo(deployment);
        verify(k8sKserveClient, never()).createService(anyString(), any());
        verify(deploymentRepository, never()).updateStatus(any(), any());
        verify(deploymentRepository, never()).updateServiceName(any(), any());
    }

    @Test
    void deploy_shouldThrowExceptionWhenDeploymentNotFound() {
        // Given
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> inferenceDeploymentManager.deploy(DEPLOYMENT_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Deployment not found");
    }

    @Test
    void deploy_shouldHandleExceptionDuringDeployment() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.STOPPED);
        InferenceService serviceSpec = new InferenceService();
        serviceSpec.setMetadata(new ObjectMeta());
        serviceSpec.getMetadata().setName(SERVICE_NAME);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(containerPortResolver.resolveContainerPort(any(), eq(DEFAULT_KSERVE_SERVICE_PORT))).thenReturn(8080);
        when(inferenceManifestGenerator.serviceConfig(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any())).thenReturn(serviceSpec);
        doThrow(new RuntimeException("Test exception")).when(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), any());

        // When/Then
        assertThatThrownBy(() -> inferenceDeploymentManager.deploy(DEPLOYMENT_ID))
                .isInstanceOf(DeploymentException.class)
                .hasMessageContaining("Failed to deploy service");

        verify(disposableResourceManager).markInferenceServiceResourceForCleanup(eq(DEPLOYMENT_ID), eq(SERVICE_NAME), eq(NAMESPACE));
    }

    @Test
    void undeploy_shouldUndeployInferenceService() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        deployment.setUrl(SERVICE_URL);
        DisposableResource disposableResource = mock(DisposableResource.class);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(disposableResourceManager.markInferenceServiceResourceForCleanup(DEPLOYMENT_ID, SERVICE_NAME, NAMESPACE))
                .thenReturn(List.of(disposableResource));

        // When
        Deployment result = inferenceDeploymentManager.undeploy(DEPLOYMENT_ID);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        assertThat(result).isEqualTo(deployment);
        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.STOPPING);

        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.STOPPING));
        verify(k8sKserveClient).deleteService(eq(NAMESPACE), eq(SERVICE_NAME));
        verify(disposableResourceManager).deleteAll(List.of(disposableResource));
    }

    @Test
    void undeploy_shouldReturnExistingDeploymentIfAlreadyInactive() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.STOPPED);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        // When
        Deployment result = inferenceDeploymentManager.undeploy(DEPLOYMENT_ID);

        // Then
        assertThat(result).isEqualTo(deployment);
        verify(k8sKserveClient, never()).deleteService(anyString(), anyString());
        verify(deploymentRepository, never()).updateStatus(any(), any());
    }

    @Test
    void undeploy_shouldReturnExistingDeploymentIfStopping() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.STOPPING);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        // When
        Deployment result = inferenceDeploymentManager.undeploy(DEPLOYMENT_ID);

        // Then
        assertThat(result).isEqualTo(deployment);
        verify(k8sKserveClient, never()).deleteService(anyString(), anyString());
        verify(deploymentRepository, never()).update(any(), any());
    }

    @Test
    void undeploy_shouldThrowExceptionWhenDeploymentNotFound() {
        // Given
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> inferenceDeploymentManager.undeploy(DEPLOYMENT_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Deployment not found");
    }

    @Test
    void undeploy_shouldHandleExceptionDuringUndeployment() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        DisposableResource disposableResource = mock(DisposableResource.class);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(disposableResourceManager.markInferenceServiceResourceForCleanup(DEPLOYMENT_ID, SERVICE_NAME, NAMESPACE))
                .thenReturn(List.of(disposableResource));
        doThrow(new RuntimeException("Test exception")).when(k8sKserveClient).deleteService(NAMESPACE, SERVICE_NAME);

        // When
        inferenceDeploymentManager.undeploy(DEPLOYMENT_ID);

        // Execute transaction synchronization callbacks manually for unit tests
        // This should throw the exception
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThatThrownBy(() -> {
            for (TransactionSynchronization sync : synchronizations) {
                sync.afterCommit();
            }
        })
                .isInstanceOf(DeploymentException.class)
                .hasMessageContaining("Unexpected error while undeploying service");
    }

    @Test
    void rollingUpdate_shouldPerformRollingUpdate() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        InferenceService serviceSpec = new InferenceService();
        serviceSpec.setMetadata(new ObjectMeta());
        serviceSpec.getMetadata().setName(SERVICE_NAME);
        Integer containerPort = 8080;

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(containerPortResolver.resolveContainerPort(any(), eq(DEFAULT_KSERVE_SERVICE_PORT)))
                .thenReturn(containerPort);
        when(inferenceManifestGenerator.serviceConfig(eq(DEPLOYMENT_ID), eq(SERVICE_NAME), any(), any(), any(), any(), any(), any(),
                any(), any(), eq(containerPort), any(), anyInt(), any(), any(), any())).thenReturn(serviceSpec);

        // When
        inferenceDeploymentManager.rollingUpdate(DEPLOYMENT_ID);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        verify(k8sKserveClient).updateService(eq(NAMESPACE), eq(serviceSpec));
        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.PENDING));
        verify(deploymentRepository, never()).updateServiceName(any(), any());
    }

    @Test
    void rollingUpdate_shouldNotUpdateWhenDeploymentNotRunning() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.PENDING);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        // When
        inferenceDeploymentManager.rollingUpdate(DEPLOYMENT_ID);

        // Then
        verify(k8sKserveClient, never()).updateService(anyString(), any());
        verify(deploymentRepository, never()).updateStatus(any(), any());
    }

    @Test
    void rollingUpdate_shouldHandleExceptionDuringUpdate() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        InferenceService serviceSpec = new InferenceService();
        serviceSpec.setMetadata(new ObjectMeta());
        serviceSpec.getMetadata().setName(SERVICE_NAME);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(containerPortResolver.resolveContainerPort(any(), eq(DEFAULT_KSERVE_SERVICE_PORT))).thenReturn(8080);
        when(inferenceManifestGenerator.serviceConfig(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any())).thenReturn(serviceSpec);
        doThrow(new RuntimeException("Test exception")).when(k8sKserveClient).updateService(eq(NAMESPACE), any());

        // When
        inferenceDeploymentManager.rollingUpdate(DEPLOYMENT_ID);

        // Execute transaction synchronization callbacks manually for unit tests
        // This should throw the exception
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThatThrownBy(() -> {
            for (TransactionSynchronization sync : synchronizations) {
                sync.afterCommit();
            }
        })
                .isInstanceOf(DeploymentException.class)
                .hasMessageContaining("Rolling update failed for deployment");
    }

    @Test
    void reconcile_shouldSetUrlWhenModelStatusIndicatesReadyAndUrlIsPresent() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.PENDING);
        InferenceService service = createInferenceServiceWithStatus(
                SERVICE_NAME,
                SERVICE_URL,
                null,
                true, // hasModelStatus
                States.ActiveModelState.LOADED,
                ModelStatus.TransitionStatus.UPTODATE
        );

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        var reconcileConfig = getReconcileConfig(service);

        // When
        boolean result = inferenceDeploymentManager.reconcile(reconcileConfig);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        assertThat(result).isTrue();
        verify(deploymentRepository).conditionalUpdateInNewTransaction(
                eq(DEPLOYMENT_ID),
                any(),
                argThat(mutatorExpectingUrlAndRunning(SERVICE_URL)));
    }

    @Test
    void reconcile_shouldSetUrlWhenModelStatusIndicatesReadyAndUrlIsPresentAndClusterInternalUrlIsSetToTrue() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.PENDING);
        InferenceService service = createInferenceServiceWithStatus(
                SERVICE_NAME,
                null,
                INTERNAL_SERVICE_URL,
                true, // hasModelStatus
                States.ActiveModelState.LOADED,
                ModelStatus.TransitionStatus.UPTODATE
        );

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        var kserveDeployProperties = new KserveDeployProperties();
        kserveDeployProperties.setNamespace(NAMESPACE);
        kserveDeployProperties.setStartupTimeout(STARTUP_TIMEOUT);
        kserveDeployProperties.setUseClusterInternalUrl(true); // use internal url
        var huggingFaceProperties = new HuggingFaceProperties();
        inferenceDeploymentManager = new InferenceDeploymentManager(
                k8sClient,
                disposableResourceManager,
                manifestGenerator,
                inferenceManifestGenerator,
                containerPortResolver,
                ciliumNetworkPolicyCreator,
                nodePoolProperties,
                deploymentRepository,
                k8sKserveClient,
                kserveDeployProperties,
                huggingFaceProperties,
                inferenceTaskDetector
        );

        var reconcileConfig = getReconcileConfig(service);

        // When
        boolean result = inferenceDeploymentManager.reconcile(reconcileConfig);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        assertThat(result).isTrue();
        verify(deploymentRepository).conditionalUpdateInNewTransaction(
                eq(DEPLOYMENT_ID),
                any(),
                argThat(mutatorExpectingUrlAndRunning(INTERNAL_SERVICE_URL)));
    }

    @Test
    void reconcile_shouldNotSetUrlWhenStatusIsNull() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.NOT_DEPLOYED);
        InferenceService service = mock(InferenceService.class);
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(SERVICE_NAME);
        when(service.getMetadata()).thenReturn(metadata);
        when(service.getStatus()).thenReturn(null);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        var reconcileConfig = getReconcileConfig(service);

        // When
        boolean result = inferenceDeploymentManager.reconcile(reconcileConfig);

        // Then
        assertThat(result).isTrue();
        // Status should remain PENDING (not RUNNING) because mapStatus returns PENDING when status is null
        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.PENDING));
    }

    @Test
    void reconcile_shouldNotSetUrlWhenModelStatusIsNull() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.CRASHED);
        InferenceService service = createInferenceServiceWithStatus(
                SERVICE_NAME,
                SERVICE_URL,
                null,
                false, // no modelStatus
                null,
                null
        );

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        var reconcileConfig = getReconcileConfig(service);

        // When
        boolean result = inferenceDeploymentManager.reconcile(reconcileConfig);

        // Then
        assertThat(result).isTrue();
        // Status should remain PENDING (not RUNNING) because mapStatus returns PENDING when modelStatus is null
        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.PENDING));
    }

    @Test
    void reconcile_shouldSetNullUrlWhenModelStatusReadyButStatusUrlIsNull() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.PENDING);
        InferenceService service = createInferenceServiceWithStatus(
                SERVICE_NAME,
                null, // null URL
                null,
                true, // hasModelStatus
                States.ActiveModelState.LOADED,
                ModelStatus.TransitionStatus.UPTODATE
        );

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        var reconcileConfig = getReconcileConfig(service);

        // When
        boolean result = inferenceDeploymentManager.reconcile(reconcileConfig);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        assertThat(result).isTrue();
        verify(deploymentRepository).conditionalUpdateInNewTransaction(
                eq(DEPLOYMENT_ID),
                any(),
                argThat(mutatorExpectingUrlAndRunning(null)));
    }

    @Test
    void reconcile_shouldSetEmptyUrlWhenModelStatusReadyButUrlIsEmpty() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.PENDING);
        InferenceService service = createInferenceServiceWithStatus(
                SERVICE_NAME,
                "", // empty URL
                null,
                true, // hasModelStatus
                States.ActiveModelState.LOADED,
                ModelStatus.TransitionStatus.UPTODATE
        );

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        var reconcileConfig = getReconcileConfig(service);

        // When
        boolean result = inferenceDeploymentManager.reconcile(reconcileConfig);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        assertThat(result).isTrue();
        verify(deploymentRepository).conditionalUpdateInNewTransaction(
                eq(DEPLOYMENT_ID),
                any(),
                argThat(mutatorExpectingUrlAndRunning("")));
    }

    @Test
    void reconcile_shouldReturnStoppingStatusWhenServiceHasDeletionTimestamp() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        InferenceService service = mock(InferenceService.class);
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(SERVICE_NAME);
        metadata.setDeletionTimestamp(Instant.now().toString());
        when(service.getMetadata()).thenReturn(metadata);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        var reconcileConfig = getReconcileConfig(service);

        // When
        boolean result = inferenceDeploymentManager.reconcile(reconcileConfig);

        // Then
        assertThat(result).isTrue();
        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.STOPPING));
    }

    private ArgumentMatcher<Consumer<Deployment>> mutatorExpectingUrlAndRunning(String expectedUrl) {
        return mutator -> {
            Deployment d = createDeployment(DeploymentStatus.PENDING);
            mutator.accept(d);
            return d.getStatus() == DeploymentStatus.RUNNING && Objects.equals(expectedUrl, d.getUrl());
        };
    }

    private Deployment createDeployment(DeploymentStatus status) {
        var deployment = new InferenceDeployment();
        deployment.setId(DEPLOYMENT_ID);
        deployment.setStatus(status);
        deployment.setMetadata(new DeploymentMetadata());
        deployment.setResources(new Resources(Collections.emptyMap(), Collections.emptyMap()));
        deployment.setEnvs(List.of(
                new SimpleEnvVar("TEST_ENV", new SimpleEnvVarValue("test-value"))));
        deployment.setSource(new HuggingFaceSource("test-bucket/model"));
        deployment.setArgs(List.of("--arg1", "value1"));
        deployment.setAllowedDomains(List.of("test-domain-1", "test-domain-2"));
        deployment.setServiceName(SERVICE_NAME);
        return deployment;
    }

    private Pod createPod(String name, boolean ready) {
        var pod = new Pod();
        pod.setMetadata(new ObjectMeta());
        pod.getMetadata().setName(name);
        pod.getMetadata().setCreationTimestamp(Instant.now().toString());

        var status = new PodStatus();
        var containerStatus = new ContainerStatus();
        containerStatus.setName(CONTAINER_NAME);

        if (ready) {
            containerStatus.setState(new ContainerStateBuilder().build()); // No waiting state means ready
        } else {
            containerStatus.setState(new ContainerStateBuilder().withNewWaiting().withReason("NotReady").endWaiting().build());
        }

        status.setContainerStatuses(List.of(containerStatus));
        pod.setStatus(status);

        var spec = new PodSpec();
        var container = new Container();
        container.setName(CONTAINER_NAME);
        spec.setContainers(List.of(container));
        pod.setSpec(spec);

        return pod;
    }

    private Pod createPodWithRestartInfo(String name, int restartCount, String terminationReason, Integer exitCode, Integer signal) {
        var pod = new Pod();
        pod.setMetadata(new ObjectMeta());
        pod.getMetadata().setName(name);
        pod.getMetadata().setCreationTimestamp(Instant.now().toString());

        var status = new PodStatus();
        var containerStatus = new ContainerStatus();
        containerStatus.setName(CONTAINER_NAME);
        containerStatus.setRestartCount(restartCount);

        // Set last terminated state
        var terminatedState = new ContainerStateBuilder()
                .withNewTerminated()
                .withReason(terminationReason)
                .withExitCode(exitCode)
                .withSignal(signal)
                .withFinishedAt(Instant.now().toString())
                .endTerminated()
                .build();
        containerStatus.setLastState(terminatedState);

        // Set current state as running
        containerStatus.setState(new ContainerStateBuilder().build());

        status.setContainerStatuses(List.of(containerStatus));
        pod.setStatus(status);

        var spec = new PodSpec();
        var container = new Container();
        container.setName(CONTAINER_NAME);
        spec.setContainers(List.of(container));
        pod.setSpec(spec);

        return pod;
    }

    private Pod createPodWithMultipleContainers(String name, int restartCount1, int restartCount2) {
        var pod = new Pod();
        pod.setMetadata(new ObjectMeta());
        pod.getMetadata().setName(name);
        pod.getMetadata().setCreationTimestamp(Instant.now().toString());

        var status = new PodStatus();

        var containerStatus1 = new ContainerStatus();
        containerStatus1.setName(CONTAINER_NAME);
        containerStatus1.setRestartCount(restartCount1);
        containerStatus1.setState(new ContainerStateBuilder().build());

        var containerStatus2 = new ContainerStatus();
        containerStatus2.setName("sidecar-container");
        containerStatus2.setRestartCount(restartCount2);
        containerStatus2.setState(new ContainerStateBuilder().build());

        status.setContainerStatuses(List.of(containerStatus1, containerStatus2));
        pod.setStatus(status);

        var spec = new PodSpec();
        var container1 = new Container();
        container1.setName(CONTAINER_NAME);
        var container2 = new Container();
        container2.setName("sidecar-container");
        spec.setContainers(List.of(container1, container2));
        pod.setSpec(spec);

        return pod;
    }

    private InferenceService createInferenceServiceWithStatus(
            String serviceName,
            String url,
            String internalUrl,
            boolean hasModelStatus,
            States.ActiveModelState activeModelState,
            ModelStatus.TransitionStatus transitionStatus
    ) {
        InferenceService service = mock(InferenceService.class);
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(serviceName);
        when(service.getMetadata()).thenReturn(metadata);

        if (hasModelStatus) {
            InferenceServiceStatus status = mock(InferenceServiceStatus.class);

            Components predicate = mock(Components.class);
            when(status.getComponents()).thenReturn(Map.of("predictor", predicate));

            if (url != null) {
                when(predicate.getUrl()).thenReturn(url);
            }

            if (internalUrl != null) {
                Address address = mock(Address.class);
                when(address.getUrl()).thenReturn(internalUrl);
                when(predicate.getAddress()).thenReturn(address);
            }

            ModelStatus modelStatus = mock(ModelStatus.class);
            when(modelStatus.getTransitionStatus()).thenReturn(transitionStatus);

            States states = mock(States.class);
            when(states.getActiveModelState()).thenReturn(activeModelState);
            when(modelStatus.getStates()).thenReturn(states);

            when(status.getModelStatus()).thenReturn(modelStatus);
            when(service.getStatus()).thenReturn(status);
        } else {
            InferenceServiceStatus status = mock(InferenceServiceStatus.class);
            when(status.getModelStatus()).thenReturn(null);
            when(service.getStatus()).thenReturn(status);
        }

        return service;
    }

    private ReconcileConfig<InferenceService> getReconcileConfig(InferenceService service) {
        return ReconcileConfig.<InferenceService>builder()
                .deploymentId(DEPLOYMENT_ID)
                .service(service)
                .serviceIsMissing(false)
                .initiator("Reconciliation Test")
                .ignorePendingOnServiceNotFound(false)
                .build();
    }

    private static HuggingFaceProperties createHuggingFacePropertiesWithDefaultDomains() {
        var props = new HuggingFaceProperties();
        props.setDefaultAllowedDomains("huggingface.co,cdn.huggingface.co");
        try {
            Method init = HuggingFaceProperties.class.getDeclaredMethod("initDefaultAllowedDomains");
            init.setAccessible(true);
            init.invoke(props);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return props;
    }
}
