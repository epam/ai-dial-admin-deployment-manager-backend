package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.configuration.KserveDeployProperties;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.DeploymentException;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.kserve.K8sKserveClient;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferenceDeploymentManagerTest {

    private static final String DEPLOYMENT_ID = String.valueOf(UUID.randomUUID());
    private static final int STARTUP_TIMEOUT = 60;
    private static final String NAMESPACE = "test-namespace";
    private static final String SERVICE_NAME = "service-" + DEPLOYMENT_ID;
    private static final String CONTAINER_NAME = "test-container";
    private static final String POD_NAME = "test-pod";
    private static final String SERVICE_URL = "http://service-name.test.com";
    private static final String INTERNAL_SERVICE_URL = "http://service-name.test-namespace.svc.cluster.local";
    private static final int DEFAULT_KSERVE_SERVICE_PORT = 8080;
    private static final String GENERATED_SERVICE_NAME = "dm-" + DEPLOYMENT_ID;

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

    private InferenceDeploymentManager inferenceDeploymentManager;

    @BeforeEach
    void setUp() {
        var kserveDeployProperties = new KserveDeployProperties();
        kserveDeployProperties.setNamespace(NAMESPACE);
        kserveDeployProperties.setStartupTimeout(STARTUP_TIMEOUT);
        kserveDeployProperties.setUseClusterInternalUrl(false);

        inferenceDeploymentManager = new InferenceDeploymentManager(
                k8sClient,
                disposableResourceManager,
                manifestGenerator,
                inferenceManifestGenerator,
                containerPortResolver,
                ciliumNetworkPolicyCreator,
                deploymentRepository,
                k8sKserveClient,
                kserveDeployProperties
        );

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

        when(k8sKserveClient.getServicePods(NAMESPACE, GENERATED_SERVICE_NAME)).thenReturn(podList);

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

        when(k8sKserveClient.getServicePods(NAMESPACE, GENERATED_SERVICE_NAME)).thenReturn(emptyPodList);

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

        when(k8sKserveClient.getServicePods(NAMESPACE, GENERATED_SERVICE_NAME)).thenReturn(podList);

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

        when(k8sKserveClient.getServicePods(NAMESPACE, GENERATED_SERVICE_NAME)).thenReturn(podList);

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

        when(k8sKserveClient.getServicePods(NAMESPACE, GENERATED_SERVICE_NAME)).thenReturn(podList);

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

        when(k8sKserveClient.getServicePods(NAMESPACE, GENERATED_SERVICE_NAME)).thenReturn(podList);

        // When
        var result = inferenceDeploymentManager.getInstances(DEPLOYMENT_ID);

        // Then
        assertThat(result).hasSize(1);
        var podInfo = result.getFirst();
        assertThat(podInfo.getName()).isEqualTo("pod-multi-container");
        assertThat(podInfo.getRestartCount()).isEqualTo(5); // 3 + 2
    }

    @Test
    void getContainerResource_shouldReturnContainerResourceForPod() {
        // Given
        Pod pod = createPod(POD_NAME, true);

        when(k8sKserveClient.getServicePod(NAMESPACE, GENERATED_SERVICE_NAME, POD_NAME)).thenReturn(pod);
        when(k8sClient.getPodResource(NAMESPACE, POD_NAME)).thenReturn(podResource);
        when(podResource.inContainer(CONTAINER_NAME)).thenReturn(containerResource);

        // When
        ContainerResource result = inferenceDeploymentManager.getContainerResource(DEPLOYMENT_ID, POD_NAME);

        // Then
        assertThat(result).isEqualTo(containerResource);
        verify(k8sKserveClient).getServicePod(NAMESPACE, GENERATED_SERVICE_NAME, POD_NAME);
        verify(k8sClient).getPodResource(NAMESPACE, POD_NAME);
        verify(podResource).inContainer(CONTAINER_NAME);
    }

    @Test
    void getContainerResource_shouldReturnNullWhenPodNotFound() {
        // Given
        when(k8sKserveClient.getServicePod(NAMESPACE, GENERATED_SERVICE_NAME, POD_NAME)).thenReturn(null);

        // When
        ContainerResource result = inferenceDeploymentManager.getContainerResource(DEPLOYMENT_ID, POD_NAME);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void getContainerResource_shouldThrowExceptionWhenContainerNotFound() {
        // Given
        Pod pod = new Pod();
        pod.setMetadata(new ObjectMeta());
        pod.setSpec(new PodSpec());
        pod.getSpec().setContainers(Collections.emptyList());

        when(k8sKserveClient.getServicePod(NAMESPACE, GENERATED_SERVICE_NAME, POD_NAME)).thenReturn(pod);

        // When/Then
        assertThatThrownBy(() -> inferenceDeploymentManager.getContainerResource(DEPLOYMENT_ID, POD_NAME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Container not found for pod");
    }

    @Test
    void deploy_shouldDeployInferenceService() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.STOPPED);
        InferenceService serviceSpec = new InferenceService();
        serviceSpec.setMetadata(new ObjectMeta());
        serviceSpec.getMetadata().setName(SERVICE_NAME);
        Integer containerPort = 8080;

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(containerPortResolver.resolveContainerPort(any(), eq(DEFAULT_KSERVE_SERVICE_PORT)))
                .thenReturn(containerPort);
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), anyString(), anyList())).thenReturn(ciliumNetworkPolicy);
        when(inferenceManifestGenerator.serviceConfig(eq(DEPLOYMENT_ID), any(), any(), any(), any(), any(), any(),
                any(), any(), eq(containerPort), any())).thenReturn(serviceSpec);

        // When
        Deployment result = inferenceDeploymentManager.deploy(DEPLOYMENT_ID);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        assertThat(result).isEqualTo(deployment);
        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.PENDING);

        verify(disposableResourceManager).saveInferenceServiceResource(DEPLOYMENT_ID, NAMESPACE);
        verify(k8sKserveClient).createService(eq(NAMESPACE), eq(serviceSpec));
        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.PENDING));
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
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(serviceSpec);
        doThrow(new RuntimeException("Test exception")).when(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), any());

        // When/Then
        assertThatThrownBy(() -> inferenceDeploymentManager.deploy(DEPLOYMENT_ID))
                .isInstanceOf(DeploymentException.class)
                .hasMessageContaining("Failed to deploy service");

        verify(disposableResourceManager).markInferenceServiceResourceForCleanup(DEPLOYMENT_ID, NAMESPACE);
    }

    @Test
    void undeploy_shouldUndeployInferenceService() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        deployment.setUrl(SERVICE_URL);
        DisposableResource disposableResource = mock(DisposableResource.class);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(disposableResourceManager.markInferenceServiceResourceForCleanup(DEPLOYMENT_ID, NAMESPACE))
                .thenReturn(List.of(disposableResource));

        // When
        Deployment result = inferenceDeploymentManager.undeploy(DEPLOYMENT_ID);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        assertThat(result).isEqualTo(deployment);
        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.STOPPING);

        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.STOPPING));
        verify(k8sKserveClient).deleteService(eq(NAMESPACE), eq(GENERATED_SERVICE_NAME));
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
        when(disposableResourceManager.markInferenceServiceResourceForCleanup(DEPLOYMENT_ID, NAMESPACE))
                .thenReturn(List.of(disposableResource));
        doThrow(new RuntimeException("Test exception")).when(k8sKserveClient).deleteService(NAMESPACE,
                GENERATED_SERVICE_NAME);

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
        when(inferenceManifestGenerator.serviceConfig(eq(DEPLOYMENT_ID), any(), any(), any(), any(), any(), any(),
                any(), any(), eq(containerPort), any())).thenReturn(serviceSpec);

        // When
        inferenceDeploymentManager.rollingUpdate(DEPLOYMENT_ID);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        verify(k8sKserveClient).updateService(eq(NAMESPACE), eq(serviceSpec));
        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.PENDING));
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
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(serviceSpec);
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
                GENERATED_SERVICE_NAME,
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

        // Then
        assertThat(result).isTrue();
        verify(deploymentRepository).update(eq(DEPLOYMENT_ID), argThat(updatedDeployment ->
                updatedDeployment.getStatus() == DeploymentStatus.RUNNING
                        && SERVICE_URL.equals(updatedDeployment.getUrl())
        ));
    }

    @Test
    void reconcile_shouldSetUrlWhenModelStatusIndicatesReadyAndUrlIsPresentAndClusterInternalUrlIsSetToTrue() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.PENDING);
        InferenceService service = createInferenceServiceWithStatus(
                GENERATED_SERVICE_NAME,
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
        inferenceDeploymentManager = new InferenceDeploymentManager(
                k8sClient,
                disposableResourceManager,
                manifestGenerator,
                inferenceManifestGenerator,
                containerPortResolver,
                ciliumNetworkPolicyCreator,
                deploymentRepository,
                k8sKserveClient,
                kserveDeployProperties
        );

        var reconcileConfig = getReconcileConfig(service);

        // When
        boolean result = inferenceDeploymentManager.reconcile(reconcileConfig);

        // Then
        assertThat(result).isTrue();
        verify(deploymentRepository).update(eq(DEPLOYMENT_ID), argThat(updatedDeployment ->
                updatedDeployment.getStatus() == DeploymentStatus.RUNNING
                        && INTERNAL_SERVICE_URL.equals(updatedDeployment.getUrl())
        ));
    }

    @Test
    void reconcile_shouldNotSetUrlWhenStatusIsNull() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.NOT_DEPLOYED);
        InferenceService service = mock(InferenceService.class);
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(GENERATED_SERVICE_NAME);
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
                GENERATED_SERVICE_NAME,
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
                GENERATED_SERVICE_NAME,
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

        // Then
        assertThat(result).isTrue();
        verify(deploymentRepository).update(eq(DEPLOYMENT_ID), argThat(updatedDeployment ->
                updatedDeployment.getStatus() == DeploymentStatus.RUNNING
                        && updatedDeployment.getUrl() == null
        ));
    }

    @Test
    void reconcile_shouldSetEmptyUrlWhenModelStatusReadyButUrlIsEmpty() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.PENDING);
        InferenceService service = createInferenceServiceWithStatus(
                GENERATED_SERVICE_NAME,
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

        // Then
        assertThat(result).isTrue();
        verify(deploymentRepository).update(eq(DEPLOYMENT_ID), argThat(updatedDeployment ->
                updatedDeployment.getStatus() == DeploymentStatus.RUNNING
                        && "".equals(updatedDeployment.getUrl())
        ));
    }

    @Test
    void reconcile_shouldReturnStoppingStatusWhenServiceHasDeletionTimestamp() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        InferenceService service = mock(InferenceService.class);
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(GENERATED_SERVICE_NAME);
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

    private Deployment createDeployment(DeploymentStatus status) {
        var deployment = new InferenceDeployment();
        deployment.setId(DEPLOYMENT_ID);
        deployment.setStatus(status);
        deployment.setMetadata(new DeploymentMetadata());
        deployment.setResources(new Resources(Collections.emptyMap(), Collections.emptyMap()));
        deployment.setEnvs(List.of(
                new SimpleEnvVar("TEST_ENV", new SimpleEnvVarValue("test-value"))));
        deployment.setSource(() -> "s3://test-bucket/model");
        deployment.setArgs(List.of("--arg1", "value1"));
        deployment.setAllowedDomains(List.of("test-domain-1", "test-domain-2"));
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
}
