package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.configuration.NimDeployProperties;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.DeploymentException;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.nim.K8sNimClient;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeploymentNgcRegistrySource;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.service.manifest.NimManifestGenerator;
import com.epam.aidial.deployment.manager.service.pipeline.specification.CiliumNetworkPolicyCreator;
import com.nvidia.apps.v1alpha1.NIMService;
import com.nvidia.apps.v1alpha1.NIMServiceStatus;
import com.nvidia.apps.v1alpha1.nimservicestatus.Model;
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
class NimDeploymentManagerTest {

    private static final UUID DEPLOYMENT_ID = UUID.randomUUID();
    private static final UUID IMAGE_DEFINITION_ID = UUID.randomUUID();
    private static final int STARTUP_TIMEOUT = 60;
    private static final String NAMESPACE = "test-namespace";
    private static final String SERVICE_NAME = "mcp-" + DEPLOYMENT_ID;
    private static final String CONTAINER_NAME = "test-container";
    private static final String IMAGE_NAME = "test-image:latest";
    private static final String POD_NAME = "test-pod";

    @Mock
    private DisposableResourceManager disposableResourceManager;
    @Mock
    private ManifestGenerator knativeManifestGenerator;
    @Mock
    private NimManifestGenerator nimManifestGenerator;
    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private ContainerPortResolver containerPortResolver;
    @Mock
    private CiliumNetworkPolicyCreator ciliumNetworkPolicyCreator;
    @Mock
    private K8sClient k8sClient;
    @Mock
    private K8sNimClient k8sNimClient;
    @Mock
    private PodResource podResource;
    @Mock
    private ContainerResource containerResource;
    @Mock
    private CiliumNetworkPolicy ciliumNetworkPolicy;

    private NimDeploymentManager nimDeploymentManager;

    @BeforeEach
    void setUp() {
        var nimDeployProperties = new NimDeployProperties();
        nimDeployProperties.setNamespace(NAMESPACE);
        nimDeployProperties.setStartupTimeout(STARTUP_TIMEOUT);
        nimDeployProperties.setUseClusterInternalUrl(false);

        nimDeploymentManager = new NimDeploymentManager(k8sClient, disposableResourceManager, knativeManifestGenerator,
                nimManifestGenerator, deploymentRepository, containerPortResolver, ciliumNetworkPolicyCreator,
                k8sNimClient, nimDeployProperties);

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

        when(k8sNimClient.getServicePods(NAMESPACE, SERVICE_NAME)).thenReturn(podList);

        // When
        var result = nimDeploymentManager.getActiveInstances(DEPLOYMENT_ID);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("ready-pod");
    }

    @Test
    void getActiveInstances_shouldReturnEmptyListWhenNoPodsFound() {
        // Given
        var emptyPodList = new PodList();
        emptyPodList.setItems(Collections.emptyList());

        when(k8sNimClient.getServicePods(NAMESPACE, SERVICE_NAME)).thenReturn(emptyPodList);

        // When
        var result = nimDeploymentManager.getActiveInstances(DEPLOYMENT_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getContainerResource_shouldReturnContainerResourceForPod() {
        // Given
        Pod pod = createPod(POD_NAME, true);

        when(k8sNimClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME)).thenReturn(pod);
        when(k8sClient.getPodResource(NAMESPACE, POD_NAME)).thenReturn(podResource);
        when(podResource.inContainer(CONTAINER_NAME)).thenReturn(containerResource);

        // When
        ContainerResource result = nimDeploymentManager.getContainerResource(DEPLOYMENT_ID, POD_NAME);

        // Then
        assertThat(result).isEqualTo(containerResource);
        verify(k8sNimClient).getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME);
        verify(k8sClient).getPodResource(NAMESPACE, POD_NAME);
        verify(podResource).inContainer(CONTAINER_NAME);
    }

    @Test
    void getContainerResource_shouldReturnNullWhenPodNotFound() {
        // Given
        when(k8sNimClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME)).thenReturn(null);

