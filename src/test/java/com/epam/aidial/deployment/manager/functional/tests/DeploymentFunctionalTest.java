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
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentManagerProvider;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        var createdImageDef = imageDefinitionService.createImageDefinition(imageDef);
        imageDefinitionId = createdImageDef.getId();
        imageDefinitionName = imageDef.getName();
        imageDefinitionVersion = imageDef.getVersion();

        imageDefinitionService.updateBuildStatus(imageDefinitionId, ImageStatus.BUILD_SUCCESSFUL);

        Mockito.clearInvocations(securityClaimsExtractor);

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
    public void shouldSuccessfullyCreateDeployment() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var expectedEnvVars = FunctionalTestHelper.getEnvVarsWithoutK8sSecretName();

        // When
        var deployment = deploymentService.createDeployment(createDeployment);
        var deployments = deploymentService.getAllDeployments();

        // Then
        Assertions.assertEquals(1, deployments.size());
        Assertions.assertEquals(createDeployment.getDisplayName(), deployment.getDisplayName());
        Assertions.assertEquals(createDeployment.getDescription(), deployment.getDescription());
        Assertions.assertEquals(createDeployment.getImageDefinitionId(), deployment.getImageDefinitionId());
        Assertions.assertEquals(imageDefinitionName, deployment.getImageDefinitionName());
        Assertions.assertEquals(imageDefinitionVersion, deployment.getImageDefinitionVersion());
        Assertions.assertEquals(createDeployment.getMinScale(), deployment.getMinScale());
        Assertions.assertEquals(createDeployment.getMaxScale(), deployment.getMaxScale());
        Assertions.assertEquals(createDeployment.getInitialScale(), deployment.getInitialScale());
        Assertions.assertEquals(createDeployment.getResources(), deployment.getResources());
        Assertions.assertEquals(DeploymentStatus.NOT_DEPLOYED, deployment.getStatus());
        assertEnvsAreEqual(expectedEnvVars, deployment.getEnvs());
        Assertions.assertNotNull(deployment.getId());
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
        Assertions.assertEquals(extractedAuthor, savedDeployment.getAuthor());
        verify(securityClaimsExtractor).getEmail();
    }

    @Test
    public void shouldFailCreateDeploymentWhenImageNotFound() {
        // Given
        var nonExistingImageDefinitionId = UUID.randomUUID();
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        createDeployment.setImageDefinitionId(nonExistingImageDefinitionId);

        // When & Then
        var exception = assertThrows(
                EntityNotFoundException.class,
                () -> deploymentService.createDeployment(createDeployment)
        );

        Assertions.assertEquals("ImageDefinition not found: '%s'".formatted(nonExistingImageDefinitionId), exception.getMessage());
    }

    @Test
    public void shouldFailCreateDeploymentWhenDisposableResourcesFound() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        disposableResourceManager.saveKnativeServiceResource(createDeployment.getId(), "some-namespace");

        // When & Then
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> deploymentService.createDeployment(createDeployment)
        );

        var message = "Failed to create deployment with ID '%s'. There are resources awaiting deletion associated with this ID."
                .formatted(createDeployment.getId());
        Assertions.assertEquals(message, exception.getMessage());
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
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> deploymentService.createDeployment(createDeployment)
        );

        Assertions.assertEquals(
                "Env variable 'env6' should contain base64-encoded file content. Actual content: not-encoded-content",
                exception.getMessage()
        );
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
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> deploymentService.createDeployment(createDeployment)
        );

        Assertions.assertEquals(
                "Environment variable name 'PORT' is reserved and cannot be used",
                exception.getMessage()
        );
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
        Assertions.assertTrue(maybeDeployment.isPresent());
        Deployment retrievedDeployment = maybeDeployment.get();
        Assertions.assertEquals(savedDeployment.getId(), retrievedDeployment.getId());
        Assertions.assertEquals(savedDeployment.getDisplayName(), retrievedDeployment.getDisplayName());
        Assertions.assertEquals(savedDeployment.getImageDefinitionId(), retrievedDeployment.getImageDefinitionId());
        Assertions.assertEquals(imageDefinitionName, retrievedDeployment.getImageDefinitionName());
        Assertions.assertEquals(imageDefinitionVersion, retrievedDeployment.getImageDefinitionVersion());
        assertEnvsAreEqual(expectedEnvVars, retrievedDeployment.getEnvs());
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
        Assertions.assertTrue(maybeDeployment.isPresent());
        Deployment retrievedDeployment = maybeDeployment.get();
        Assertions.assertEquals(savedDeployment.getId(), retrievedDeployment.getId());
        Assertions.assertEquals(savedDeployment.getDisplayName(), retrievedDeployment.getDisplayName());
        Assertions.assertEquals(savedDeployment.getImageDefinitionId(), retrievedDeployment.getImageDefinitionId());
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
        Assertions.assertEquals("updated-deployment", updatedDeployment.getDisplayName());
        Assertions.assertEquals("Updated description", updatedDeployment.getDescription());
        Assertions.assertEquals("updated-author", updatedDeployment.getAuthor());
        Assertions.assertEquals(savedDeployment.getId(), updatedDeployment.getId());
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
        deploymentRepository.update(savedDeployment.getId(), savedDeployment);

        var updateRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        updateRequest.setDisplayName("updated-deployment");

        var updatedDeployment = deploymentService.updateDeployment(savedDeployment.getId(), updateRequest);

        // Then
        Assertions.assertEquals("updated-deployment", updatedDeployment.getDisplayName());
        Assertions.assertEquals(savedDeployment.getId(), updatedDeployment.getId());
        Assertions.assertEquals(DeploymentStatus.RUNNING, updatedDeployment.getStatus());
        Assertions.assertEquals("http://test.com", updatedDeployment.getUrl());
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

        var exception = assertThrows(
                IllegalStateException.class,
                () -> deploymentService.updateDeployment(savedDeployment.getId(), updateRequest)
        );

        // Then
        var expectedMessage = "Deployment '%s' has an intermediate status '%s'. Update is not allowed"
                .formatted(savedDeployment.getId(), DeploymentStatus.PENDING);
        Assertions.assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSuccessfullyUpdateDeploymentIfRunningAndImageChanged() {
        // Given
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        var savedDeployment = deploymentService.createDeployment(createDeployment);

        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        imageDef.setName(imageDef.getName() + "-updated");
        var createdImageDef = imageDefinitionService.createImageDefinition(imageDef);
        imageDefinitionService.updateBuildStatus(createdImageDef.getId(), ImageStatus.BUILD_SUCCESSFUL);
        var newImageDefinitionId = createdImageDef.getId();

        var mixedOperation = Mockito.mock(MixedOperation.class);
        var resource = Mockito.mock(Resource.class);
        var service = Mockito.mock(Service.class);
        var metadata = Mockito.mock(ObjectMeta.class);
        when(metadata.getAnnotations()).thenReturn(Map.of("serving.knative.dev/creator", "immutable-creator"));
        when(service.getMetadata()).thenReturn(metadata);
        when(resource.get()).thenReturn(service);
        when(mixedOperation.inNamespace(any())).thenReturn(mixedOperation);
        when(mixedOperation.withName(any())).thenReturn(resource);
        when(mixedOperation.resource(any())).thenReturn(resource);
        when(knativeClient.services()).thenReturn(mixedOperation);

        when(kubernetesClient.resources(any(Class.class))).thenReturn(mixedOperation);

        mockSecretMetaData(savedDeployment);

        // When
        savedDeployment.setStatus(DeploymentStatus.RUNNING);
        deploymentRepository.update(savedDeployment.getId(), savedDeployment);

        var updateRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        updateRequest.setImageDefinitionId(newImageDefinitionId);
        updateRequest.setDisplayName("updated-deployment");

        var updatedDeployment = deploymentService.updateDeployment(savedDeployment.getId(), updateRequest);

        // Then
        Assertions.assertEquals("updated-deployment", updatedDeployment.getDisplayName());
        Assertions.assertEquals(savedDeployment.getId(), updatedDeployment.getId());
        Assertions.assertEquals(newImageDefinitionId, updatedDeployment.getImageDefinitionId());

        verify(resource, times(1)).update();
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
        deploymentRepository.update(savedDeployment.getId(), savedDeployment);

        var allowedDomains = List.of("domain1.com", "domain2.com");

        var updateRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        updateRequest.setDisplayName("updated-deployment");
        updateRequest.setAllowedDomains(allowedDomains);

        var updatedDeployment = deploymentService.updateDeployment(savedDeployment.getId(), updateRequest);

        // Then - deployment metadata was updated in DB
        Assertions.assertEquals("updated-deployment", updatedDeployment.getDisplayName());
        Assertions.assertEquals(savedDeployment.getId(), updatedDeployment.getId());
        Assertions.assertEquals(allowedDomains, updatedDeployment.getAllowedDomains());

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
        deploymentRepository.update(savedDeployment.getId(), savedDeployment);

        var updateRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        updateRequest.setDisplayName("updated-deployment");

        var updatedDeployment = deploymentService.updateDeployment(savedDeployment.getId(), updateRequest);

        // Then
        Assertions.assertEquals("updated-deployment", updatedDeployment.getDisplayName());
        Assertions.assertEquals(savedDeployment.getId(), updatedDeployment.getId());
        Assertions.assertEquals(DeploymentStatus.STOPPING, updatedDeployment.getStatus());

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

        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        imageDef.setName(imageDef.getName() + "-updated");
        var createdImageDef = imageDefinitionService.createImageDefinition(imageDef);
        imageDefinitionService.updateBuildStatus(createdImageDef.getId(), ImageStatus.BUILD_SUCCESSFUL);
        var newImageDefinitionId = createdImageDef.getId();

        var mixedOperation = Mockito.mock(MixedOperation.class);
        var resource = Mockito.mock(Resource.class);
        var service = Mockito.mock(Service.class);
        var metadata = Mockito.mock(ObjectMeta.class);
        when(metadata.getAnnotations()).thenReturn(null);
        when(service.getMetadata()).thenReturn(metadata);
        when(resource.get()).thenReturn(service);
        when(mixedOperation.inNamespace(any())).thenReturn(mixedOperation);
        when(mixedOperation.withName(any())).thenReturn(resource);
        when(mixedOperation.resource(any())).thenReturn(resource);
        when(knativeClient.services()).thenReturn(mixedOperation);

        when(kubernetesClient.resources(any(Class.class))).thenReturn(mixedOperation);

        // When
        savedDeployment1.setStatus(DeploymentStatus.RUNNING);
        deploymentRepository.update(savedDeployment1.getId(), savedDeployment1);

        var updateRequest = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);
        updateRequest.setImageDefinitionId(newImageDefinitionId);
        updateRequest.setDisplayName("updated-deployment");

        var deploymentIds = List.of(savedDeployment1.getId(), savedDeployment2.getId());
        deploymentService.updateImageDefinitionForDeployments(newImageDefinitionId, deploymentIds);

        // Then
        // should update only once because deployment1 is active, and deployment2 isn't
        verify(resource, times(1)).update();
    }

    @Test
    public void shouldSetCreatedAndUpdatedAtOnCreate_AndOnlyUpdateUpdatedAtOnUpdate_forDeployment() throws InterruptedException {
        // Given - Creation
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);

        // When - Create
        var savedDeployment = deploymentService.createDeployment(createDeployment);

        // Then - After Create
        Assertions.assertNotNull(savedDeployment.getCreatedAt());
        Assertions.assertNotNull(savedDeployment.getUpdatedAt());
        Assertions.assertEquals(savedDeployment.getCreatedAt(), savedDeployment.getUpdatedAt());
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
        Assertions.assertEquals("updated-deployment", updatedDeployment.getDisplayName(), "Name should be updated");
        Assertions.assertEquals("Updated description", updatedDeployment.getDescription(), "Description should be updated");
        Assertions.assertEquals(savedDeployment.getId(), updatedDeployment.getId(), "ID should remain the same");
        Assertions.assertEquals(creationTime, updatedDeployment.getCreatedAt(), "CreatedAt should not change");
        Assertions.assertNotEquals(creationTime, updatedDeployment.getUpdatedAt(), "UpdatedAt should be updated to new time");
    }

    @Test
    public void shouldReturnCorrectTimestampsWhenFetchingAll_forDeployment() throws InterruptedException {
        // Given - Creation
        var createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefinitionId);

        // When - Create
        deploymentService.createDeployment(createDeployment);
        var deployments = List.copyOf(deploymentService.getAllDeployments());

        // Then - After Create
        Assertions.assertEquals(1, deployments.size(), "There should be exactly one deployment");
        var savedDeployment = deployments.getFirst();
        Assertions.assertNotNull(savedDeployment.getCreatedAt());
        Assertions.assertNotNull(savedDeployment.getUpdatedAt());
        Assertions.assertEquals(savedDeployment.getCreatedAt(), savedDeployment.getUpdatedAt());
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
        Assertions.assertEquals(1, updatedDeployments.size(), "There should still be exactly one deployment");
        var updatedDeployment = updatedDeployments.getFirst();
        Assertions.assertEquals(savedDeployment.getId(), updatedDeployment.getId(), "Deployment ID should remain unchanged");
        Assertions.assertEquals("updated-deployment", updatedDeployment.getDisplayName(), "Name should be updated");
        Assertions.assertEquals("Updated description", updatedDeployment.getDescription(), "Description should be updated");
        Assertions.assertEquals(creationTime, updatedDeployment.getCreatedAt(), "CreatedAt should remain unchanged");
        Assertions.assertNotEquals(creationTime, updatedDeployment.getUpdatedAt(), "UpdatedAt should be updated to new time");
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
        Assertions.assertTrue(maybeDeployment.isPresent());
        Assertions.assertFalse(maybeDeploymentAfterDeletion.isPresent());
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
        Assertions.assertEquals(savedDeployment.getId(), deployment.getId());
        Assertions.assertEquals(savedDeployment.getDisplayName(), deployment.getDisplayName());
        Assertions.assertEquals(savedDeployment.getImageDefinitionId(), deployment.getImageDefinitionId());
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
        deploymentRepository.update(savedDeployment.getId(), savedDeployment);

        var expectedEnvVars = FunctionalTestHelper.getEnvVarsWithoutK8sSecretName();

        mockSecretMetaData(savedDeployment);

        // When
        var deployment = deploymentService.undeploy(savedDeployment.getId());

        // Then
        assertEnvsAreEqual(expectedEnvVars, deployment.getEnvs());
        Assertions.assertEquals(savedDeployment.getId(), deployment.getId());
        Assertions.assertEquals(savedDeployment.getDisplayName(), deployment.getDisplayName());
        Assertions.assertEquals(DeploymentStatus.STOPPING, deployment.getStatus());

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
        Assertions.assertEquals(2, deployments.size());
    }

    @Test
    public void shouldSuccessfullyGetAllDeploymentsByType() {
        // Given

        // Create deployment-1 of MCP type
        var createDeployment1 = FunctionalTestHelper.createMcpDeploymentRequest(imageDefinitionId);
        createDeployment1.setDisplayName("deployment-1");
        var deployment1 = deploymentService.createDeployment(createDeployment1);

        // Create deployment-2 of Interceptor type
        var imageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        var createdImageDef = imageDefinitionService.createImageDefinition(imageDef);
        imageDefinitionService.updateBuildStatus(createdImageDef.getId(), ImageStatus.BUILD_SUCCESSFUL);

        var createDeployment2 = FunctionalTestHelper.createInterceptorDeploymentRequest(createdImageDef.getId());
        createDeployment2.setDisplayName("deployment-2");
        deploymentService.createDeployment(createDeployment2);

        // When
        var deployments = deploymentService.getAllDeploymentsByType(List.of(DeploymentTypeDto.MCP)).stream().toList();

        // Then
        Assertions.assertEquals(1, deployments.size());
        Assertions.assertEquals(deployment1.getId(), deployments.getFirst().getId());
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
        Assertions.assertNotNull(clonedDeployment.getId());
        Assertions.assertNotEquals(originalDeployment.getId(), clonedDeployment.getId());
        Assertions.assertEquals("cloned-deployment", clonedDeployment.getDisplayName());
        Assertions.assertEquals(originalDeployment.getDescription(), clonedDeployment.getDescription());
        Assertions.assertEquals(originalDeployment.getImageDefinitionId(), clonedDeployment.getImageDefinitionId());
        Assertions.assertEquals(originalDeployment.getMinScale(), clonedDeployment.getMinScale());
        Assertions.assertEquals(originalDeployment.getMaxScale(), clonedDeployment.getMaxScale());
        Assertions.assertEquals(originalDeployment.getInitialScale(), clonedDeployment.getInitialScale());
        Assertions.assertEquals(originalDeployment.getResources(), clonedDeployment.getResources());
        Assertions.assertEquals(originalDeployment.getContainerPort(), clonedDeployment.getContainerPort());
        Assertions.assertEquals(DeploymentStatus.NOT_DEPLOYED, clonedDeployment.getStatus());
        assertEnvsAreEqual(expectedEnvVars, clonedDeployment.getEnvs());
    }

    @Test
    public void shouldFailDuplicateDeploymentWhenEtalonNotFound() {
        // Given
        var nonExistingDeploymentId = String.valueOf(UUID.randomUUID());
        var newDeploymentId = String.valueOf(UUID.randomUUID());

        // When & Then
        var exception = assertThrows(
                EntityNotFoundException.class,
                () -> deploymentService.duplicateDeployment(nonExistingDeploymentId, newDeploymentId, "cloned-deployment")
        );

        Assertions.assertEquals("Etalon deployment not found by id: '%s'".formatted(nonExistingDeploymentId), exception.getMessage());
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
        Assertions.assertEquals(expectedEnvVars.size(), actualEnvVars.size());
        // Verifying auto-generated 'k8sSecretName' is not null & removing it for easier comparison
        actualEnvVars.forEach(envVar -> {
            if (envVar instanceof SensitiveEnvVar sensitiveEnvVar) {
                Assertions.assertNotNull(sensitiveEnvVar.getK8sSecretName());
                sensitiveEnvVar.setK8sSecretName(null);
            }
        });
        Assertions.assertTrue(CollectionUtils.isEqualCollection(expectedEnvVars, actualEnvVars));
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