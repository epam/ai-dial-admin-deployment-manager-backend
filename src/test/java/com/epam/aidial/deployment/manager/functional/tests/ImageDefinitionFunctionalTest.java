package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.dao.repository.ImageDefinitionRepository;
import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.GenericRef;
import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
import com.epam.aidial.deployment.manager.model.GitHubRef;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageDefinitionViewElement;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.McpRegistryRef;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public abstract class ImageDefinitionFunctionalTest {

    @Autowired
    private ImageDefinitionService service;
    @Autowired
    private ImageDefinitionRepository repository;
    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private KubernetesClient kubernetesClient;
    @Autowired
    private SecurityClaimsExtractor securityClaimsExtractor;

    @BeforeEach
    void setUp() {
        Mockito.clearInvocations(securityClaimsExtractor);
    }

    @Test
    public void shouldSuccessfullyCreateImageDefinition() {
        // Given
        ImageDefinition imageDef = FunctionalTestHelper.createMcpImageDefinition();

        // When
        service.createImageDefinition(imageDef);
        List<ImageDefinition> imageDefs = service.getAllImageDefinitions().stream().toList();

        // Then
        Assertions.assertEquals(1, imageDefs.size());

        ImageDefinition actualImageDef = imageDefs.getFirst();
        imageDef.setId(actualImageDef.getId());
        imageDef.setBuildStatus(ImageStatus.NOT_BUILT);
        Assertions.assertEquals(imageDef, actualImageDef);
    }

    @Test
    public void shouldExtractAuthorIfWasNotProvided() {
        // Given
        var extractedAuthor = "extracted-author";
        when(securityClaimsExtractor.getEmail()).thenReturn(extractedAuthor);
        ImageDefinition imageDef = FunctionalTestHelper.createMcpImageDefinition();
        imageDef.setAuthor(null);

        // When
        var createdImageDef = service.createImageDefinition(imageDef);

        // Then
        Assertions.assertEquals(extractedAuthor, createdImageDef.getAuthor());
        verify(securityClaimsExtractor).getEmail();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldSuccessfullyCreateAndDeleteImageDefinition() {
        // Given
        var mixedOperation = Mockito.mock(MixedOperation.class);
        var resource = Mockito.mock(Resource.class);
        var secret = Mockito.mock(Secret.class);
        when(mixedOperation.inNamespace(any())).thenReturn(mixedOperation);
        when(mixedOperation.resource(any())).thenReturn(resource);
        when(resource.create()).thenReturn(secret);
        when(kubernetesClient.secrets()).thenReturn(mixedOperation);

        ImageDefinition imageDef = FunctionalTestHelper.createMcpImageDefinition();

        // When 1
        service.createImageDefinition(imageDef);
        List<ImageDefinition> imageDefs = service.getAllImageDefinitions().stream().toList();

        // Then 1
        Assertions.assertEquals(1, imageDefs.size());
        UUID imageDefId = imageDefs.getFirst().getId();

        // When 2
        service.updateBuildStatus(imageDefId, ImageStatus.BUILD_SUCCESSFUL);
        CreateDeployment createDeployment = FunctionalTestHelper.createInterceptorDeploymentRequest(imageDefId);
        Deployment deployment = deploymentService.createDeployment(createDeployment);

        service.deleteImageDefinitionSync(imageDefId);
        imageDefs = service.getAllImageDefinitions().stream().toList();
        boolean isImageDefPresent = service.getImageDefinition(imageDefId).isPresent();
        boolean isDeploymentPresent = deploymentService.getDeployment(deployment.getId()).isPresent();

        // Then 2
        Assertions.assertTrue(imageDefs.isEmpty());
        Assertions.assertFalse(isImageDefPresent);
        Assertions.assertFalse(isDeploymentPresent);
    }

    @Test
    public void shouldSuccessfullyCreateAndUpdateImageDefinition() {
        // Given
        ImageDefinition imageDef = FunctionalTestHelper.createMcpImageDefinition();

        // When 1
        service.createImageDefinition(imageDef);
        List<ImageDefinition> imageDefs = service.getAllImageDefinitions().stream().toList();

        // Then 1
        Assertions.assertEquals(1, imageDefs.size());
        imageDef = imageDefs.getFirst();

        // When 2
        imageDef.setName(imageDef.getName() + "1");
        imageDef.setSource(new GitDockerfileImageSource("http://test-uri", "some-branch", "*", "", List.of("entry2"), null));
        imageDef.setAuthor("updated-author");

        service.updateImageDefinition(imageDef.getId(), imageDef);
        imageDefs = service.getAllImageDefinitions().stream().toList();

        // Then 2
        Assertions.assertEquals(imageDef, imageDefs.getFirst());
    }

    @Test
    public void shouldSetCreatedAndUpdatedAtOnCreate_AndOnlyUpdateUpdatedAtOnUpdate() throws InterruptedException {
        // Given - Creation
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();

        // When - Create
        var createdImageDef = service.createImageDefinition(imageDef);

        // Then - After Create
        Assertions.assertNotNull(createdImageDef.getCreatedAt());
        Assertions.assertNotNull(createdImageDef.getUpdatedAt());
        Assertions.assertEquals(createdImageDef.getCreatedAt(), createdImageDef.getUpdatedAt());
        var creationTime = createdImageDef.getCreatedAt();

        // Given - Update same entity at a later time
        createdImageDef.setName(createdImageDef.getName() + "1");
        createdImageDef.setSource(new GitDockerfileImageSource(
                "http://test-uri", "some-branch", "*", "", List.of("entry2"), null
        ));

        // When - Update
        Thread.sleep(1); // to be sure that created_at not equals to updated_at
        var updatedImageDef = service.updateImageDefinition(createdImageDef.getId(), createdImageDef);

        // Then - After Update
        Assertions.assertEquals(createdImageDef, updatedImageDef, "Updated object should match input");
        Assertions.assertEquals(creationTime, updatedImageDef.getCreatedAt(), "CreatedAt should not change");
        Assertions.assertNotEquals(creationTime, updatedImageDef.getUpdatedAt(), "UpdatedAt should reflect new time");
    }

    @Test
    public void shouldReturnCorrectTimestampsWhenFetchingAll() throws InterruptedException {
        // Given - Creation
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();

        // When - Create
        service.createImageDefinition(imageDef);
        var createdImageDefs = List.copyOf(service.getAllImageDefinitions());

        // Then - After Create
        Assertions.assertEquals(1, createdImageDefs.size());
        var createdImageDef = createdImageDefs.getFirst();
        Assertions.assertNotNull(createdImageDef.getCreatedAt());
        Assertions.assertNotNull(createdImageDef.getUpdatedAt());
        Assertions.assertEquals(createdImageDef.getCreatedAt(), createdImageDef.getUpdatedAt());
        var creationTime = createdImageDef.getCreatedAt();

        // Given - Update
        createdImageDef.setName(createdImageDef.getName() + "1");
        createdImageDef.setSource(new GitDockerfileImageSource(
                "http://test-uri", "some-branch", "*", "", List.of("entry2"), null
        ));

        // When - Update
        Thread.sleep(1); // to be sure that created_at not equals to updated_at
        service.updateImageDefinition(createdImageDef.getId(), createdImageDef);
        var updatedImageDefs = List.copyOf(service.getAllImageDefinitions());

        // Then - After Update
        Assertions.assertEquals(1, updatedImageDefs.size());
        var updatedImageDef = updatedImageDefs.getFirst();
        Assertions.assertEquals(createdImageDef, updatedImageDef, "Fetched entity should match updated one");
        Assertions.assertEquals(creationTime, updatedImageDef.getCreatedAt(), "CreatedAt should remain unchanged");
        Assertions.assertNotEquals(creationTime, updatedImageDef.getUpdatedAt(), "UpdatedAt should be updated");
    }

    @Test
    public void shouldSuccessfullyCreateAndGetImageDefinitionByType() {
        // Given
        ImageDefinition mcpImageDef = FunctionalTestHelper.createMcpImageDefinition();
        ImageDefinition interceptorImageDef = FunctionalTestHelper.createInterceptorImageDefinition();

        // When
        service.createImageDefinition(mcpImageDef);
        service.createImageDefinition(interceptorImageDef);
        List<ImageDefinition> imageDefs = service.getAllImageDefinitionsByType(ImageType.MCP).stream().toList();

        // Then
        Assertions.assertEquals(1, imageDefs.size());

        ImageDefinition actualImageDef = imageDefs.getFirst();
        mcpImageDef.setId(actualImageDef.getId());
        mcpImageDef.setBuildStatus(ImageStatus.NOT_BUILT);
        Assertions.assertEquals(mcpImageDef, actualImageDef);
    }

    @Test
    public void shouldSuccessfullyCreateAndGetImageDefinitionsByNameAndType() {
        // Given
        ImageDefinition mcpImageDef = FunctionalTestHelper.createMcpImageDefinition();
        ImageDefinition interceptorImageDef1 = FunctionalTestHelper.createInterceptorImageDefinition();
        ImageDefinition interceptorImageDef2 = FunctionalTestHelper.createInterceptorImageDefinition();

        String name = "test-interceptor-1";
        interceptorImageDef1.setName(name);
        interceptorImageDef1.setVersion("1.0.0");
        interceptorImageDef2.setName(name);
        interceptorImageDef2.setVersion("2.0.0");

        // When
        service.createImageDefinition(mcpImageDef);
        service.createImageDefinition(interceptorImageDef1);
        service.createImageDefinition(interceptorImageDef2);
        var imageDefs = service.getAllImageDefinitionsByNameAndType(name, ImageType.INTERCEPTOR).stream()
                .collect(Collectors.toMap(ImageDefinition::getVersion, def -> def));

        // Then
        Assertions.assertEquals(2, imageDefs.size());

        ImageDefinition actualImageDef = imageDefs.get(interceptorImageDef2.getVersion());
        interceptorImageDef2.setId(actualImageDef.getId());
        interceptorImageDef2.setBuildStatus(ImageStatus.NOT_BUILT);
        Assertions.assertEquals(interceptorImageDef2, actualImageDef);
    }

    @Test
    public void shouldSuccessfullyCreateAndGetImageDefinitionsByName() {
        // Given
        ImageDefinition interceptorImageDef1 = FunctionalTestHelper.createInterceptorImageDefinition();
        ImageDefinition interceptorImageDef2 = FunctionalTestHelper.createInterceptorImageDefinition();
        ImageDefinition interceptorImageDef3 = FunctionalTestHelper.createInterceptorImageDefinition();

        String name = "test-interceptor-1";
        interceptorImageDef1.setName(name);
        interceptorImageDef1.setVersion("1.0.0");
        interceptorImageDef2.setName(name);
        interceptorImageDef2.setVersion("2.0.0");
        interceptorImageDef3.setName(name + "-0");

        // When
        service.createImageDefinition(interceptorImageDef1);
        service.createImageDefinition(interceptorImageDef2);
        service.createImageDefinition(interceptorImageDef3);
        List<ImageDefinition> imageDefs = service.getAllImageDefinitionsByName(name).stream().toList();

        // Then
        Assertions.assertEquals(2, imageDefs.size());
    }

    @Test
    public void shouldSuccessfullyCreateMcpImageDefinitionWithMcp() {
        // Given
        McpImageDefinition imageDef = FunctionalTestHelper.createMcpImageDefinition();

        // When
        service.createImageDefinition(imageDef);
        List<ImageDefinition> imageDefs = service.getAllImageDefinitions().stream().toList();

        // Then
        Assertions.assertEquals(1, imageDefs.size());

        ImageDefinition actualImageDef = imageDefs.getFirst();
        imageDef.setId(actualImageDef.getId());
        imageDef.setBuildStatus(ImageStatus.NOT_BUILT);
        Assertions.assertEquals(imageDef, actualImageDef);
    }

    @Test
    public void shouldSuccessfullyCreateAdapterImageDefinition() {
        // Given
        ImageDefinition imageDef = FunctionalTestHelper.createAdapterImageDefinition();

        // When
        service.createImageDefinition(imageDef);
        List<ImageDefinition> imageDefs = service.getAllImageDefinitions().stream().toList();

        // Then
        Assertions.assertEquals(1, imageDefs.size());

        ImageDefinition actualImageDef = imageDefs.getFirst();
        imageDef.setId(actualImageDef.getId());
        imageDef.setBuildStatus(ImageStatus.NOT_BUILT);
        Assertions.assertEquals(imageDef, actualImageDef);
    }

    @Test
    public void shouldSuccessfullyCreateAndGetAdapterImageDefinitionByType() {
        // Given
        ImageDefinition adapterImageDef = FunctionalTestHelper.createAdapterImageDefinition();
        ImageDefinition interceptorImageDef = FunctionalTestHelper.createInterceptorImageDefinition();

        // When
        service.createImageDefinition(adapterImageDef);
        service.createImageDefinition(interceptorImageDef);
        List<ImageDefinition> imageDefs = service.getAllImageDefinitionsByType(ImageType.ADAPTER).stream().toList();

        // Then
        Assertions.assertEquals(1, imageDefs.size());

        ImageDefinition actualImageDef = imageDefs.getFirst();
        adapterImageDef.setId(actualImageDef.getId());
        adapterImageDef.setBuildStatus(ImageStatus.NOT_BUILT);
        Assertions.assertEquals(adapterImageDef, actualImageDef);
    }

    @Test
    public void shouldSuccessfullyCreateAndGetAdapterImageDefinitionsByNameAndType() {
        // Given
        ImageDefinition adapterImageDef1 = FunctionalTestHelper.createAdapterImageDefinition();
        ImageDefinition adapterImageDef2 = FunctionalTestHelper.createAdapterImageDefinition();
        ImageDefinition interceptorImageDef = FunctionalTestHelper.createInterceptorImageDefinition();

        String name = "test-adapter-1";
        adapterImageDef1.setName(name);
        adapterImageDef1.setVersion("1.0.0");
        adapterImageDef2.setName(name);
        adapterImageDef2.setVersion("2.0.0");

        // When
        service.createImageDefinition(interceptorImageDef);
        service.createImageDefinition(adapterImageDef1);
        service.createImageDefinition(adapterImageDef2);
        var imageDefs = service.getAllImageDefinitionsByNameAndType(name, ImageType.ADAPTER).stream()
                .collect(Collectors.toMap(ImageDefinition::getVersion, def -> def));

        // Then
        Assertions.assertEquals(2, imageDefs.size());

        ImageDefinition actualImageDef1 = imageDefs.get(adapterImageDef1.getVersion());
        adapterImageDef1.setId(actualImageDef1.getId());
        Assertions.assertEquals(adapterImageDef1, actualImageDef1);

        ImageDefinition actualImageDef2 = imageDefs.get(adapterImageDef2.getVersion());
        adapterImageDef2.setId(actualImageDef2.getId());
        Assertions.assertEquals(adapterImageDef2, actualImageDef2);
    }

    @Test
    public void shouldSuccessfullyCreateApplicationImageDefinition() {
        // Given
        ImageDefinition imageDef = FunctionalTestHelper.createApplicationImageDefinition();

        // When
        service.createImageDefinition(imageDef);
        List<ImageDefinition> imageDefs = service.getAllImageDefinitions().stream().toList();

        // Then
        Assertions.assertEquals(1, imageDefs.size());

        ImageDefinition actualImageDef = imageDefs.getFirst();
        imageDef.setId(actualImageDef.getId());
        imageDef.setBuildStatus(ImageStatus.NOT_BUILT);
        Assertions.assertEquals(imageDef, actualImageDef);
    }

    @Test
    public void shouldSuccessfullyCreateAndGetApplicationImageDefinitionByType() {
        // Given
        ImageDefinition applicationImageDef = FunctionalTestHelper.createApplicationImageDefinition();
        ImageDefinition interceptorImageDef = FunctionalTestHelper.createInterceptorImageDefinition();

        // When
        service.createImageDefinition(applicationImageDef);
        service.createImageDefinition(interceptorImageDef);
        List<ImageDefinition> imageDefs = service.getAllImageDefinitionsByType(ImageType.APPLICATION).stream().toList();

        // Then
        Assertions.assertEquals(1, imageDefs.size());

        ImageDefinition actualImageDef = imageDefs.getFirst();
        applicationImageDef.setId(actualImageDef.getId());
        applicationImageDef.setBuildStatus(ImageStatus.NOT_BUILT);
        Assertions.assertEquals(applicationImageDef, actualImageDef);
    }

    @Test
    public void shouldGetAdapterImageDefinitionViewsByType() {
        // Given
        var adapterImageDef1 = FunctionalTestHelper.createAdapterImageDefinition();
        adapterImageDef1.setName("adapter-model");
        adapterImageDef1.setVersion("1.0.0");

        var adapterImageDef2 = FunctionalTestHelper.createAdapterImageDefinition();
        adapterImageDef2.setName("adapter-model");
        adapterImageDef2.setVersion("1.1.0");

        var interceptorImageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        interceptorImageDef.setName("interceptor-model");
        interceptorImageDef.setVersion("1.0.0");

        // When
        var createdAdapter1 = service.createImageDefinition(adapterImageDef1);
        var createdAdapter2 = service.createImageDefinition(adapterImageDef2);
        var createdInterceptor = service.createImageDefinition(interceptorImageDef);

        service.updateBuildStatus(createdAdapter1.getId(), ImageStatus.BUILD_SUCCESSFUL);

        var adapterViews = service.getImageDefinitionViewsByType(ImageType.ADAPTER).stream().toList();
        var interceptorViews = service.getImageDefinitionViewsByType(ImageType.INTERCEPTOR).stream().toList();

        // Then
        Assertions.assertEquals(1, adapterViews.size());
        var adapterView = adapterViews.getFirst();
        Assertions.assertEquals("adapter-model", adapterView.getName());
        Assertions.assertEquals(2, adapterView.getAvailableVersions().size());
        Assertions.assertEquals(createdAdapter1.getId(), adapterView.getSelectedId());

        Assertions.assertEquals(1, interceptorViews.size());
        var interceptorView = interceptorViews.getFirst();
        Assertions.assertEquals("interceptor-model", interceptorView.getName());
        Assertions.assertEquals(1, interceptorView.getAvailableVersions().size());
        Assertions.assertEquals(createdInterceptor.getId(), interceptorView.getSelectedId());

        List<UUID> adapterVersionIds = adapterViews.stream()
                .flatMap(v -> v.getAvailableVersions().stream())
                .map(ImageDefinitionViewElement::getId)
                .toList();

        Assertions.assertTrue(adapterVersionIds.contains(createdAdapter1.getId()));
        Assertions.assertTrue(adapterVersionIds.contains(createdAdapter2.getId()));
        Assertions.assertFalse(adapterVersionIds.contains(createdInterceptor.getId()));
    }

    @Test
    public void shouldSuccessfullyAddBuildLogsWithinLimit() {
        // Given
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();

        // When
        var createdImageDef = service.createImageDefinition(imageDef);
        var id = createdImageDef.getId();

        service.addBuildLog(id, "Log 1");
        service.addBuildLogs(id, List.of("Log 2", "Log 3"));
        service.addBuildLog(id, "Log 4");

        var imageDefWithLogs = service.getImageDefinition(id).orElseThrow();

        // Then
        var buildLogs = imageDefWithLogs.getBuildLogs();
        Assertions.assertEquals(3, buildLogs.size()); // limit is overriden in @TestPropertySource
        Assertions.assertTrue(buildLogs.contains("Log 4"));
        Assertions.assertFalse(buildLogs.contains("Log 1"));
    }

    @Test
    public void shouldGetImageDefinitionViews() {
        // Given
        var imageDef0 = FunctionalTestHelper.createMcpImageDefinition();
        imageDef0.setName("test-model");
        imageDef0.setVersion("0.0.1");
        imageDef0.setDescription("Version 0.0.1");

        var imageDef1 = FunctionalTestHelper.createMcpImageDefinition();
        imageDef1.setName("test-model");
        imageDef1.setVersion("1.0.0");
        imageDef1.setDescription("Version 1.0.0");

        var imageDef2 = FunctionalTestHelper.createMcpImageDefinition();
        imageDef2.setName("test-model");
        imageDef2.setVersion("1.1.0");
        imageDef2.setDescription("Version 1.1.0");

        var imageDef3 = FunctionalTestHelper.createMcpImageDefinition();
        imageDef3.setName("another-model");
        imageDef3.setVersion("1.0.0");
        imageDef3.setDescription("Another model 1");

        var imageDef4 = FunctionalTestHelper.createMcpImageDefinition();
        imageDef4.setName("another-model");
        imageDef4.setVersion("2.0.0");
        imageDef4.setDescription("Another model 2");

        // When
        var created0 = service.createImageDefinition(imageDef0);
        var created1 = service.createImageDefinition(imageDef1);
        var created4 = service.createImageDefinition(imageDef4);
        service.createImageDefinition(imageDef2);
        service.createImageDefinition(imageDef3);

        service.updateBuildStatus(created0.getId(), ImageStatus.BUILD_SUCCESSFUL);
        service.updateBuildStatus(created1.getId(), ImageStatus.BUILD_SUCCESSFUL);

        // When
        var views = service.getImageDefinitionViews().stream().toList();

        // Then
        Assertions.assertEquals(2, views.size());

        var testModelView = views.stream()
                .filter(v -> v.getName().equals("test-model"))
                .findFirst()
                .orElseThrow();

        Assertions.assertEquals(created1.getId(), testModelView.getSelectedId());
        Assertions.assertEquals(3, testModelView.getAvailableVersions().size());

        var anotherModelView = views.stream()
                .filter(v -> v.getName().equals("another-model"))
                .findFirst()
                .orElseThrow();

        Assertions.assertEquals(2, anotherModelView.getAvailableVersions().size());
        Assertions.assertEquals(created4.getId(), anotherModelView.getSelectedId());
    }

    @Test
    public void shouldGetImageDefinitionViewsByType() {
        // Given
        var mcpImageDef1 = FunctionalTestHelper.createMcpImageDefinition();
        mcpImageDef1.setName("mcp-model");
        mcpImageDef1.setVersion("1.0.0");

        var mcpImageDef2 = FunctionalTestHelper.createMcpImageDefinition();
        mcpImageDef2.setName("mcp-model");
        mcpImageDef2.setVersion("1.1.0");

        var interceptorImageDef = FunctionalTestHelper.createInterceptorImageDefinition();
        interceptorImageDef.setName("interceptor-model");
        interceptorImageDef.setVersion("1.0.0");

        // When
        var createdMcp1 = service.createImageDefinition(mcpImageDef1);
        var createdMcp2 = service.createImageDefinition(mcpImageDef2);
        var createdInterceptor = service.createImageDefinition(interceptorImageDef);

        service.updateBuildStatus(createdMcp1.getId(), ImageStatus.BUILD_SUCCESSFUL);

        // When
        var mcpViews = service.getImageDefinitionViewsByType(ImageType.MCP).stream().toList();
        var interceptorViews = service.getImageDefinitionViewsByType(ImageType.INTERCEPTOR).stream().toList();

        // Then
        Assertions.assertEquals(1, mcpViews.size());
        var mcpView = mcpViews.getFirst();
        Assertions.assertEquals("mcp-model", mcpView.getName());
        Assertions.assertEquals(2, mcpView.getAvailableVersions().size());
        Assertions.assertEquals(createdMcp1.getId(), mcpView.getSelectedId());

        Assertions.assertEquals(1, interceptorViews.size());
        var interceptorView = interceptorViews.getFirst();
        Assertions.assertEquals("interceptor-model", interceptorView.getName());
        Assertions.assertEquals(1, interceptorView.getAvailableVersions().size());
        Assertions.assertEquals(createdInterceptor.getId(), interceptorView.getSelectedId());

        List<UUID> mcpVersionIds = mcpViews.stream()
                .flatMap(v -> v.getAvailableVersions().stream())
                .map(ImageDefinitionViewElement::getId)
                .toList();

        Assertions.assertTrue(mcpVersionIds.contains(createdMcp1.getId()));
        Assertions.assertTrue(mcpVersionIds.contains(createdMcp2.getId()));
        Assertions.assertFalse(mcpVersionIds.contains(createdInterceptor.getId()));
    }

    @Test
    public void shouldCreateImageDefinitionWithMcpRegistryRef() {
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        var source = new GitDockerfileImageSource("http://test-uri", "main", null, null, List.of("entry"), null);
        source.setExternalRegistryRef(new McpRegistryRef("my-server", null));
        imageDef.setSource(source);

        var created = service.createImageDefinition(imageDef);
        var fetched = service.getImageDefinition(created.getId()).orElseThrow();

        Assertions.assertInstanceOf(GitDockerfileImageSource.class, fetched.getSource());
        var fetchedSource = (GitDockerfileImageSource) fetched.getSource();
        Assertions.assertInstanceOf(McpRegistryRef.class, fetchedSource.getExternalRegistryRef());
        Assertions.assertEquals("my-server", ((McpRegistryRef) fetchedSource.getExternalRegistryRef()).packageName());
    }

    @Test
    public void shouldCreateImageDefinitionWithVersionedMcpRegistryRef() {
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        var source = new DockerImageSource("http://test-uri", List.of("entry1"), new McpRegistryRef("github/github", "2025.4.1"));
        imageDef.setSource(source);

        var created = service.createImageDefinition(imageDef);
        var fetched = service.getImageDefinition(created.getId()).orElseThrow();

        var fetchedSource = (DockerImageSource) fetched.getSource();
        Assertions.assertInstanceOf(McpRegistryRef.class, fetchedSource.getExternalRegistryRef());
        var ref = (McpRegistryRef) fetchedSource.getExternalRegistryRef();
        Assertions.assertEquals("github/github", ref.packageName());
        Assertions.assertEquals("2025.4.1", ref.version());
    }

    @Test
    public void shouldCreateImageDefinitionWithMcpRegistryRefWithoutVersion() {
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        var source = new DockerImageSource("http://test-uri", List.of("entry1"), new McpRegistryRef("my-server", null));
        imageDef.setSource(source);

        var created = service.createImageDefinition(imageDef);
        var fetched = service.getImageDefinition(created.getId()).orElseThrow();

        var fetchedSource = (DockerImageSource) fetched.getSource();
        var ref = (McpRegistryRef) fetchedSource.getExternalRegistryRef();
        Assertions.assertEquals("my-server", ref.packageName());
        Assertions.assertNull(ref.version());
    }

    @Test
    public void shouldUpdateImageDefinitionMcpRegistryRefVersion() {
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        var source = new DockerImageSource("http://test-uri", List.of("entry1"), new McpRegistryRef("my-server", "1.0.0"));
        imageDef.setSource(source);

        var created = service.createImageDefinition(imageDef);

        created.setSource(new DockerImageSource("http://test-uri", List.of("entry1"), new McpRegistryRef("my-server", "2.0.0")));
        service.updateImageDefinition(created.getId(), created);

        var fetched = service.getImageDefinition(created.getId()).orElseThrow();
        var ref = (McpRegistryRef) ((DockerImageSource) fetched.getSource()).getExternalRegistryRef();
        Assertions.assertEquals("my-server", ref.packageName());
        Assertions.assertEquals("2.0.0", ref.version());
    }

    @Test
    public void shouldClearImageDefinitionMcpRegistryRefVersion() {
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        var source = new DockerImageSource("http://test-uri", List.of("entry1"), new McpRegistryRef("my-server", "1.0.0"));
        imageDef.setSource(source);

        var created = service.createImageDefinition(imageDef);

        created.setSource(new DockerImageSource("http://test-uri", List.of("entry1"), new McpRegistryRef("my-server", null)));
        service.updateImageDefinition(created.getId(), created);

        var fetched = service.getImageDefinition(created.getId()).orElseThrow();
        var ref = (McpRegistryRef) ((DockerImageSource) fetched.getSource()).getExternalRegistryRef();
        Assertions.assertEquals("my-server", ref.packageName());
        Assertions.assertNull(ref.version());
    }

    @Test
    public void shouldCreateImageDefinitionWithGitHubRef() {
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        var source = new DockerImageSource("http://test-uri", List.of("entry1"), new GitHubRef("org/repo"));
        imageDef.setSource(source);

        var created = service.createImageDefinition(imageDef);
        var fetched = service.getImageDefinition(created.getId()).orElseThrow();

        Assertions.assertInstanceOf(DockerImageSource.class, fetched.getSource());
        var fetchedSource = (DockerImageSource) fetched.getSource();
        Assertions.assertInstanceOf(GitHubRef.class, fetchedSource.getExternalRegistryRef());
        Assertions.assertEquals("org/repo", ((GitHubRef) fetchedSource.getExternalRegistryRef()).repo());
    }

    @Test
    public void shouldCreateImageDefinitionWithGenericRef() {
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        var source = new DockerImageSource("http://test-uri", List.of("entry1"), new GenericRef("https://example.com/pkg"));
        imageDef.setSource(source);

        var created = service.createImageDefinition(imageDef);
        var fetched = service.getImageDefinition(created.getId()).orElseThrow();

        Assertions.assertInstanceOf(DockerImageSource.class, fetched.getSource());
        var fetchedSource = (DockerImageSource) fetched.getSource();
        Assertions.assertInstanceOf(GenericRef.class, fetchedSource.getExternalRegistryRef());
        Assertions.assertEquals("https://example.com/pkg", ((GenericRef) fetchedSource.getExternalRegistryRef()).url());
    }

    @Test
    public void shouldCreateImageDefinitionWithoutExternalRef() {
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();

        var created = service.createImageDefinition(imageDef);
        var fetched = service.getImageDefinition(created.getId()).orElseThrow();

        Assertions.assertInstanceOf(DockerImageSource.class, fetched.getSource());
        var fetchedSource = (DockerImageSource) fetched.getSource();
        Assertions.assertNull(fetchedSource.getExternalRegistryRef());
    }

    @Test
    public void shouldUpdateImageDefinitionExternalRef() {
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        var source = new DockerImageSource("http://test-uri", List.of("entry1"), new McpRegistryRef("old-pkg", null));
        imageDef.setSource(source);

        var created = service.createImageDefinition(imageDef);

        created.setSource(new DockerImageSource("http://test-uri", List.of("entry1"), new GitHubRef("new/repo")));
        service.updateImageDefinition(created.getId(), created);

        var fetched = service.getImageDefinition(created.getId()).orElseThrow();
        var fetchedSource = (DockerImageSource) fetched.getSource();
        Assertions.assertInstanceOf(GitHubRef.class, fetchedSource.getExternalRegistryRef());
        Assertions.assertEquals("new/repo", ((GitHubRef) fetchedSource.getExternalRegistryRef()).repo());
    }

    @Test
    public void shouldClearImageDefinitionExternalRef() {
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        var source = new DockerImageSource("http://test-uri", List.of("entry1"), new McpRegistryRef("my-pkg", null));
        imageDef.setSource(source);

        var created = service.createImageDefinition(imageDef);

        created.setSource(new DockerImageSource("http://test-uri", List.of("entry1"), null));
        service.updateImageDefinition(created.getId(), created);

        var fetched = service.getImageDefinition(created.getId()).orElseThrow();
        var fetchedSource = (DockerImageSource) fetched.getSource();
        Assertions.assertNull(fetchedSource.getExternalRegistryRef());
    }

    @Test
    public void shouldListImageDefinitions_withMixedExternalRefs() {
        var imgDef1 = FunctionalTestHelper.createMcpImageDefinition();
        imgDef1.setName("img-with-mcp-ref");
        imgDef1.setSource(new DockerImageSource("http://test-1", List.of("e"), new McpRegistryRef("pkg-1", null)));

        var imgDef2 = FunctionalTestHelper.createInterceptorImageDefinition();
        imgDef2.setName("img-with-generic-ref");
        imgDef2.setSource(new DockerImageSource("http://test-2", List.of("e"), new GenericRef("https://example.com")));

        var imgDef3 = FunctionalTestHelper.createAdapterImageDefinition();
        imgDef3.setName("img-no-ref");
        imgDef3.setSource(new DockerImageSource("http://test-3", List.of("e"), null));

        service.createImageDefinition(imgDef1);
        service.createImageDefinition(imgDef2);
        service.createImageDefinition(imgDef3);

        var allDefs = service.getAllImageDefinitions().stream().toList();
        Assertions.assertEquals(3, allDefs.size());

        for (var def : allDefs) {
            var src = (DockerImageSource) def.getSource();
            switch (def.getName()) {
                case "img-with-mcp-ref" -> Assertions.assertInstanceOf(McpRegistryRef.class, src.getExternalRegistryRef());
                case "img-with-generic-ref" -> Assertions.assertInstanceOf(GenericRef.class, src.getExternalRegistryRef());
                case "img-no-ref" -> Assertions.assertNull(src.getExternalRegistryRef());
                default -> Assertions.fail("Unexpected image definition: " + def.getName());
            }
        }
    }
}