        // When
        ContainerResource result = nimDeploymentManager.getContainerResource(DEPLOYMENT_ID, POD_NAME);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void getContainerResource_shouldThrowExceptionWhenContainerNotFound() {
        // Given
        Pod pod = new Pod(); // Pod with no containers
        pod.setMetadata(new ObjectMeta());
        pod.setSpec(new PodSpec());
        pod.getSpec().setContainers(Collections.emptyList());

        when(k8sNimClient.getServicePod(NAMESPACE, SERVICE_NAME, POD_NAME)).thenReturn(pod);

        // When/Then
        assertThatThrownBy(() -> nimDeploymentManager.getContainerResource(DEPLOYMENT_ID, POD_NAME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Container not found for pod");
    }

    @Test
    void deploy_shouldDeployNimService() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.STOPPED);
        NIMService serviceSpec = new NIMService();
        serviceSpec.getMetadata().setName(SERVICE_NAME);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(nimManifestGenerator.serviceConfig(
                eq(DEPLOYMENT_ID.toString()),
                any(),
                any(),
                any(),
                eq(IMAGE_NAME),
                any(),
                any()
        )).thenReturn(serviceSpec);
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), anyString(), anyList())).thenReturn(ciliumNetworkPolicy);

        // When
        Deployment result = nimDeploymentManager.deploy(DEPLOYMENT_ID);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        assertThat(result).isEqualTo(deployment);
        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.PENDING);

        verify(disposableResourceManager).saveNimServiceResource(eq(DEPLOYMENT_ID), eq(NAMESPACE));
        verify(k8sNimClient).createService(eq(NAMESPACE), eq(serviceSpec));
        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.PENDING));
    }

    @Test
    void deploy_shouldReturnExistingDeploymentIfAlreadyActive() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        // When
        Deployment result = nimDeploymentManager.deploy(DEPLOYMENT_ID);

        // Then
        assertThat(result).isEqualTo(deployment);
        verify(k8sNimClient, never()).createService(anyString(), any());
        verify(deploymentRepository, never()).updateStatus(any(), any());
    }

    @Test
    void deploy_shouldThrowExceptionWhenDeploymentNotFound() {
        // Given
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> nimDeploymentManager.deploy(DEPLOYMENT_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Deployment not found");
    }

    @Test
    void deploy_shouldThrowExceptionWhenSourceIsNull() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.STOPPED);
        ((NimDeployment) deployment).setSource(null);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        // When/Then
        assertThatThrownBy(() -> nimDeploymentManager.deploy(DEPLOYMENT_ID))
                .isInstanceOf(DeploymentException.class)
                .hasMessageContaining("Failed to deploy service")
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("NIM deployment source should be NGC registry. Deployment: '%s'".formatted(DEPLOYMENT_ID));
    }

    @Test
    void deploy_shouldHandleExceptionDuringDeployment() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.STOPPED);
        NIMService serviceSpec = new NIMService();
        serviceSpec.getMetadata().setName(SERVICE_NAME);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(nimManifestGenerator.serviceConfig(
                eq(DEPLOYMENT_ID.toString()),
                any(),
                any(),
                any(),
                eq(IMAGE_NAME),
                any(),
                any()
        )).thenReturn(serviceSpec);
        when(ciliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()).thenReturn(true);
        when(ciliumNetworkPolicyCreator.create(eq(NAMESPACE), anyString(), anyString(), anyList())).thenReturn(ciliumNetworkPolicy);
        doThrow(new RuntimeException("Test exception")).when(k8sClient).createCiliumNetworkPolicy(eq(NAMESPACE), any());

        // When
        nimDeploymentManager.deploy(DEPLOYMENT_ID);

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

        verify(disposableResourceManager).markNimServiceResourceForCleanup(DEPLOYMENT_ID, NAMESPACE);
    }

    @Test
    void undeploy_shouldUndeployNimService() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        DisposableResource disposableResource = mock(DisposableResource.class);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(disposableResourceManager.markNimServiceResourceForCleanup(DEPLOYMENT_ID, NAMESPACE))
                .thenReturn(List.of(disposableResource));

        // When
        Deployment result = nimDeploymentManager.undeploy(DEPLOYMENT_ID);

        // Execute transaction synchronization callbacks manually for unit tests
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // Then
        assertThat(result).isEqualTo(deployment);
        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.STOPPING);

        verify(deploymentRepository).updateStatus(eq(DEPLOYMENT_ID), eq(DeploymentStatus.STOPPING));
        verify(k8sNimClient).deleteService(NAMESPACE, SERVICE_NAME);
        verify(disposableResourceManager).deleteAll(List.of(disposableResource));
    }

    @Test
    void undeploy_shouldReturnExistingDeploymentIfAlreadyInactive() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.STOPPED);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        // When
        Deployment result = nimDeploymentManager.undeploy(DEPLOYMENT_ID);

        // Then
        assertThat(result).isEqualTo(deployment);
        verify(k8sNimClient, never()).deleteService(anyString(), anyString());
        verify(deploymentRepository, never()).updateStatus(any(), any());
    }

    @Test
    void undeploy_shouldThrowExceptionWhenDeploymentNotFound() {
        // Given
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> nimDeploymentManager.undeploy(DEPLOYMENT_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Deployment not found");
    }

    @Test
    void undeploy_shouldHandleExceptionDuringUndeployment() {
        // Given
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        DisposableResource disposableResource = mock(DisposableResource.class);

        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(disposableResourceManager.markNimServiceResourceForCleanup(DEPLOYMENT_ID, NAMESPACE))
                .thenReturn(List.of(disposableResource));
        doThrow(new RuntimeException("Test exception")).when(k8sNimClient).deleteService(NAMESPACE, SERVICE_NAME);

        // When
        nimDeploymentManager.undeploy(DEPLOYMENT_ID);

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
    void resolveServiceUrl_shouldReturnNullWhenStatusIsNull() {
        // Given
        NIMService service = new NIMService();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(SERVICE_NAME);
        service.setMetadata(metadata);
        service.setStatus(null);
        NimDeployment deployment = (NimDeployment) createDeployment(DeploymentStatus.RUNNING);

        // When
        String result = nimDeploymentManager.resolveServiceUrl(service, deployment);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void resolveServiceUrl_shouldReturnNullWhenModelIsNull() {
        // Given
        NIMService service = new NIMService();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(SERVICE_NAME);
        service.setMetadata(metadata);
        NIMServiceStatus status = new NIMServiceStatus();
        status.setModel(null);
        service.setStatus(status);
        NimDeployment deployment = (NimDeployment) createDeployment(DeploymentStatus.RUNNING);

        // When
        String result = nimDeploymentManager.resolveServiceUrl(service, deployment);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void resolveServiceUrl_shouldReturnExternalEndpoint() {
        // Given
        NIMService service = new NIMService();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(SERVICE_NAME);
        service.setMetadata(metadata);
        NIMServiceStatus status = new NIMServiceStatus();
        Model model = new Model();
        model.setClusterEndpoint("http://cluster-internal:8000");
        model.setExternalEndpoint("http://external:8000");
        status.setModel(model);
        service.setStatus(status);
        NimDeployment deployment = (NimDeployment) createDeployment(DeploymentStatus.RUNNING);

        // When
        String result = nimDeploymentManager.resolveServiceUrl(service, deployment);

        // Then
        assertThat(result).isEqualTo("http://external:8000");
    }

    @Test
    void resolveServiceUrl_shouldReturnClusterEndpointWhenUseClusterInternalUrlIsTrue() {
        // Given
        var nimDeployProperties = new NimDeployProperties();
        nimDeployProperties.setNamespace(NAMESPACE);
        nimDeployProperties.setStartupTimeout(STARTUP_TIMEOUT);
        nimDeployProperties.setUseClusterInternalUrl(true);

        nimDeploymentManager = new NimDeploymentManager(k8sClient, disposableResourceManager,
                knativeManifestGenerator, nimManifestGenerator, deploymentRepository, containerPortResolver,
                ciliumNetworkPolicyCreator, k8sNimClient, nimDeployProperties);

        NIMService service = new NIMService();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(SERVICE_NAME);
        service.setMetadata(metadata);
        NIMServiceStatus status = new NIMServiceStatus();
        Model model = new Model();
        model.setClusterEndpoint("http://cluster-internal:8000");
        model.setExternalEndpoint("http://external:8000");
        status.setModel(model);
        service.setStatus(status);
        NimDeployment deployment = (NimDeployment) createDeployment(DeploymentStatus.RUNNING);

        // When
        String result = nimDeploymentManager.resolveServiceUrl(service, deployment);

        // Then
        assertThat(result).isEqualTo("http://cluster-internal:8000");
    }

    private Deployment createDeployment(DeploymentStatus status) {
        var deployment = new NimDeployment();
        deployment.setId(DEPLOYMENT_ID);
        deployment.setImageDefinitionId(IMAGE_DEFINITION_ID);
        deployment.setStatus(status);
        deployment.setMetadata(new DeploymentMetadata());
        deployment.setResources(new Resources(Collections.emptyMap(), Collections.emptyMap()));
        deployment.setEnvs(List.of(
                new SimpleEnvVar("TEST_ENV", new SimpleEnvVarValue("test-value"))
        ));
        deployment.setContainerGrpcPort(50052);
        deployment.setAllowedDomains(List.of("test-domain-1", "test-domain-2"));
        deployment.setSource(new NimDeploymentNgcRegistrySource(IMAGE_NAME));
        return deployment;
    }

    private Pod createPod(String name, boolean ready) {
        var pod = new Pod();
        pod.setMetadata(new ObjectMeta());
        pod.getMetadata().setName(name);
        pod.getMetadata().setCreationTimestamp(Instant.now().toString());

        var status = new PodStatus();
        var containerStatus = new ContainerStatus();

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
}