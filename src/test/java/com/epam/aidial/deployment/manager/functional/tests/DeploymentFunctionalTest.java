package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.kubernetes.knative.K8sKnativeClient;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import com.epam.aidial.deployment.manager.model.EnvVarMountType;
import com.epam.aidial.deployment.manager.model.FileEnvVarValue;
import com.epam.aidial.deployment.manager.model.GenericRef;
import com.epam.aidial.deployment.manager.model.GitHubRef;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.model.McpRegistryRef;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.CreateMcpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.ImageReferenceSource;
import com.epam.aidial.deployment.manager.model.deployment.InternalImageSource;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentManagerProvider;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import com.epam.aidial.deployment.manager.web.dto.DeploymentTypeDto;
import io.cilium.v2.CiliumNetworkPolicy;
import io.fabric8.knative.client.KnativeClient;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public abstract class DeploymentFunctionalTest {

    @Autowired
    private DeploymentRepository deploymentRepository;
    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private KubernetesClient kubernetesClient;
    @Autowired
    private KnativeClient knativeClient;
    @Autowired
    private K8sKnativeClient k8sKnativeClient;
    @Autowired
    private SecurityClaimsExtractor securityClaimsExtractor;
    @Autowired
    private DeploymentManagerProvider deploymentManagerProvider;
    @Autowired
    private DisposableResourceManager disposableResourceManager;

    private ObjectMeta secretMetaData;

    private UUID imageDefinitionId;
    private String imageDefinitionName;
    private String imageDefinitionVersion;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        var imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        var createdImageDef = imageDefinitionService.createImageDefinition(imageDef);
        imageDefinitionId = createdImageDef.getId();
        imageDefinitionName = imageDef.getName();
        imageDefinitionVersion = imageDef.getVersion();

        imageDefinitionService.completeBuildSuccessfully(imageDefinitionId, "test-image", System.currentTimeMillis());

        Mockito.reset(securityClaimsExtractor);

        var mixedOperation = Mockito.mock(MixedOperation.class);
        var resource = Mockito.mock(Resource.class);
        var secret = Mockito.mock(Secret.class);
        secretMetaData = Mockito.mock(ObjectMeta.class);
        when(mixedOperation.inNamespace(any())).thenReturn(mixedOperation);
        when(mixedOperation.resource(any())).thenReturn(resource);
        when(mixedOperation.withName(any())).thenReturn(resource);
        when(resource.create()).thenReturn(secret);
        when(resource.get()).thenReturn(secret);
        when(secret.getMetadata()).thenReturn(secretMetaData);
        when(secret.getData()).thenReturn(FunctionalTestHelper.getEncodedSensitiveEnvs());
        when(secretMetaData.getName()).thenReturn("some-secret");
        when(kubernetesClient.secrets()).thenReturn(mixedOperation);
    }

    @Test
    public void shouldSuccessfullyCreateInterceptorDeployment() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var expectedEnvVars = FunctionalTestHelper.getEnvVarsWithoutK8sSecretName();

        // When
        var deployment = deploymentService.createDeployment(createDeployment);
        var deployments = deploymentService.getAllDeployments();

        // Then
        assertThat(deployments.size()).isEqualTo(1);
        assertThat(deployment.getDisplayName()).isEqualTo(createDeployment.getDisplayName());
        assertThat(deployment.getDescription()).isEqualTo(createDeployment.getDescription());
        assertThat(deployment.getSource()).isInstanceOf(InternalImageSource.class);
        var deploymentSource = (InternalImageSource) deployment.getSource();
        assertThat(deploymentSource.imageDefinitionId()).isEqualTo(((InternalImageSource) createDeployment.getSource()).imageDefinitionId());
        assertThat(deploymentSource.imageDefinitionName()).isEqualTo(imageDefinitionName);
        assertThat(deploymentSource.imageDefinitionVersion()).isEqualTo(imageDefinitionVersion);
        assertThat(deployment.getResources()).isEqualTo(createDeployment.getResources());
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.NOT_DEPLOYED);
        assertEnvsAreEqual(expectedEnvVars, deployment.getEnvs());
        assertThat(deployment.getId()).isNotNull();
        assertThat(deployment.getServiceName()).isNull();
    }

    @Test
    public void shouldSuccessfullyCreateAdapterDeployment() {
        // Given - adapter image definition
        var adapterImageDef = FunctionalTestHelper.createAdapterImageDefinition();
        var createdAdapterImageDef = imageDefinitionService.createImageDefinition(adapterImageDef);
        imageDefinitionService.completeBuildSuccessfully(createdAdapterImageDef.getId(), "test-image", System.currentTimeMillis());

        var createDeployment = FunctionalTestHelper.createAdapterDeploymentRequest(createdAdapterImageDef.getId());
        var expectedEnvVars = FunctionalTestHelper.getEnvVarsWithoutK8sSecretName();

        // When
        var deployment = deploymentService.createDeployment(createDeployment);
        var deployments = deploymentService.getAllDeployments();

        // Then
        assertThat(deployments.size()).isEqualTo(1);
        assertThat(deployment.getDisplayName()).isEqualTo(createDeployment.getDisplayName());
        assertThat(deployment.getDescription()).isEqualTo(createDeployment.getDescription());
        assertThat(deployment.getSource()).isInstanceOf(InternalImageSource.class);
        var adapterDeploymentSource = (InternalImageSource) deployment.getSource();
        assertThat(adapterDeploymentSource.imageDefinitionId()).isEqualTo(((InternalImageSource) createDeployment.getSource()).imageDefinitionId());
        assertThat(adapterDeploymentSource.imageDefinitionName()).isEqualTo(adapterImageDef.getName());
        assertThat(adapterDeploymentSource.imageDefinitionVersion()).isEqualTo(adapterImageDef.getVersion());
        assertThat(deployment.getResources()).isEqualTo(createDeployment.getResources());
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.NOT_DEPLOYED);
        assertEnvsAreEqual(expectedEnvVars, deployment.getEnvs());
        assertThat(deployment.getId()).isNotNull();
        assertThat(deployment.getServiceName()).isNull();
    }

    @Test
    public void shouldSuccessfullyCreateApplicationDeployment() {
        // Given - application image definition
        var applicationImageDef = FunctionalTestHelper.createApplicationImageDefinition();
        var createdApplicationImageDef = imageDefinitionService.createImageDefinition(applicationImageDef);
        imageDefinitionService.completeBuildSuccessfully(createdApplicationImageDef.getId(), "test-image", System.currentTimeMillis());

        var createDeployment = FunctionalTestHelper.createApplicationDeploymentRequest(createdApplicationImageDef.getId());
        var expectedEnvVars = FunctionalTestHelper.getEnvVarsWithoutK8sSecretName();

        // When
        var deployment = deploymentService.createDeployment(createDeployment);
        var deployments = deploymentService.getAllDeployments();

        // Then
        assertThat(deployments.size()).isEqualTo(1);
        assertThat(deployment.getDisplayName()).isEqualTo(createDeployment.getDisplayName());
        assertThat(deployment.getDescription()).isEqualTo(createDeployment.getDescription());
        assertThat(deployment.getSource()).isInstanceOf(InternalImageSource.class);
        var applicationDeploymentSource = (InternalImageSource) deployment.getSource();
        assertThat(applicationDeploymentSource.imageDefinitionId()).isEqualTo(((InternalImageSource) createDeployment.getSource()).imageDefinitionId());
        assertThat(applicationDeploymentSource.imageDefinitionName()).isEqualTo(applicationImageDef.getName());
        assertThat(applicationDeploymentSource.imageDefinitionVersion()).isEqualTo(applicationImageDef.getVersion());
        assertThat(deployment.getResources()).isEqualTo(createDeployment.getResources());
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.NOT_DEPLOYED);
        assertEnvsAreEqual(expectedEnvVars, deployment.getEnvs());
        assertThat(deployment.getId()).isNotNull();
        assertThat(deployment.getServiceName()).isNull();
    }

    @Test
    public void shouldSuccessfullyGetAllDeploymentsByType_whenTypeIsApplication() {
        // Given
        var mcpImageDef = FunctionalTestHelper.createMcpImageDefinition();
        var createdMcpImageDef = imageDefinitionService.createImageDefinition(mcpImageDef);
        imageDefinitionService.completeBuildSuccessfully(createdMcpImageDef.getId(), "test-image", System.currentTimeMillis());
        var createMcpDeployment = FunctionalTestHelper.createMcpDeploymentRequest(createdMcpImageDef.getId());
        createMcpDeployment.setDisplayName("mcp-deployment");
        deploymentService.createDeployment(createMcpDeployment);

        var applicationImageDef = FunctionalTestHelper.createApplicationImageDefinition();
        var createdApplicationImageDef = imageDefinitionService.createImageDefinition(applicationImageDef);
        imageDefinitionService.completeBuildSuccessfully(createdApplicationImageDef.getId(), "test-image", System.currentTimeMillis());
        var createApplicationDeployment = FunctionalTestHelper.createApplicationDeploymentRequest(createdApplicationImageDef.getId());
        createApplicationDeployment.setDisplayName("application-deployment");
        var applicationDeployment = deploymentService.createDeployment(createApplicationDeployment);

        // When
        var applicationDeployments = deploymentService.getAllDeploymentsByType(List.of(DeploymentTypeDto.APPLICATION)).stream().toList();

        // Then
        assertThat(applicationDeployments.size()).isEqualTo(1);
        assertThat(applicationDeployments.getFirst().getId()).isEqualTo(applicationDeployment.getId());
        assertThat(applicationDeployments.getFirst().getDisplayName()).isEqualTo("application-deployment");
    }

    @Test
    public void shouldExtractAuthorIfWasNotProvided() {
        // Given 1
        var extractedAuthor = "extracted-author";
        when(securityClaimsExtractor.getEmail()).thenReturn(extractedAuthor);
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        createDeployment.setAuthor(null);

        // When 1
        var savedDeployment = deploymentService.createDeployment(createDeployment);

        // Then 1
        assertThat(savedDeployment.getAuthor()).isEqualTo(extractedAuthor);
        verify(securityClaimsExtractor).getEmail();
    }

    @Test
    public void shouldFailCreateDeploymentWhenImageNotFound() {
        // Given
        var nonExistingImageDefinitionId = UUID.randomUUID();
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        createDeployment.setSource(new InternalImageSource(nonExistingImageDefinitionId, null, null, null));

        // When & Then
        assertThatThrownBy(() -> deploymentService.createDeployment(createDeployment))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("ImageDefinition not found: '%s'".formatted(nonExistingImageDefinitionId));
    }

    @Test
    public void shouldFailCreateDeploymentWhenDisposableResourcesFound() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        disposableResourceManager.saveKnativeServiceResource(createDeployment.getId(), "some-service", "some-namespace");

        // When & Then
        var message = "Failed to create deployment with ID '%s'. There are resources awaiting deletion associated with this ID."
                .formatted(createDeployment.getId());
        assertThatThrownBy(() -> deploymentService.createDeployment(createDeployment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }

    @Test
    public void shouldFailCreateDeploymentWhenSecureFileEnvVarIsNotBase64Encoded() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);

        var updatedEnv6 = new EnvVarDefinition("env6", new FileEnvVarValue("file6.json", "not-encoded-content"),
                EnvVarMountType.SECURE_FILE, "File value & secure file mount type");
        createDeployment.setMetadata(new DeploymentMetadata(new ArrayList<>(createDeployment.getMetadata().getEnvs())));
        createDeployment.getMetadata().getEnvs().remove(5);
        createDeployment.getMetadata().getEnvs().add(updatedEnv6);

        // When & Then
        assertThatThrownBy(() -> deploymentService.createDeployment(createDeployment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Env variable 'env6' should contain base64-encoded file content. Actual content: not-encoded-content");
    }

    @Test
    public void shouldFailCreateDeploymentWhenEnvNameIsReserved() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);

        var reservedEnvName = "PORT";
        var reservedEnv = new EnvVarDefinition(reservedEnvName, new FileEnvVarValue("file.json", "YmFzZTY0LWNvbnRlbnQ="),
                EnvVarMountType.CONTENT, "Reserved env name");
        createDeployment.setMetadata(new DeploymentMetadata(new ArrayList<>(createDeployment.getMetadata().getEnvs())));
        createDeployment.getMetadata().getEnvs().add(reservedEnv);

        // When & Then
        assertThatThrownBy(() -> deploymentService.createDeployment(createDeployment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Environment variable name 'PORT' is reserved and cannot be used");
    }

    @Test
    public void shouldSuccessfullyGetDeployment() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var savedDeployment = deploymentService.createDeployment(createDeployment);
        var expectedEnvVars = FunctionalTestHelper.getEnvVarsWithoutK8sSecretName();

        mockSecretMetaData(savedDeployment);

        // When
        var maybeDeployment = deploymentService.getDeployment(savedDeployment.getId());

        // Then
        assertThat(maybeDeployment.isPresent()).isTrue();
        Deployment retrievedDeployment = maybeDeployment.get();
        assertThat(retrievedDeployment.getId()).isEqualTo(savedDeployment.getId());
        assertThat(retrievedDeployment.getDisplayName()).isEqualTo(savedDeployment.getDisplayName());
        assertThat(retrievedDeployment.getSource()).isInstanceOf(InternalImageSource.class);
        var retrievedSource = (InternalImageSource) retrievedDeployment.getSource();
        assertThat(retrievedSource.imageDefinitionId()).isEqualTo(((InternalImageSource) savedDeployment.getSource()).imageDefinitionId());
        assertThat(retrievedSource.imageDefinitionName()).isEqualTo(imageDefinitionName);
        assertThat(retrievedSource.imageDefinitionVersion()).isEqualTo(imageDefinitionVersion);
        assertEnvsAreEqual(expectedEnvVars, retrievedDeployment.getEnvs());
        assertThat(retrievedDeployment.getServiceName()).isNull();
    }

    @Test
    public void shouldSuccessfullyGetDeploymentWithoutResolvedSecrets() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var savedDeployment = deploymentService.createDeployment(createDeployment);
        var expectedEnvVars = FunctionalTestHelper.getEnvVarsWithoutK8sSecretNameAndSecrets();

        // When
        var maybeDeployment = deploymentService.getDeployment(savedDeployment.getId(), false);

        // Then
        assertThat(maybeDeployment.isPresent()).isTrue();
        Deployment retrievedDeployment = maybeDeployment.get();
        assertThat(retrievedDeployment.getId()).isEqualTo(savedDeployment.getId());
        assertThat(retrievedDeployment.getDisplayName()).isEqualTo(savedDeployment.getDisplayName());
        assertThat(((InternalImageSource) retrievedDeployment.getSource()).imageDefinitionId()).isEqualTo(((InternalImageSource) savedDeployment.getSource()).imageDefinitionId());
        assertEnvsAreEqual(expectedEnvVars, retrievedDeployment.getEnvs());
    }

    @Test
    public void shouldSuccessfullyUpdateDeployment() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var savedDeployment = deploymentService.createDeployment(createDeployment);

        mockSecretMetaData(savedDeployment);

        // When
        var updateRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        updateRequest.setDisplayName("updated-deployment");
        updateRequest.setDescription("Updated description");
        updateRequest.setAuthor("updated-author");

        var updatedDeployment = deploymentService.updateDeployment(savedDeployment.getId(), updateRequest);

        // Then
        assertThat(updatedDeployment.getDisplayName()).isEqualTo("updated-deployment");
        assertThat(updatedDeployment.getDescription()).isEqualTo("Updated description");
        assertThat(updatedDeployment.getAuthor()).isEqualTo("updated-author");
        assertThat(updatedDeployment.getId()).isEqualTo(savedDeployment.getId());
    }

    @Test
    public void shouldSuccessfullyUpdateDeploymentIfRunning() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var savedDeployment = deploymentService.createDeployment(createDeployment);

        mockSecretMetaData(savedDeployment);

        // When
        savedDeployment.setStatus(DeploymentStatus.RUNNING);
        savedDeployment.setUrl("http://test.com");
        savedDeployment.setServiceName("saved-service-name");
        deploymentRepository.update(savedDeployment.getId(), savedDeployment);

        var updateRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        updateRequest.setDisplayName("updated-deployment");

        var updatedDeployment = deploymentService.updateDeployment(savedDeployment.getId(), updateRequest);

        // Then
        assertThat(updatedDeployment.getDisplayName()).isEqualTo("updated-deployment");
        assertThat(updatedDeployment.getId()).isEqualTo(savedDeployment.getId());
        assertThat(updatedDeployment.getStatus()).isEqualTo(DeploymentStatus.RUNNING);
        assertThat(updatedDeployment.getUrl()).isEqualTo("http://test.com");
        assertThat(updatedDeployment.getServiceName()).isEqualTo("saved-service-name");
    }

    @Test
    public void shouldFailUpdateDeploymentIfStatusIsPending() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var savedDeployment = deploymentService.createDeployment(createDeployment);

        mockSecretMetaData(savedDeployment);

        // When
        savedDeployment.setStatus(DeploymentStatus.PENDING);
        deploymentRepository.update(savedDeployment.getId(), savedDeployment);

        var updateRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        updateRequest.setDisplayName("updated-deployment");

        // Then
        var expectedMessage = "Deployment '%s' has an intermediate status '%s'. Update is not allowed"
                .formatted(savedDeployment.getId(), DeploymentStatus.PENDING);
        assertThatThrownBy(() -> deploymentService.updateDeployment(savedDeployment.getId(), updateRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(expectedMessage);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSuccessfullyUpdateDeploymentIfRunningAndImageChanged() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var savedDeployment = deploymentService.createDeployment(createDeployment);

        var imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        imageDef.setName(imageDef.getName() + "-updated");
        var createdImageDef = imageDefinitionService.createImageDefinition(imageDef);
        imageDefinitionService.completeBuildSuccessfully(createdImageDef.getId(), "test-image", System.currentTimeMillis());
        var newImageDefinitionId = createdImageDef.getId();

        var knativeMixedOperation = Mockito.mock(MixedOperation.class);
        var knativeServiceResource = Mockito.mock(Resource.class);
        var service = Mockito.mock(Service.class);
        var metadata = Mockito.mock(ObjectMeta.class);
        when(metadata.getAnnotations()).thenReturn(Map.of("serving.knative.dev/creator", "immutable-creator"));
        when(service.getMetadata()).thenReturn(metadata);
        when(knativeServiceResource.get()).thenReturn(service);
        when(knativeMixedOperation.inNamespace(any())).thenReturn(knativeMixedOperation);
        when(knativeMixedOperation.withName(any())).thenReturn(knativeServiceResource);
        when(knativeMixedOperation.resource(any())).thenReturn(knativeServiceResource);
        when(knativeClient.services()).thenReturn(knativeMixedOperation);

        var ciliumMixedOperation = Mockito.mock(MixedOperation.class);
        var ciliumNetworkPolicyResource = Mockito.mock(Resource.class);
        when(ciliumMixedOperation.inNamespace(any())).thenReturn(ciliumMixedOperation);
        when(ciliumMixedOperation.resource(any())).thenReturn(ciliumNetworkPolicyResource);
        when(kubernetesClient.resources(eq(CiliumNetworkPolicy.class))).thenReturn(ciliumMixedOperation);

        mockSecretMetaData(savedDeployment);

        // When
        savedDeployment.setStatus(DeploymentStatus.RUNNING);
        savedDeployment.setServiceName("some-service");
        deploymentRepository.update(savedDeployment.getId(), savedDeployment);

        var updateRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        updateRequest.setSource(new InternalImageSource(newImageDefinitionId, null, null, null));
        updateRequest.setDisplayName("updated-deployment");

        var updatedDeployment = deploymentService.updateDeployment(savedDeployment.getId(), updateRequest);

        // Then
        assertThat(updatedDeployment.getDisplayName()).isEqualTo("updated-deployment");
        assertThat(updatedDeployment.getId()).isEqualTo(savedDeployment.getId());
        assertThat(((InternalImageSource) updatedDeployment.getSource()).imageDefinitionId()).isEqualTo(newImageDefinitionId);
        assertThat(updatedDeployment.getStatus()).isEqualTo(DeploymentStatus.PENDING);

        verify(knativeServiceResource, times(1)).update();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSuccessfullyUpdateCiliumNetworkPolicyIfRunningAndDomainsWereChanged() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var savedDeployment = deploymentService.createDeployment(createDeployment);

        var knativeMixedOperation = Mockito.mock(MixedOperation.class);
        var knativeServiceResource = Mockito.mock(Resource.class);
        var service = Mockito.mock(Service.class);
        var metadata = Mockito.mock(ObjectMeta.class);
        when(metadata.getAnnotations()).thenReturn(Map.of("serving.knative.dev/creator", "immutable-creator"));
        when(service.getMetadata()).thenReturn(metadata);
        when(knativeServiceResource.get()).thenReturn(service);
        when(knativeMixedOperation.inNamespace(any())).thenReturn(knativeMixedOperation);
        when(knativeMixedOperation.withName(any())).thenReturn(knativeServiceResource);
        when(knativeMixedOperation.resource(any())).thenReturn(knativeServiceResource);
        when(knativeClient.services()).thenReturn(knativeMixedOperation);

        var ciliumMixedOperation = Mockito.mock(MixedOperation.class);
        var ciliumNetworkPolicyResource = Mockito.mock(Resource.class);
        when(ciliumMixedOperation.inNamespace(any())).thenReturn(ciliumMixedOperation);
        when(ciliumMixedOperation.resource(any())).thenReturn(ciliumNetworkPolicyResource);
        when(kubernetesClient.resources(eq(CiliumNetworkPolicy.class))).thenReturn(ciliumMixedOperation);

        mockSecretMetaData(savedDeployment);

        // When
        savedDeployment.setStatus(DeploymentStatus.RUNNING);
        savedDeployment.setServiceName("some-service");
        deploymentRepository.update(savedDeployment.getId(), savedDeployment);

        var allowedDomains = List.of("domain1.com", "domain2.com");

        var updateRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        updateRequest.setDisplayName("updated-deployment");
        updateRequest.setAllowedDomains(allowedDomains);

        var updatedDeployment = deploymentService.updateDeployment(savedDeployment.getId(), updateRequest);

        // Then - deployment metadata was updated in DB
        assertThat(updatedDeployment.getDisplayName()).isEqualTo("updated-deployment");
        assertThat(updatedDeployment.getId()).isEqualTo(savedDeployment.getId());
        assertThat(updatedDeployment.getAllowedDomains()).isEqualTo(allowedDomains);

        // Then - Cilium network policy was updated (domains change only triggers policy update)
        verify(ciliumNetworkPolicyResource, times(1)).update();

        // Then - Knative service was not updated
        verify(knativeServiceResource, never()).update();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSuccessfullyUpdateDeploymentIfFailed() throws Exception {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var savedDeployment = deploymentService.createDeployment(createDeployment);

        var mixedOperation = Mockito.mock(MixedOperation.class);
        var resource = Mockito.mock(Resource.class);
        var podList = Mockito.mock(PodList.class);

        when(mixedOperation.withGracePeriod(anyLong())).thenReturn(mixedOperation);
        when(resource.withPropagationPolicy(any())).thenReturn(mixedOperation);
        when(mixedOperation.withName(any())).thenReturn(resource);
        when(mixedOperation.inNamespace(any())).thenReturn(mixedOperation);
        when(mixedOperation.resource(any())).thenReturn(resource);
        when(knativeClient.services()).thenReturn(mixedOperation);

        when(podList.getItems()).thenReturn(List.of());
        when(mixedOperation.list()).thenReturn(podList);
        when(mixedOperation.withLabels(any(Map.class))).thenReturn(mixedOperation);
        when(kubernetesClient.pods()).thenReturn(mixedOperation);

        when(kubernetesClient.resources(any(Class.class))).thenReturn(mixedOperation);

        mockSecretMetaData(savedDeployment);

        // When
        savedDeployment.setStatus(DeploymentStatus.CRASHED);
        savedDeployment.setServiceName("some-service");
        deploymentRepository.update(savedDeployment.getId(), savedDeployment);

        var updateRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        updateRequest.setDisplayName("updated-deployment");

        var updatedDeployment = deploymentService.updateDeployment(savedDeployment.getId(), updateRequest);

        // Then
        assertThat(updatedDeployment.getDisplayName()).isEqualTo("updated-deployment");
        assertThat(updatedDeployment.getId()).isEqualTo(savedDeployment.getId());
        assertThat(updatedDeployment.getStatus()).isEqualTo(DeploymentStatus.STOPPING);

        // Manually trigger reconciliation to update status from STOPPING to STOPPED
        var deploymentToReconcile = deploymentRepository.getById(savedDeployment.getId()).orElseThrow();

        var reconcileConfig = getReconcileConfig(deploymentToReconcile.getId());
        deploymentManagerProvider.provide(deploymentToReconcile.getId()).reconcile(reconcileConfig);

        // Wait for complete stop
        waitForDeployment(savedDeployment.getId(), DeploymentStatus.STOPPED, "Undeploy timed out");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSuccessfullyChangeImageInDeployments() {
        // Given
        var createDeployment1 = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        createDeployment1.setId(createDeployment1.getId() + "1");
        createDeployment1.setDisplayName(createDeployment1.getDisplayName() + "1");
        var createDeployment2 = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        createDeployment2.setId(createDeployment2.getId() + "2");
        createDeployment2.setDisplayName(createDeployment2.getDisplayName() + "2");
        var savedDeployment1 = deploymentService.createDeployment(createDeployment1);
        var savedDeployment2 = deploymentService.createDeployment(createDeployment2);

        var imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        imageDef.setName(imageDef.getName() + "-updated");
        var createdImageDef = imageDefinitionService.createImageDefinition(imageDef);
        imageDefinitionService.completeBuildSuccessfully(createdImageDef.getId(), "test-image", System.currentTimeMillis());
        var newImageDefinitionId = createdImageDef.getId();

        var knativeMixedOperation = Mockito.mock(MixedOperation.class);
        var knativeServiceResource = Mockito.mock(Resource.class);
        var service = Mockito.mock(Service.class);
        var metadata = Mockito.mock(ObjectMeta.class);
        when(metadata.getAnnotations()).thenReturn(null);
        when(service.getMetadata()).thenReturn(metadata);
        when(knativeServiceResource.get()).thenReturn(service);
        when(knativeMixedOperation.inNamespace(any())).thenReturn(knativeMixedOperation);
        when(knativeMixedOperation.withName(any())).thenReturn(knativeServiceResource);
        when(knativeMixedOperation.resource(any())).thenReturn(knativeServiceResource);
        when(knativeClient.services()).thenReturn(knativeMixedOperation);

        var ciliumMixedOperation = Mockito.mock(MixedOperation.class);
        var ciliumNetworkPolicyResource = Mockito.mock(Resource.class);
        when(ciliumMixedOperation.inNamespace(any())).thenReturn(ciliumMixedOperation);
        when(ciliumMixedOperation.resource(any())).thenReturn(ciliumNetworkPolicyResource);
        when(kubernetesClient.resources(eq(CiliumNetworkPolicy.class))).thenReturn(ciliumMixedOperation);

        // When
        savedDeployment1.setStatus(DeploymentStatus.RUNNING);
        savedDeployment1.setServiceName("some-service-1");
        deploymentRepository.update(savedDeployment1.getId(), savedDeployment1);

        var updateRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        updateRequest.setSource(new InternalImageSource(newImageDefinitionId, null, null, null));
        updateRequest.setDisplayName("updated-deployment");

        var deploymentIds = List.of(savedDeployment1.getId(), savedDeployment2.getId());
        deploymentService.updateImageDefinitionForDeployments(newImageDefinitionId, deploymentIds);

        // Then
        // should update only once because deployment1 is active, and deployment2 isn't
        verify(knativeServiceResource, times(1)).update();
    }

    @Test
    public void shouldSetCreatedAndUpdatedAtOnCreate_AndOnlyUpdateUpdatedAtOnUpdate_forDeployment() throws InterruptedException {
        // Given - Creation
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);

        // When - Create
        var savedDeployment = deploymentService.createDeployment(createDeployment);

        // Then - After Create
        assertThat(savedDeployment.getCreatedAt()).isNotNull();
        assertThat(savedDeployment.getUpdatedAt()).isNotNull();
        assertThat(savedDeployment.getUpdatedAt()).isEqualTo(savedDeployment.getCreatedAt());
        var creationTime = savedDeployment.getCreatedAt();

        // Given - Update same entity at a later time
        var updateRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        updateRequest.setDisplayName("updated-deployment");
        updateRequest.setDescription("Updated description");

        mockSecretMetaData(savedDeployment);

        // When - Update
        Thread.sleep(1); // to be sure that created_at not equals to updated_at
        var updatedDeployment = deploymentService.updateDeployment(savedDeployment.getId(), updateRequest);

        // Then - After Update
        assertThat(updatedDeployment.getDisplayName()).as("Name should be updated").isEqualTo("updated-deployment");
        assertThat(updatedDeployment.getDescription()).as("Description should be updated").isEqualTo("Updated description");
        assertThat(updatedDeployment.getId()).as("ID should remain the same").isEqualTo(savedDeployment.getId());
        assertThat(updatedDeployment.getCreatedAt()).as("CreatedAt should not change").isEqualTo(creationTime);
        assertThat(updatedDeployment.getUpdatedAt()).as("UpdatedAt should be updated to new time").isNotEqualTo(creationTime);
    }

    @Test
    public void shouldReturnCorrectTimestampsWhenFetchingAll_forDeployment() throws InterruptedException {
        // Given - Creation
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);

        // When - Create
        deploymentService.createDeployment(createDeployment);
        var deployments = List.copyOf(deploymentService.getAllDeployments());

        // Then - After Create
        assertThat(deployments.size()).as("There should be exactly one deployment").isEqualTo(1);
        var savedDeployment = deployments.getFirst();
        assertThat(savedDeployment.getCreatedAt()).isNotNull();
        assertThat(savedDeployment.getUpdatedAt()).isNotNull();
        assertThat(savedDeployment.getUpdatedAt()).isEqualTo(savedDeployment.getCreatedAt());
        var creationTime = savedDeployment.getCreatedAt();

        mockSecretMetaData(savedDeployment);

        // Given - Update
        var updateRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        updateRequest.setDisplayName("updated-deployment");
        updateRequest.setDescription("Updated description");

        // When - Update
        Thread.sleep(1); // to be sure that created_at not equals to updated_at
        deploymentService.updateDeployment(savedDeployment.getId(), updateRequest);
        var updatedDeployments = List.copyOf(deploymentService.getAllDeployments());

        // Then - After Update
        assertThat(updatedDeployments.size()).as("There should still be exactly one deployment").isEqualTo(1);
        var updatedDeployment = updatedDeployments.getFirst();
        assertThat(updatedDeployment.getId()).as("Deployment ID should remain unchanged").isEqualTo(savedDeployment.getId());
        assertThat(updatedDeployment.getDisplayName()).as("Name should be updated").isEqualTo("updated-deployment");
        assertThat(updatedDeployment.getDescription()).as("Description should be updated").isEqualTo("Updated description");
        assertThat(updatedDeployment.getCreatedAt()).as("CreatedAt should remain unchanged").isEqualTo(creationTime);
        assertThat(updatedDeployment.getUpdatedAt()).as("UpdatedAt should be updated to new time").isNotEqualTo(creationTime);
    }

    @Test
    public void shouldSuccessfullyDeleteDeployment() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var savedDeployment = deploymentService.createDeployment(createDeployment);

        mockSecretMetaData(savedDeployment);

        // When
        var maybeDeployment = deploymentService.getDeployment(savedDeployment.getId());
        deploymentService.deleteDeployment(savedDeployment.getId());
        var maybeDeploymentAfterDeletion = deploymentService.getDeployment(savedDeployment.getId());

        // Then
        assertThat(maybeDeployment.isPresent()).isTrue();
        assertThat(maybeDeploymentAfterDeletion.isPresent()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldSuccessfullyDeployDeployment() {
        // Given
        var mixedOperation = Mockito.mock(MixedOperation.class);
        var resource = Mockito.mock(Resource.class);
        when(mixedOperation.inNamespace(any())).thenReturn(mixedOperation);
        when(mixedOperation.resource(any())).thenReturn(resource);
        when(knativeClient.services()).thenReturn(mixedOperation);

        var policy = Mockito.mock(CiliumNetworkPolicy.class);
        when(resource.create()).thenReturn(policy);
        when(kubernetesClient.resources(any(Class.class))).thenReturn(mixedOperation);

        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var savedDeployment = deploymentService.createDeployment(createDeployment);
        var expectedEnvVars = FunctionalTestHelper.getEnvVarsWithoutK8sSecretName();

        mockSecretMetaData(savedDeployment);

        // When
        var deployment = deploymentService.deploy(savedDeployment.getId());

        // Then
        assertThat(deployment.getId()).isEqualTo(savedDeployment.getId());
        assertThat(deployment.getDisplayName()).isEqualTo(savedDeployment.getDisplayName());
        assertThat(deployment.getServiceName()).isEqualTo(K8sNamingUtils.generateName(savedDeployment.getId()));
        assertThat(((InternalImageSource) deployment.getSource()).imageDefinitionId()).isEqualTo(((InternalImageSource) savedDeployment.getSource()).imageDefinitionId());
        assertEnvsAreEqual(expectedEnvVars, deployment.getEnvs());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldSuccessfullyUndeployDeployment() throws Exception {
        // Given
        var mixedOperation = Mockito.mock(MixedOperation.class);
        var resource = Mockito.mock(Resource.class);
        var podList = Mockito.mock(PodList.class);

        when(mixedOperation.withGracePeriod(anyLong())).thenReturn(mixedOperation);
        when(resource.withPropagationPolicy(any())).thenReturn(mixedOperation);
        when(mixedOperation.withName(any())).thenReturn(resource);
        when(mixedOperation.inNamespace(any())).thenReturn(mixedOperation);
        when(mixedOperation.resource(any())).thenReturn(resource);
        when(knativeClient.services()).thenReturn(mixedOperation);

        when(podList.getItems()).thenReturn(List.of());
        when(mixedOperation.list()).thenReturn(podList);
        when(mixedOperation.withLabels(any(Map.class))).thenReturn(mixedOperation);
        when(kubernetesClient.pods()).thenReturn(mixedOperation);

        when(kubernetesClient.resources(any(Class.class))).thenReturn(mixedOperation);

        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var savedDeployment = deploymentService.createDeployment(createDeployment);

        savedDeployment.setStatus(DeploymentStatus.RUNNING);
        savedDeployment.setServiceName("some-service");
        deploymentRepository.update(savedDeployment.getId(), savedDeployment);

        var expectedEnvVars = FunctionalTestHelper.getEnvVarsWithoutK8sSecretName();

        mockSecretMetaData(savedDeployment);

        // When
        var deployment = deploymentService.undeploy(savedDeployment.getId());

        // Then
        assertEnvsAreEqual(expectedEnvVars, deployment.getEnvs());
        assertThat(deployment.getId()).isEqualTo(savedDeployment.getId());
        assertThat(deployment.getDisplayName()).isEqualTo(savedDeployment.getDisplayName());
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.STOPPING);

        // Manually trigger reconciliation to update status from STOPPING to STOPPED
        var deploymentToReconcile = deploymentRepository.getById(savedDeployment.getId()).orElseThrow();

        var reconcileConfig = getReconcileConfig(deploymentToReconcile.getId());
        deploymentManagerProvider.provide(deploymentToReconcile.getId()).reconcile(reconcileConfig);

        // Wait for complete stop
        waitForDeployment(savedDeployment.getId(), DeploymentStatus.STOPPED, "Undeploy timed out");
    }

    @Test
    public void shouldSuccessfullyGetAllDeploymentsByImageDefinitionId() {
        // Given
        var createDeployment1 = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        createDeployment1.setId("deployment-1");
        createDeployment1.setDisplayName("deployment-1");
        deploymentService.createDeployment(createDeployment1);

        var createDeployment2 = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        createDeployment2.setId("deployment-2");
        createDeployment2.setDisplayName("deployment-2");
        deploymentService.createDeployment(createDeployment2);

        // When
        var deployments = deploymentService.getAllDeployments(imageDefinitionId);

        // Then
        assertThat(deployments.size()).isEqualTo(2);
    }

    @Test
    public void shouldSuccessfullyGetAllDeploymentsByType_whenTypeIsMcp() {
        // Given

        // Create deployment-1 of MCP type
        var mcpImageDef = FunctionalTestHelper.createMcpImageDefinition();
        var createdMcpImageDef = imageDefinitionService.createImageDefinition(mcpImageDef);
        imageDefinitionService.completeBuildSuccessfully(createdMcpImageDef.getId(), "test-image", System.currentTimeMillis());
        var createDeployment1 = FunctionalTestHelper.createMcpDeploymentRequest(createdMcpImageDef.getId());
        createDeployment1.setDisplayName("deployment-1");
        var deployment1 = deploymentService.createDeployment(createDeployment1);

        // Create deployment-2 of Interceptor type (use the interceptor image from setUp)
        var createDeployment2 = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        createDeployment2.setDisplayName("deployment-2");
        deploymentService.createDeployment(createDeployment2);

        // When
        var deployments = deploymentService.getAllDeploymentsByType(List.of(DeploymentTypeDto.MCP)).stream().toList();

        // Then
        assertThat(deployments.size()).isEqualTo(1);
        assertThat(deployments.getFirst().getId()).isEqualTo(deployment1.getId());
    }

    @Test
    public void shouldSuccessfullyGetAllDeploymentsByType_whenTypeIsAdapter() {
        // Given
        var mcpImageDef = FunctionalTestHelper.createMcpImageDefinition();
        var createdMcpImageDef = imageDefinitionService.createImageDefinition(mcpImageDef);
        imageDefinitionService.completeBuildSuccessfully(createdMcpImageDef.getId(), "test-image", System.currentTimeMillis());
        var createMcpDeployment = FunctionalTestHelper.createMcpDeploymentRequest(createdMcpImageDef.getId());
        createMcpDeployment.setDisplayName("mcp-deployment");
        deploymentService.createDeployment(createMcpDeployment);

        var adapterImageDef = FunctionalTestHelper.createAdapterImageDefinition();
        var createdAdapterImageDef = imageDefinitionService.createImageDefinition(adapterImageDef);
        imageDefinitionService.completeBuildSuccessfully(createdAdapterImageDef.getId(), "test-image", System.currentTimeMillis());
        var createAdapterDeployment = FunctionalTestHelper.createAdapterDeploymentRequest(createdAdapterImageDef.getId());
        createAdapterDeployment.setDisplayName("adapter-deployment");
        var adapterDeployment = deploymentService.createDeployment(createAdapterDeployment);

        // When
        var adapterDeployments = deploymentService.getAllDeploymentsByType(List.of(DeploymentTypeDto.ADAPTER)).stream().toList();

        // Then
        assertThat(adapterDeployments.size()).isEqualTo(1);
        assertThat(adapterDeployments.getFirst().getId()).isEqualTo(adapterDeployment.getId());
        assertThat(adapterDeployments.getFirst().getDisplayName()).isEqualTo("adapter-deployment");
    }

    @Test
    public void shouldSuccessfullyDuplicateDeployment() {
        // Given
        var newDeploymentId = String.valueOf(UUID.randomUUID());

        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        createDeployment.setDisplayName("original-deployment");
        createDeployment.setDescription("Original description");
        var originalDeployment = deploymentService.createDeployment(createDeployment);
        var expectedEnvVars = FunctionalTestHelper.getEnvVarsWithoutK8sSecretName();

        mockSecretMetaData(originalDeployment);

        // When
        var clonedDeployment = deploymentService.duplicateDeployment(
                originalDeployment.getId(),
                newDeploymentId,
                "cloned-deployment"
        );

        // Then
        assertThat(clonedDeployment.getId()).isNotNull();
        assertThat(clonedDeployment.getId()).isNotEqualTo(originalDeployment.getId());
        assertThat(clonedDeployment.getDisplayName()).isEqualTo("cloned-deployment");
        assertThat(clonedDeployment.getDescription()).isEqualTo(originalDeployment.getDescription());
        assertThat(((InternalImageSource) clonedDeployment.getSource()).imageDefinitionId()).isEqualTo(((InternalImageSource) originalDeployment.getSource()).imageDefinitionId());
        assertThat(clonedDeployment.getResources()).isEqualTo(originalDeployment.getResources());
        assertThat(clonedDeployment.getContainerPort()).isEqualTo(originalDeployment.getContainerPort());
        assertThat(clonedDeployment.getStatus()).isEqualTo(DeploymentStatus.NOT_DEPLOYED);
        assertThat(clonedDeployment.getServiceName()).isNull();
        assertEnvsAreEqual(expectedEnvVars, clonedDeployment.getEnvs());
    }

    @Test
    public void shouldFailDuplicateDeploymentWhenEtalonNotFound() {
        // Given
        var nonExistingDeploymentId = String.valueOf(UUID.randomUUID());
        var newDeploymentId = String.valueOf(UUID.randomUUID());

        // When & Then
        assertThatThrownBy(() -> deploymentService.duplicateDeployment(nonExistingDeploymentId, newDeploymentId, "cloned-deployment"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Etalon deployment not found by id: '%s'".formatted(nonExistingDeploymentId));
    }

    @Test
    public void shouldFailCreateDeploymentWhenImageTypeDoesNotMatchDeploymentType_byId() {
        // Given — setUp created an Interceptor image; use it with an MCP deployment
        var createDeployment = FunctionalTestHelper.createMcpDeploymentRequest(imageDefinitionId);

        // When & Then
        assertThatThrownBy(() -> deploymentService.createDeployment(createDeployment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot use image of type 'INTERCEPTOR'")
                .hasMessageContaining("expected 'MCP'");
    }

    @Test
    public void shouldFailCreateDeploymentWhenImageTypeDoesNotMatchDeploymentType_byTypeNameVersion() {
        // Given — MCP deployment referencing the interceptor image by (type, name, version)
        var createDeployment = FunctionalTestHelper.createMcpDeploymentRequest(null);
        createDeployment.setSource(new InternalImageSource(
                null, ImageType.INTERCEPTOR, imageDefinitionName, imageDefinitionVersion));

        // When & Then
        assertThatThrownBy(() -> deploymentService.createDeployment(createDeployment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot use image of type 'INTERCEPTOR'")
                .hasMessageContaining("expected 'MCP'");
    }

    @Test
    public void shouldFailCreateDeploymentWhenDeclaredImageTypeConflictsWithDeploymentType() {
        // Given — declared type is INTERCEPTOR but deployment is MCP; rejected before any DB lookup
        var createDeployment = FunctionalTestHelper.createMcpDeploymentRequest(null);
        createDeployment.setSource(new InternalImageSource(
                UUID.randomUUID(), ImageType.INTERCEPTOR, null, null));

        // When & Then
        assertThatThrownBy(() -> deploymentService.createDeployment(createDeployment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot use image of type 'INTERCEPTOR'")
                .hasMessageContaining("expected 'MCP'");
    }

    @Test
    public void shouldSuccessfullyCreateDeploymentWhenImageTypeMatchesDeploymentType() {
        // Given — matching MCP image + MCP deployment
        var mcpImageDef = FunctionalTestHelper.createMcpImageDefinition();
        var createdMcpImageDef = imageDefinitionService.createImageDefinition(mcpImageDef);
        imageDefinitionService.completeBuildSuccessfully(createdMcpImageDef.getId(), "test-image", System.currentTimeMillis());
        var createDeployment = FunctionalTestHelper.createMcpDeploymentRequest(createdMcpImageDef.getId());

        // When
        var deployment = deploymentService.createDeployment(createDeployment);

        // Then
        assertThat(deployment.getId()).isNotNull();
        assertThat(((InternalImageSource) deployment.getSource()).imageDefinitionType()).isEqualTo(ImageType.MCP);
    }

    @Test
    public void shouldFailChangeImageInDeploymentsWhenImageTypeDoesNotMatchDeploymentType() {
        // Given — an interceptor deployment and a new MCP image
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var savedDeployment = deploymentService.createDeployment(createDeployment);

        var mcpImageDef = FunctionalTestHelper.createMcpImageDefinition();
        var createdMcpImageDef = imageDefinitionService.createImageDefinition(mcpImageDef);
        imageDefinitionService.completeBuildSuccessfully(createdMcpImageDef.getId(), "test-image", System.currentTimeMillis());

        // When & Then — bulk change-image must reject the mismatch
        assertThatThrownBy(() -> deploymentService.updateImageDefinitionForDeployments(
                createdMcpImageDef.getId(), List.of(savedDeployment.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot use image of type 'MCP'")
                .hasMessageContaining("expected 'INTERCEPTOR'");
    }

    private void mockSecretMetaData(Deployment deployment) {
        AtomicReference<String> secretName = new AtomicReference<>();
        deployment.getEnvs().stream()
                .filter(envVar -> envVar instanceof SensitiveEnvVar)
                .findFirst()
                .ifPresent(envVar -> secretName.set(((SensitiveEnvVar) envVar).getK8sSecretName()));
        when(secretMetaData.getName()).thenReturn(secretName.get());
    }

    private void assertEnvsAreEqual(List<EnvVar> expectedEnvVars, List<EnvVar> actualEnvVars) {
        assertThat(actualEnvVars.size()).isEqualTo(expectedEnvVars.size());
        // Verifying auto-generated 'k8sSecretName' is not null & removing it for easier comparison
        actualEnvVars.forEach(envVar -> {
            if (envVar instanceof SensitiveEnvVar sensitiveEnvVar) {
                assertThat(sensitiveEnvVar.getK8sSecretName()).isNotNull();
                sensitiveEnvVar.setK8sSecretName(null);
            }
        });
        assertThat(CollectionUtils.isEqualCollection(expectedEnvVars, actualEnvVars)).isTrue();
    }

    private void waitForDeployment(String deploymentId, DeploymentStatus expectedStatus, String errorMessage) throws Exception {
        long timeoutMs = 20 * 1000; // 20 seconds
        long pollIntervalMs = 1000; // 1 seconds
        long deployStartTime = System.currentTimeMillis();
        while (true) {
            var maybeDeployment = deploymentService.getDeployment(deploymentId);
            if (maybeDeployment.isPresent() && maybeDeployment.get().getStatus().equals(expectedStatus)) {
                break;
            }
            if (System.currentTimeMillis() - deployStartTime > timeoutMs) {
                throw new IllegalStateException(errorMessage);
            }
            Thread.sleep(pollIntervalMs);
        }
    }

    @Test
    public void shouldCreateDeploymentWithMcpRegistryRef() {
        var createDeployment = CreateMcpDeployment.builder()
                .id("deploy-mcp-ref")
                .source(new ImageReferenceSource("registry.test/image:1.0", new McpRegistryRef("my-server", "1.0.0")))
                .displayName("Deployment with MCP ref")
                .resources(new Resources())
                .metadata(new DeploymentMetadata())
                .allowedDomains(List.of())
                .build();

        var deployment = deploymentService.createDeployment(createDeployment);
        var fetched = deploymentService.getDeployment(deployment.getId()).orElseThrow();

        assertThat(fetched.getSource()).isInstanceOf(ImageReferenceSource.class);
        var source = (ImageReferenceSource) fetched.getSource();
        assertThat(source.imageReference()).isEqualTo("registry.test/image:1.0");
        assertThat(source.externalRegistryRef()).isInstanceOf(McpRegistryRef.class);
        assertThat(((McpRegistryRef) source.externalRegistryRef()).packageName()).isEqualTo("my-server");
    }

    @Test
    public void shouldCreateDeploymentWithGenericRef() {
        var createDeployment = CreateMcpDeployment.builder()
                .id("deploy-generic-ref")
                .source(new ImageReferenceSource("registry.test/image:1.0", new GenericRef("https://example.com/pkg")))
                .displayName("Deployment with generic ref")
                .resources(new Resources())
                .metadata(new DeploymentMetadata())
                .allowedDomains(List.of())
                .build();

        var deployment = deploymentService.createDeployment(createDeployment);
        var fetched = deploymentService.getDeployment(deployment.getId()).orElseThrow();

        var source = (ImageReferenceSource) fetched.getSource();
        assertThat(source.externalRegistryRef()).isInstanceOf(GenericRef.class);
        assertThat(((GenericRef) source.externalRegistryRef()).url()).isEqualTo("https://example.com/pkg");
    }

    @Test
    public void shouldCreateDeploymentWithoutExternalRef() {
        var createDeployment = CreateMcpDeployment.builder()
                .id("deploy-no-ref")
                .source(new ImageReferenceSource("registry.test/image:1.0", null))
                .displayName("Deployment without ref")
                .resources(new Resources())
                .metadata(new DeploymentMetadata())
                .allowedDomains(List.of())
                .build();

        var deployment = deploymentService.createDeployment(createDeployment);
        var fetched = deploymentService.getDeployment(deployment.getId()).orElseThrow();

        var source = (ImageReferenceSource) fetched.getSource();
        assertThat(source.externalRegistryRef()).isNull();
    }

    @Test
    public void shouldUpdateDeploymentExternalRef() {
        var createDeployment = CreateMcpDeployment.builder()
                .id("deploy-update-ref")
                .source(new ImageReferenceSource("registry.test/image:1.0", new McpRegistryRef("old-pkg", "1.0.0")))
                .displayName("Deployment to update ref")
                .resources(new Resources())
                .metadata(new DeploymentMetadata())
                .allowedDomains(List.of())
                .build();

        deploymentService.createDeployment(createDeployment);

        var updateRequest = CreateMcpDeployment.builder()
                .id("deploy-update-ref")
                .source(new ImageReferenceSource("registry.test/image:1.0", new GitHubRef("new/repo")))
                .displayName("Deployment to update ref")
                .resources(new Resources())
                .metadata(new DeploymentMetadata())
                .allowedDomains(List.of())
                .build();
        deploymentService.updateDeployment("deploy-update-ref", updateRequest);

        var fetched = deploymentService.getDeployment("deploy-update-ref").orElseThrow();
        var source = (ImageReferenceSource) fetched.getSource();
        assertThat(source.externalRegistryRef()).isInstanceOf(GitHubRef.class);
        assertThat(((GitHubRef) source.externalRegistryRef()).repo()).isEqualTo("new/repo");
    }

    @Test
    public void shouldClearDeploymentExternalRef() {
        var createDeployment = CreateMcpDeployment.builder()
                .id("deploy-clear-ref")
                .source(new ImageReferenceSource("registry.test/image:1.0", new McpRegistryRef("my-pkg", "1.0.0")))
                .displayName("Deployment to clear ref")
                .resources(new Resources())
                .metadata(new DeploymentMetadata())
                .allowedDomains(List.of())
                .build();

        deploymentService.createDeployment(createDeployment);

        var clearRequest = CreateMcpDeployment.builder()
                .id("deploy-clear-ref")
                .source(new ImageReferenceSource("registry.test/image:1.0", null))
                .displayName("Deployment to clear ref")
                .resources(new Resources())
                .metadata(new DeploymentMetadata())
                .allowedDomains(List.of())
                .build();
        deploymentService.updateDeployment("deploy-clear-ref", clearRequest);

        var fetched = deploymentService.getDeployment("deploy-clear-ref").orElseThrow();
        var source = (ImageReferenceSource) fetched.getSource();
        assertThat(source.externalRegistryRef()).isNull();
    }

    @Test
    public void shouldListDeployments_withMixedExternalRefs() {
        var dep1 = CreateMcpDeployment.builder()
                .id("deploy-list-ref")
                .source(new ImageReferenceSource("registry.test/img1:1.0", new McpRegistryRef("pkg-1", "1.0.0")))
                .displayName("Dep with ref")
                .resources(new Resources())
                .metadata(new DeploymentMetadata())
                .allowedDomains(List.of())
                .build();

        var dep2 = CreateMcpDeployment.builder()
                .id("deploy-list-noref")
                .source(new ImageReferenceSource("registry.test/img2:1.0", null))
                .displayName("Dep without ref")
                .resources(new Resources())
                .metadata(new DeploymentMetadata())
                .allowedDomains(List.of())
                .build();

        deploymentService.createDeployment(dep1);
        deploymentService.createDeployment(dep2);

        var allDeployments = deploymentService.getAllDeployments();
        // Filter to only ImageReferenceSource deployments (BeforeEach creates one with InternalImageSource)
        var imageRefDeployments = allDeployments.stream()
                .filter(d -> d.getSource() instanceof ImageReferenceSource)
                .toList();
        assertThat(imageRefDeployments.size()).isEqualTo(2);

        for (var dep : imageRefDeployments) {
            var source = (ImageReferenceSource) dep.getSource();
            if ("deploy-list-ref".equals(dep.getId())) {
                assertThat(source.externalRegistryRef()).isInstanceOf(McpRegistryRef.class);
            } else {
                assertThat(source.externalRegistryRef()).isNull();
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private ReconcileConfig getReconcileConfig(String deploymentId) {
        return ReconcileConfig.<Service>builder()
                .deploymentId(deploymentId)
                .service(null)
                .serviceIsMissing(true)
                .initiator("DeploymentFunctionalTest")
                .ignorePendingOnServiceNotFound(true)
                .build();
    }
}