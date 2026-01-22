package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.configuration.KnativeDeployProperties;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.DeploymentException;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.knative.K8sKnativeClient;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.deployment.healthcheck.HealthCheckProvider;
import com.epam.aidial.deployment.manager.service.manifest.KnativeManifestGenerator;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.service.pipeline.specification.CiliumNetworkPolicyCreator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.cilium.v2.CiliumNetworkPolicy;
import io.fabric8.knative.serving.v1.Service;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnativeDeploymentManagerTest {

    private static final String DEPLOYMENT_ID = String.valueOf(UUID.randomUUID());
    private static final UUID IMAGE_DEFINITION_ID = UUID.randomUUID();
    private static final int STARTUP_TIMEOUT = 60;
    private static final int UNDEPLOY_TIMEOUT = 300;

    private static final String SERVICE_URL = "https://service-url.example.com";
    private static final String SERVICE_NAME = "test-" + DEPLOYMENT_ID;
    private static final String SERVICE_CONTAINER = "user-container";
    private static final String IMAGE_NAME = "test-image:latest";
    private static final String NAMESPACE = "test-namespace";
    private static final String POD_NAME = "test-pod";

    @Mock
    private DisposableResourceManager disposableResourceManager;
    @Mock
    private ManifestGenerator manifestGenerator;
    @Mock
    private KnativeManifestGenerator knativeManifestGenerator;
    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private ImageDefinitionService imageDefinitionService;
    @Mock
    private ContainerPortResolver containerPortResolver;
    @Mock
    private CiliumNetworkPolicyCreator ciliumNetworkPolicyCreator;
    @Mock
    private HealthCheckProvider healthCheckProvider;
    @Mock
    private K8sClient k8sClient;
    @Mock
    private K8sKnativeClient k8sKnativeClient;
    @Mock
    private PodResource podResource;
    @Mock
    private ContainerResource containerResource;
    @Mock
    private CiliumNetworkPolicy ciliumNetworkPolicy;

    private KnativeDeploymentManager knativeDeploymentManager;

    @BeforeAll
    static void setPrefix() {
        K8sNamingUtils.setResourceNamePrefix("test");
    }

    @AfterAll
    static void dropPrefix() {
        K8sNamingUtils.setResourceNamePrefix("");
    }

    @BeforeEach
    void setUp() {
        var knativeDeployProperties = new KnativeDeployProperties();
        knativeDeployProperties.setNamespace(NAMESPACE);
        knativeDeployProperties.setStartupTimeout(STARTUP_TIMEOUT);
        knativeDeployProperties.setUndeployTimeout(UNDEPLOY_TIMEOUT);

        knativeDeploymentManager = new KnativeDeploymentManager(
                k8sClient,
                manifestGenerator,
                knativeManifestGenerator,
                deploymentRepository,
                imageDefinitionService,
                containerPortResolver,
                disposableResourceManager,
                ciliumNetworkPolicyCreator,
                healthCheckProvider,
                k8sKnativeClient,
                knativeDeployProperties,
                SERVICE_CONTAINER
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

        // Create two pods, one ready and one not ready
        var readyPod = createPod("ready-pod", true);
        var notReadyPod = createPod("not-ready-pod", false);
        podList.setItems(List.of(readyPod, notReadyPod));

        when(k8sKnativeClient.getServicePods(NAMESPACE, SERVICE_NAME)).thenReturn(podList);

        // When
        var result = knativeDeploymentManager.getActiveInstances(DEPLOYMENT_ID);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("ready-pod");
    }

    @Test
    void getActiveInstances_shouldReturnEmptyListWhenNoPodsFound() {
        // Given
        var emptyPodList = new PodList();
        emptyPodList.setItems(Collections.emptyList());

        when(k8sKnativeClient.getServicePods(NAMESPACE, SERVICE_NAME)).thenReturn(emptyPodList);

        // When
        var result = knativeDeploymentManager.getActiveInstances(DEPLOYMENT_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getActiveInstances_shouldFilterOutPodsWithoutReadyContainer() {
        // Given
        var podList = new PodList();

        // Create a pod with container status but container name doesn't match
        var pod = createPod("pod-with-wrong-container", true);
        // Override the container name to simulate a different container
        pod.getStatus().getContainerStatuses().getFirst().setName("wrong-container-name");
        podList.setItems(List.of(pod));

        when(k8sKnativeClient.getServicePods(NAMESPACE, SERVICE_NAME)).thenReturn(podList);

        // When/Then
        assertThatThrownBy(() -> knativeDeploymentManager.getActiveInstances(DEPLOYMENT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Container " + SERVICE_CONTAINER + " is missing in service pod");
    }

    @Test
    void getInstances_shouldReturnPodsWithRestartInfo() {
        // Given
        var podList = new PodList();
        var pod = createPodWithRestartInfo("pod-with-restarts", 5, "OOMKilled", 137, 9);
        podList.setItems(List.of(pod));

        when(k8sKnativeClient.getServicePods(NAMESPACE, SERVICE_NAME)).thenReturn(podList);

        // When
        var result = knativeDeploymentManager.getInstances(DEPLOYMENT_ID);

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

        when(k8sKnativeClient.getServicePods(NAMESPACE, SERVICE_NAME)).thenReturn(podList);

        // When
        var result = knativeDeploymentManager.getInstances(DEPLOYMENT_ID);

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
    void getContainerResource_shouldReturnContainerResourceForPod() {
        // Given
        Pod pod = createPod(POD_NAME, true);

        when(k8sKnativeClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME)).thenReturn(pod);
        when(k8sClient.getPodResource(NAMESPACE, POD_NAME)).thenReturn(podResource);
        when(podResource.inContainer(SERVICE_CONTAINER)).thenReturn(containerResource);

        // When
        ContainerResource result = knativeDeploymentManager.getContainerResource(DEPLOYMENT_ID, POD_NAME);

        // Then
        assertThat(result).isEqualTo(containerResource);
        verify(k8sKnativeClient).getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME);
        verify(k8sClient).getPodResource(NAMESPACE, POD_NAME);
        verify(podResource).inContainer(SERVICE_CONTAINER);
    }

    @Test
    void getContainerResource_shouldReturnNullWhenPodNotFound() {
        // Given
        when(k8sKnativeClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME)).thenReturn(null);

        // When
        ContainerResource result = knativeDeploymentManager.getContainerResource(DEPLOYMENT_ID, POD_NAME);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void deploy_shouldDeployKnativeService() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.STOPPED);
        ImageDefinition imageDefinition = createImageDefinition();
        Service serviceSpec = new Service();
        serviceSpec.setMetadata(new ObjectMeta());
        serviceSpec.getMetadata().setName(SERVICE_NAME);
        Integer containerPort = 8080;

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(imageDefinitionService.getImageDefinition(IMAGE_DEFINITION_ID)).thenReturn(Optional.of(imageDefinition));
        when(containerPortResolver.resolveContainerPort(any(), any())).thenReturn(containerPort);
        when(knativeManifestGenerator.serviceConfig(
                eq(DEPLOYMENT_ID),
                any(),
                any(),
                any(),
                eq(IMAGE_NAME),
                eq(deployment.getInitialScale()),
                eq(deployment.getMinScale()),
                eq(deployment.getMaxScale()),
                eq(deployment.getResources()),
                eq(containerPort)
        )).thenReturn(serviceSpec);
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), anyString(), anyList())).thenReturn(ciliumNetworkPolicy);

        // When
        Deployment result = knativeDeploymentManager.deploy(DEPLOYMENT_ID);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        assertThat(result).isEqualTo(deployment);
        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.PENDING);

        verify(disposableResourceManager).saveKnativeServiceResource(DEPLOYMENT_ID, NAMESPACE);
        verify(k8sKnativeClient).createService(eq(NAMESPACE), eq(serviceSpec));
        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.PENDING));
    }

    @Test
    void deploy_shouldReturnExistingDeploymentIfAlreadyActive() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        // When
        Deployment result = knativeDeploymentManager.deploy(DEPLOYMENT_ID);

        // Then
        assertThat(result).isEqualTo(deployment);
        verify(k8sKnativeClient, never()).createService(anyString(), any());
        verify(deploymentRepository, never()).updateStatus(any(), any());
    }

    @Test
    void deploy_shouldThrowExceptionWhenDeploymentNotFound() {
        // Given
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> knativeDeploymentManager.deploy(DEPLOYMENT_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Deployment not found");
    }

    @Test
    void deploy_shouldThrowExceptionWhenImageDefinitionNotFound() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.STOPPED);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(imageDefinitionService.getImageDefinition(IMAGE_DEFINITION_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> knativeDeploymentManager.deploy(DEPLOYMENT_ID))
                .hasCauseInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Failed to deploy service");
    }

    @Test
    void deploy_shouldHandleExceptionDuringDeployment() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.STOPPED);
        ImageDefinition imageDefinition = createImageDefinition();
        Service serviceSpec = new Service();
        serviceSpec.setMetadata(new ObjectMeta());
        serviceSpec.getMetadata().setName(SERVICE_NAME);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(imageDefinitionService.getImageDefinition(IMAGE_DEFINITION_ID)).thenReturn(Optional.of(imageDefinition));
        when(containerPortResolver.resolveContainerPort(any(), any())).thenReturn(8080);
        when(knativeManifestGenerator.serviceConfig(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(serviceSpec);
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), anyString(), anyList())).thenReturn(ciliumNetworkPolicy);
        doThrow(new RuntimeException("Test exception")).when(k8sClient).createCiliumNetworkPolicy(eq(NAMESPACE), any());

        // When
        knativeDeploymentManager.deploy(DEPLOYMENT_ID);

        // Execute transaction synchronization callbacks manually for unit tests
        // This should throw the exception
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThatThrownBy(() -> {
            for (TransactionSynchronization sync : synchronizations) {
                sync.afterCommit();
            }
        })
                .isInstanceOf(DeploymentException.class)
                .hasMessageContaining("Failed to deploy service");

        verify(disposableResourceManager).markKnativeServiceResourceForCleanup(DEPLOYMENT_ID, NAMESPACE);
    }

    @Test
    void undeploy_shouldUndeployKnativeService() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        deployment.setUrl(SERVICE_URL);
        DisposableResource disposableResource = mock(DisposableResource.class);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(disposableResourceManager.markKnativeServiceResourceForCleanup(DEPLOYMENT_ID, NAMESPACE))
                .thenReturn(List.of(disposableResource));

        // When
        Deployment result = knativeDeploymentManager.undeploy(DEPLOYMENT_ID);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        assertThat(result).isEqualTo(deployment);
        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.STOPPING);

        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.STOPPING));
        verify(k8sKnativeClient).deleteServiceAndAllRunningPods(NAMESPACE, SERVICE_NAME);
        verify(disposableResourceManager).deleteAll(List.of(disposableResource));
    }

    @Test
    void undeploy_shouldReturnExistingDeploymentIfAlreadyInactive() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.STOPPED);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        // When
        Deployment result = knativeDeploymentManager.undeploy(DEPLOYMENT_ID);

        // Then
        assertThat(result).isEqualTo(deployment);
        verify(k8sKnativeClient, never()).deleteService(anyString(), anyString());
        verify(deploymentRepository, never()).updateStatus(any(), any());
    }

    @Test
    void undeploy_shouldThrowExceptionWhenDeploymentNotFound() {
        // Given
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> knativeDeploymentManager.undeploy(DEPLOYMENT_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Deployment not found");
    }

    @Test
    void undeploy_shouldHandleExceptionDuringUndeployment() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        DisposableResource disposableResource = mock(DisposableResource.class);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(disposableResourceManager.markKnativeServiceResourceForCleanup(DEPLOYMENT_ID, NAMESPACE))
                .thenReturn(List.of(disposableResource));
        doThrow(new RuntimeException("Test exception")).when(k8sKnativeClient)
                .deleteServiceAndAllRunningPods(NAMESPACE, SERVICE_NAME);

        // When
        knativeDeploymentManager.undeploy(DEPLOYMENT_ID);

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
    void undeploy_shouldDeleteServiceAndPods() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        DisposableResource disposableResource = mock(DisposableResource.class);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(disposableResourceManager.markKnativeServiceResourceForCleanup(DEPLOYMENT_ID, NAMESPACE))
                .thenReturn(List.of(disposableResource));

        // When
        Deployment result = knativeDeploymentManager.undeploy(DEPLOYMENT_ID);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        assertThat(result).isEqualTo(deployment);
        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.STOPPING);

        verify(k8sKnativeClient).deleteServiceAndAllRunningPods(NAMESPACE, SERVICE_NAME);
        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.STOPPING));
        verify(disposableResourceManager).deleteAll(List.of(disposableResource));
    }

    @Test
    void reconcile_shouldReturnStoppingStatusWhenServiceHasDeletionTimestamp() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        Service service = mock(Service.class);
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(SERVICE_NAME);
        metadata.setDeletionTimestamp(Instant.now().toString());
        when(service.getMetadata()).thenReturn(metadata);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        var reconcileConfig = ReconcileConfig.<Service>builder()
                .deploymentId(DEPLOYMENT_ID)
                .service(service)
                .serviceIsMissing(false)
                .initiator("Reconciliation Test")
                .build();

        // When
        boolean result = knativeDeploymentManager.reconcile(reconcileConfig);

        // Then
        assertThat(result).isTrue();
        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.STOPPING));
    }

    private Deployment createDeployment(DeploymentStatus status) {
        var deployment = new McpDeployment();
        deployment.setId(DEPLOYMENT_ID);
        deployment.setImageDefinitionId(IMAGE_DEFINITION_ID);
        deployment.setStatus(status);
        deployment.setMetadata(new DeploymentMetadata());
        deployment.setResources(new Resources(Collections.emptyMap(), Collections.emptyMap()));
        deployment.setEnvs(List.of(
                new SimpleEnvVar("TEST_ENV", new SimpleEnvVarValue("test-value"))
        ));
        deployment.setAllowedDomains(List.of("test-domain-1", "test-domain-2"));
        return deployment;
    }

    private ImageDefinition createImageDefinition() {
        return McpImageDefinition.builder()
                .id(IMAGE_DEFINITION_ID)
                .imageName(IMAGE_NAME)
                .version("1.0.0")
                .build();
    }

    private Pod createPod(String name, boolean ready) {
        var pod = new Pod();
        pod.setMetadata(new ObjectMeta());
        pod.getMetadata().setName(name);
        pod.getMetadata().setCreationTimestamp(Instant.now().toString());

        var status = new PodStatus();
        var containerStatus = new ContainerStatus();
        containerStatus.setName(SERVICE_CONTAINER);

        if (ready) {
            containerStatus.setState(new ContainerStateBuilder().build()); // No waiting state means ready
        } else {
            containerStatus.setState(new ContainerStateBuilder().withNewWaiting().withReason("NotReady").endWaiting().build());
        }

        status.setContainerStatuses(List.of(containerStatus));
        pod.setStatus(status);

        var spec = new PodSpec();
        var container = new Container();
        container.setName(SERVICE_CONTAINER);
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
        containerStatus.setName(SERVICE_CONTAINER);
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
        container.setName(SERVICE_CONTAINER);
        spec.setContainers(List.of(container));
        pod.setSpec(spec);

        return pod;
    }

}