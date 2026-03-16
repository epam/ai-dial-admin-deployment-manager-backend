package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.configuration.ConfigExportProperties;
import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.mapper.DeploymentMapper;
import com.epam.aidial.deployment.manager.model.AdapterImageDefinition;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import com.epam.aidial.deployment.manager.model.EnvVarMountType;
import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
import com.epam.aidial.deployment.manager.model.ImageBuilder;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageSource;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.McpRegistryRef;
import com.epam.aidial.deployment.manager.model.McpTransport;
import com.epam.aidial.deployment.manager.model.McpTransportType;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.Scaling;
import com.epam.aidial.deployment.manager.model.ScalingStrategy;
import com.epam.aidial.deployment.manager.model.ScalingStrategyType;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.config.ExportConfigComponent;
import com.epam.aidial.deployment.manager.model.config.ExportConfigComponentType;
import com.epam.aidial.deployment.manager.model.config.SelectedItemsExportRequest;
import com.epam.aidial.deployment.manager.model.deployment.CreateAdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateMcpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateNimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.HuggingFaceSource;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InternalImageSource;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NgcRegistrySource;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Source;
import com.epam.aidial.deployment.manager.model.probe.HttpGetProbe;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.epam.aidial.deployment.manager.model.probe.TcpSocketProbe;
import com.epam.aidial.deployment.manager.service.GlobalDomainWhitelistService;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.config.ConfigExportImportTestHelper;
import com.epam.aidial.deployment.manager.service.config.ConfigExporter;
import com.epam.aidial.deployment.manager.service.config.ConfigTransferService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public abstract class ConfigExportImportFunctionalTest {

    private static final String EXPECTED_EXPORT_CONFIG_RESOURCE = "/config/expected_export_config_for_import.json";

    private static final List<String> EXPORT_WHITELIST = List.of("test-export-a.com", "test-export-b.com");
    private static final String MCP_IMAGE_NAME = "mcp-exp";
    private static final String ADAPTER_IMAGE_NAME = "adapter-exp";
    private static final String INTERCEPTOR_IMAGE_NAME = "interceptor-exp";
    private static final String MCP_DEP_ID = "mcp-dep-exp";
    private static final String ADAPTER_DEP_ID = "adapter-dep-exp";
    private static final String INTERCEPTOR_DEP_ID = "interceptor-dep-exp";
    private static final String NIM_DEP_ID = "nim-dep-exp";
    private static final String INFERENCE_DEP_ID = "inference-dep-exp";
    private static final String VERSION = "1.0.0";
    private static final String TEST_SECRET_B = "TEST_SECRET_B";
    private static final String TEST_SECRET_B_VALUE = "secret-val-b";

    private static final Resources EMPTY_RESOURCES = new Resources(Map.of(), Map.of());

    @Autowired
    private ConfigExportProperties configExportProperties;
    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private GlobalDomainWhitelistService globalDomainWhitelistService;
    @Autowired
    private ConfigTransferService configTransferService;
    @Autowired
    private ConfigExporter configExporter;
    @Autowired
    @Qualifier("exportJsonMapper")
    private JsonMapper exportJsonMapper;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private KubernetesClient kubernetesClient;
    @Autowired
    private SecurityClaimsExtractor securityClaimsExtractor;
    @Autowired
    private DeploymentMapper deploymentMapper;

    private final AtomicReference<String> lastSecretName = new AtomicReference<>();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        Mockito.clearInvocations(securityClaimsExtractor);

        var mixedOperation = Mockito.mock(MixedOperation.class);
        var resource = Mockito.mock(Resource.class);
        var secret = Mockito.mock(Secret.class);
        var secretMetaData = Mockito.mock(ObjectMeta.class);

        when(mixedOperation.inNamespace(any())).thenReturn(mixedOperation);
        when(mixedOperation.resource(any())).thenReturn(resource);
        when(mixedOperation.withName(any())).thenAnswer(invocation -> {
            lastSecretName.set(invocation.getArgument(0));
            return resource;
        });
        when(resource.create()).thenReturn(secret);
        when(resource.get()).thenReturn(secret);
        when(secret.getMetadata()).thenReturn(secretMetaData);
        Map<String, String> secretData = new HashMap<>(FunctionalTestHelper.getEncodedSensitiveEnvs());
        secretData.put(TEST_SECRET_B, Base64.getEncoder().encodeToString(TEST_SECRET_B_VALUE.getBytes(StandardCharsets.UTF_8)));
        when(secret.getData()).thenReturn(secretData);
        when(secretMetaData.getName()).thenAnswer(invocation -> lastSecretName.get());
        when(kubernetesClient.secrets()).thenReturn(mixedOperation);
    }

    /**
     * Creates all types of image definitions and deployments, populates whitelist,
     * exports with addSecrets=true and addGlobalImageBuildDomainWhitelist=true,
     * then compares the JSON inside the ZIP to the expected export JSON (same file used for import test).
     */
    @Test
    void export_allComponentTypesAndWhitelist_exportedJsonMatchesExpected() throws Exception {
        // Create image definitions and deployments, set whitelist
        var data = createExportTestData();
        var request = getSelectedItemsExportRequest(data);

        // Export to ZIP
        var stream = configTransferService.exportConfig(request);
        var baos = new ByteArrayOutputStream();
        stream.writeTo(baos);
        byte[] zipBytes = baos.toByteArray();

        // Expected JSON
        String expectedJson = ResourceUtils.readResource(EXPECTED_EXPORT_CONFIG_RESOURCE);
        JsonNode expectedTree = exportJsonMapper.readTree(expectedJson);

        // Actual JSON
        String actualJson = extractFirstEntryFromZip(zipBytes, configExportProperties.getFileName());
        JsonNode actualTree = exportJsonMapper.readTree(actualJson);

        // Compare each exported key to expected
        expectedTree.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode expectedNode = actualTree.get(key);
            Assertions.assertNotNull(expectedNode, "Exported JSON should contain key: " + key);
            Assertions.assertEquals(expectedNode, entry.getValue(),
                    "Exported JSON for key '%s' should match expected export config".formatted(key));
        });
    }

    /**
     * Imports a ZIP built from the expected export config (test resource), then validates
     * that image definitions, deployments and whitelist were created correctly.
     */
    @Test
    void import_validExportZipFromResources_createsImageDefinitionsDeploymentsAndWhitelist() throws Exception {
        // Prepare ZIP
        String exportConfigJson = ResourceUtils.readResource(EXPECTED_EXPORT_CONFIG_RESOURCE);
        byte[] zipBytes = ConfigExportImportTestHelper.buildZipFromJson(configExportProperties.getFileName(), exportConfigJson);
        MultipartFile multipartFile = ConfigExportImportTestHelper.createZipMultipartFile("export.zip", zipBytes);

        // Import
        configTransferService.importConfig(multipartFile, ConflictResolutionPolicy.OVERWRITE);

        // Validate imported image definitions
        var expectedImageDefs = buildExportImageDefinitions();
        for (var expected : expectedImageDefs) {
            var actual = imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(
                    imageTypeOf(expected), expected.getName(), expected.getVersion()).orElseThrow();
            assertImageDefinitionEquals(actual, expected);
        }

        // Validate imported deployments
        var expectedDeployments = buildExpectedDeployments();
        for (var expected : expectedDeployments) {
            var actual = deploymentService.getDeployment(expected.getId()).orElseThrow();
            assertDeploymentEquals(actual, expected);
        }

        // Validate whitelist
        List<String> whitelist = globalDomainWhitelistService.getDomainWhitelist();
        Assertions.assertTrue(whitelist.containsAll(EXPORT_WHITELIST),
                "Whitelist should contain %s, got: %s".formatted(EXPORT_WHITELIST, whitelist));
    }

    private ExportTestData createExportTestData() {
        List<ImageDefinition> imageDefs = buildExportImageDefinitions();
        var mcpCreated = imageDefinitionService.createImageDefinition(imageDefs.get(0));
        var mcp2Created = imageDefinitionService.createImageDefinition(imageDefs.get(1));
        var adapterCreated = imageDefinitionService.createImageDefinition(imageDefs.get(2));
        var interceptorCreated = imageDefinitionService.createImageDefinition(imageDefs.get(3));

        for (CreateDeployment create : buildExportDeployments()) {
            deploymentService.createDeployment(create);
        }

        globalDomainWhitelistService.setDomainWhitelistOrCreate(EXPORT_WHITELIST);

        return new ExportTestData(mcpCreated.getId(), mcp2Created.getId(), adapterCreated.getId(), interceptorCreated.getId());
    }

    private static List<CreateDeployment> buildExportDeployments() {
        var probeTcp8080 = new ProbeProperties(true, 5, 10, 3, 2, new TcpSocketProbe(8080));

        var mcpDep = CreateMcpDeployment.builder()
                .id(MCP_DEP_ID)
                .source(new InternalImageSource(null, ImageType.MCP, MCP_IMAGE_NAME, VERSION))
                .displayName("MCP deployment export test")
                .description("MCP deployment for import test")
                .metadata(mcpDeploymentMetadata())
                .scaling(new Scaling(0, 5, 300, new ScalingStrategy(ScalingStrategyType.PENDING_REQUESTS, 10)))
                .resources(EMPTY_RESOURCES)
                .probeProperties(probeTcp8080)
                .containerPort(8080)
                .allowedDomains(List.of())
                .topics(List.of("nlp", "mcp-topic"))
                .transport(McpTransport.SSE)
                .mcpEndpointPath("some-path")
                .build();

        var adapterDep = CreateAdapterDeployment.builder()
                .id(ADAPTER_DEP_ID)
                .source(new InternalImageSource(null, ImageType.ADAPTER, ADAPTER_IMAGE_NAME, VERSION))
                .displayName("Adapter deployment export test")
                .description("Adapter deployment for import test")
                .metadata(adapterDeploymentMetadata())
                .scaling(new Scaling(0, 5, null, new ScalingStrategy(ScalingStrategyType.ACTIVE_REQUESTS, 50)))
                .resources(EMPTY_RESOURCES)
                .containerPort(5000)
                .allowedDomains(List.of("*"))
                .build();

        var interceptorDep = CreateInterceptorDeployment.builder()
                .id(INTERCEPTOR_DEP_ID)
                .source(new InternalImageSource(null, ImageType.INTERCEPTOR, INTERCEPTOR_IMAGE_NAME, VERSION))
                .displayName("Interceptor deployment export test")
                .description("Interceptor deployment for import test")
                .metadata(interceptorDeploymentMetadata())
                .scaling(new Scaling(0, 5, 600, null))
                .resources(EMPTY_RESOURCES)
                .probeProperties(probeTcp8080)
                .containerPort(8080)
                .allowedDomains(List.of("test-domain-2.com"))
                .build();

        var nimProbe = new ProbeProperties(true, 5, 10, 3, 2, new TcpSocketProbe(8000));
        var nimDep = CreateNimDeployment.builder()
                .id(NIM_DEP_ID)
                .displayName("NIM deployment export test")
                .description("NIM deployment for import test")
                .metadata(new DeploymentMetadata(List.of()))
                .resources(EMPTY_RESOURCES)
                .probeProperties(nimProbe)
                .containerPort(8000)
                .allowedDomains(List.of())
                .source(new NgcRegistrySource("nvcr.io/nim/test-model:latest"))
                .containerGrpcPort(50051)
                .build();

        var inferenceProbe = new ProbeProperties(true, 5, 10, 3, 2, new HttpGetProbe("/health", 8080));
        var inferenceDep = CreateInferenceDeployment.builder()
                .id(INFERENCE_DEP_ID)
                .displayName("Inference deployment export test")
                .description("Inference deployment for import test")
                .modelFormat("huggingface")
                .metadata(new DeploymentMetadata(List.of()))
                .resources(EMPTY_RESOURCES)
                .probeProperties(inferenceProbe)
                .containerPort(8080)
                .allowedDomains(List.of())
                .source(new HuggingFaceSource("test-org/export-test-model"))
                .build();

        return List.of(mcpDep, adapterDep, interceptorDep, nimDep, inferenceDep);
    }

    private static List<ImageDefinition> buildExportImageDefinitions() {
        var mcp1 = FunctionalTestHelper.createMcpImageDefinition();
        mcp1.setName(MCP_IMAGE_NAME);
        mcp1.setVersion(VERSION);
        mcp1.setDescription("MCP for export/import test");
        mcp1.setSource(new DockerImageSource("test-registry/mcp-exp:1.0", List.of(), new McpRegistryRef("export-test-pkg")));
        mcp1.setImageBuilder(ImageBuilder.BUILDKIT);
        mcp1.setLicense("MIT");
        mcp1.setTopics(List.of("topic1"));
        mcp1.setAllowedDomains(List.of());
        mcp1.setTransportType(McpTransportType.REMOTE);

        var mcp2 = FunctionalTestHelper.createMcpImageDefinition();
        mcp2.setName(MCP_IMAGE_NAME);
        mcp2.setVersion("2.0.0");
        mcp2.setDescription("MCP for export/import test v2");
        mcp2.setSource(GitDockerfileImageSource.builder()
                .url("https://github.com/test/mcp-exp.git")
                .branchName("main")
                .build());
        mcp2.setImageBuilder(ImageBuilder.BUILDKIT_ROOTLESS);
        mcp2.setLicense("MIT");
        mcp2.setTopics(List.of("topic2"));
        mcp2.setAllowedDomains(List.of());
        mcp2.setTransportType(McpTransportType.LOCAL);

        var adapter = FunctionalTestHelper.createAdapterImageDefinition();
        adapter.setName(ADAPTER_IMAGE_NAME);
        adapter.setVersion(VERSION);
        adapter.setDescription("Adapter for export/import test");
        adapter.setSource(new DockerImageSource("test-registry/adapter-exp:1.0", List.of(), null));
        adapter.setImageBuilder(ImageBuilder.BUILDKIT);
        adapter.setLicense("");
        adapter.setTopics(List.of());
        adapter.setAllowedDomains(List.of("*"));

        var interceptor = FunctionalTestHelper.createInterceptorImageDefinition();
        interceptor.setName(INTERCEPTOR_IMAGE_NAME);
        interceptor.setVersion(VERSION);
        interceptor.setDescription("Interceptor for export/import test");
        interceptor.setSource(new DockerImageSource("test-registry/interceptor-exp:1.0", List.of(), null));
        interceptor.setImageBuilder(ImageBuilder.BUILDKIT);
        interceptor.setLicense("");
        interceptor.setTopics(List.of());
        interceptor.setAllowedDomains(List.of("test-domain-1.com"));

        return List.of(mcp1, mcp2, adapter, interceptor);
    }

    private static SelectedItemsExportRequest getSelectedItemsExportRequest(ExportTestData data) {
        var request = new SelectedItemsExportRequest();
        request.setAddSecrets(true);
        request.setAddGlobalImageBuildDomainWhitelist(true);
        request.setComponents(List.of(
                new ExportConfigComponent(data.firstMcpImageDefId().toString(), ExportConfigComponentType.MCP_IMAGE_DEFINITION),
                new ExportConfigComponent(data.secondMcpImageDefId().toString(), ExportConfigComponentType.MCP_IMAGE_DEFINITION),
                new ExportConfigComponent(data.adapterImageDefId().toString(), ExportConfigComponentType.ADAPTER_IMAGE_DEFINITION),
                new ExportConfigComponent(data.interceptorImageDefId().toString(), ExportConfigComponentType.INTERCEPTOR_IMAGE_DEFINITION),
                new ExportConfigComponent(MCP_DEP_ID, ExportConfigComponentType.MCP_DEPLOYMENT),
                new ExportConfigComponent(ADAPTER_DEP_ID, ExportConfigComponentType.ADAPTER_DEPLOYMENT),
                new ExportConfigComponent(INTERCEPTOR_DEP_ID, ExportConfigComponentType.INTERCEPTOR_DEPLOYMENT),
                new ExportConfigComponent(NIM_DEP_ID, ExportConfigComponentType.NIM_DEPLOYMENT),
                new ExportConfigComponent(INFERENCE_DEP_ID, ExportConfigComponentType.INFERENCE_DEPLOYMENT)
        ));
        return request;
    }

    private List<Deployment> buildExpectedDeployments() {
        return buildExportDeployments().stream()
                .map(deploymentMapper::toDeployment)
                .toList();
    }

    private static ImageType imageTypeOf(ImageDefinition def) {
        return switch (def) {
            case McpImageDefinition ignored -> ImageType.MCP;
            case AdapterImageDefinition ignored -> ImageType.ADAPTER;
            case InterceptorImageDefinition ignored -> ImageType.INTERCEPTOR;
            default -> throw new IllegalArgumentException("Unsupported: " + def.getClass().getName());
        };
    }

    private static DeploymentMetadata mcpDeploymentMetadata() {
        return new DeploymentMetadata(List.of(
                new EnvVarDefinition("TEST_ENV_A", new SimpleEnvVarValue("test-val-a"), EnvVarMountType.CONTENT, ""),
                new EnvVarDefinition(TEST_SECRET_B, new SimpleEnvVarValue(TEST_SECRET_B_VALUE), EnvVarMountType.SECURE_CONTENT, "")
        ));
    }

    private static DeploymentMetadata adapterDeploymentMetadata() {
        return new DeploymentMetadata(List.of(
                new EnvVarDefinition("ADAPTER_URL", new SimpleEnvVarValue("http://adapter-test-url"), EnvVarMountType.CONTENT, "")
        ));
    }

    private static DeploymentMetadata interceptorDeploymentMetadata() {
        return new DeploymentMetadata(List.of(
                new EnvVarDefinition("INTERCEPTOR_URL", new SimpleEnvVarValue("http://interceptor-test-url"), EnvVarMountType.CONTENT, "")
        ));
    }

    private void assertImageDefinitionEquals(ImageDefinition actual, ImageDefinition expected) {
        Assertions.assertEquals(expected.getName(), actual.getName(), "name");
        Assertions.assertEquals(expected.getVersion(), actual.getVersion(), "version");
        Assertions.assertEquals(expected.getDescription(), actual.getDescription(), "description");
        Assertions.assertEquals(expected.getImageBuilder(), actual.getImageBuilder(), "imageBuilder");
        Assertions.assertEquals(expected.getLicense(), actual.getLicense(), "license");
        Assertions.assertEquals(expected.getTopics(), actual.getTopics(), "topics");
        Assertions.assertEquals(
                expected.getAllowedDomains() != null ? expected.getAllowedDomains() : List.of(),
                actual.getAllowedDomains() != null ? actual.getAllowedDomains() : List.of(),
                "allowedDomains");
        assertImageSourceEquals(expected.getSource(), actual.getSource());
        if (expected instanceof McpImageDefinition expectedMcp && actual instanceof McpImageDefinition actualMcp) {
            Assertions.assertEquals(expectedMcp.getTransportType(), actualMcp.getTransportType(), "transportType");
        }
    }

    private static void assertImageSourceEquals(ImageSource expected, ImageSource actual) {
        if (expected == null && actual == null) {
            return;
        }

        Assertions.assertNotNull(expected, "expected source");
        Assertions.assertNotNull(actual, "actual source");

        if (expected instanceof DockerImageSource expDocker && actual instanceof DockerImageSource actDocker) {
            Assertions.assertEquals(expDocker.getImageUri(), actDocker.getImageUri(), "source.imageUri");
            Assertions.assertEquals(expDocker.getEntrypoint(), actDocker.getEntrypoint(), "source.entrypoint");
            Assertions.assertEquals(expDocker.getExternalRegistryRef(), actDocker.getExternalRegistryRef(), "source.externalRegistryRef");
        } else if (expected instanceof GitDockerfileImageSource expGit && actual instanceof GitDockerfileImageSource actGit) {
            Assertions.assertEquals(expGit.getUrl(), actGit.getUrl(), "source.url");
            Assertions.assertEquals(expGit.getBranchName(), actGit.getBranchName(), "source.branchName");
            Assertions.assertEquals(expGit.getExternalRegistryRef(), actGit.getExternalRegistryRef(), "source.externalRegistryRef");
        } else {
            Assertions.fail("Source type mismatch: expected=%s, actual=%s"
                    .formatted(expected.getClass().getSimpleName(), actual.getClass().getSimpleName()));
        }
    }

    private void assertDeploymentEquals(Deployment actual, Deployment expected) {
        Assertions.assertEquals(expected.getId(), actual.getId(), "id");
        Assertions.assertEquals(expected.getDisplayName(), actual.getDisplayName(), "displayName");
        Assertions.assertEquals(expected.getDescription(), actual.getDescription(), "description");
        Assertions.assertEquals(expected.getScaling(), actual.getScaling(), "scaling");
        Assertions.assertEquals(expected.getContainerPort(), actual.getContainerPort(), "containerPort");
        Assertions.assertEquals(expected.getResources(), actual.getResources(), "resources");
        Assertions.assertEquals(expected.getAllowedDomains(), actual.getAllowedDomains(), "allowedDomains");
        Assertions.assertEquals(
                Set.copyOf(expected.getTopics() != null ? expected.getTopics() : List.of()),
                Set.copyOf(actual.getTopics() != null ? actual.getTopics() : List.of()),
                "topics");
        assertProbePropertiesEquals(expected.getProbeProperties(), actual.getProbeProperties());
        assertMetadataEnvsEquals(
                expected.getMetadata() != null ? expected.getMetadata().getEnvs() : null,
                actual.getMetadata() != null ? actual.getMetadata().getEnvs() : null);

        if (expected.getSource() instanceof InternalImageSource expSource) {
            Assertions.assertInstanceOf(InternalImageSource.class, actual.getSource(), "source should be InternalImageSource");
            InternalImageSource actSource = (InternalImageSource) actual.getSource();
            Assertions.assertEquals(expSource.imageDefinitionName(), actSource.imageDefinitionName(), "imageDefinitionName");
            Assertions.assertEquals(expSource.imageDefinitionVersion(), actSource.imageDefinitionVersion(), "imageDefinitionVersion");
        }

        switch (expected) {
            case McpDeployment expMcp when actual instanceof McpDeployment actMcp -> {
                Assertions.assertEquals(expMcp.getTransport(), actMcp.getTransport(), "transport");
                Assertions.assertEquals(expMcp.getMcpEndpointPath(), actMcp.getMcpEndpointPath(), "mcpEndpointPath");
            }
            case NimDeployment expNim when actual instanceof NimDeployment actNim -> {
                Assertions.assertEquals(expNim.getContainerGrpcPort(), actNim.getContainerGrpcPort(), "containerGrpcPort");
                assertNimSourceEquals(expNim.getSource(), actNim.getSource());
            }
            case InferenceDeployment expInf when actual instanceof InferenceDeployment actInf -> {
                Assertions.assertEquals(expInf.getModelFormat(), actInf.getModelFormat(), "modelFormat");
                assertInferenceSourceEquals(expInf.getSource(), actInf.getSource());
            }
            default -> { }
        }
    }

    private static void assertProbePropertiesEquals(ProbeProperties expected, ProbeProperties actual) {
        if (expected == null && actual == null) {
            return;
        }

        Assertions.assertNotNull(expected, "expected probeProperties");
        Assertions.assertNotNull(actual, "actual probeProperties");
        Assertions.assertEquals(expected.isEnabled(), actual.isEnabled(), "probeProperties.enabled");
        Assertions.assertEquals(expected.getInitialDelaySeconds(), actual.getInitialDelaySeconds(), "probeProperties.initialDelaySeconds");
        Assertions.assertEquals(expected.getPeriodSeconds(), actual.getPeriodSeconds(), "probeProperties.periodSeconds");
        Assertions.assertEquals(expected.getTimeoutSeconds(), actual.getTimeoutSeconds(), "probeProperties.timeoutSeconds");
        Assertions.assertEquals(expected.getFailureThreshold(), actual.getFailureThreshold(), "probeProperties.failureThreshold");

        if (expected.getProbe() instanceof TcpSocketProbe expTcp && actual.getProbe() instanceof TcpSocketProbe actTcp) {
            Assertions.assertEquals(expTcp.getPort(), actTcp.getPort(), "probe.port");
        } else if (expected.getProbe() instanceof HttpGetProbe expHttp && actual.getProbe() instanceof HttpGetProbe actHttp) {
            Assertions.assertEquals(expHttp.getPath(), actHttp.getPath(), "probe.path");
            Assertions.assertEquals(expHttp.getPort(), actHttp.getPort(), "probe.port");
        } else {
            Assertions.assertEquals(expected.getProbe(), actual.getProbe(), "probe");
        }
    }

    private static void assertMetadataEnvsEquals(List<EnvVarDefinition> expected, List<EnvVarDefinition> actual) {
        if (expected == null && actual == null) {
            return;
        }

        Assertions.assertNotNull(expected, "expected metadata.envs");
        Assertions.assertNotNull(actual, "actual metadata.envs");
        Assertions.assertEquals(expected.size(), actual.size(), "metadata.envs.size");

        for (int i = 0; i < expected.size(); i++) {
            var exp = expected.get(i);
            var act = actual.get(i);
            Assertions.assertEquals(exp.getName(), act.getName(), "metadata.envs[%d].name".formatted(i));
            Assertions.assertEquals(exp.getMountType(), act.getMountType(), "metadata.envs[%d].mountType".formatted(i));
            if (exp.getValue() != null && act.getValue() != null && exp.getValue() instanceof SimpleEnvVarValue expVal && act.getValue() instanceof SimpleEnvVarValue actVal) {
                Assertions.assertEquals(expVal.getValue(), actVal.getValue(), "metadata.envs[%d].value".formatted(i));
            }
        }
    }

    private static void assertNimSourceEquals(Source expected, Source actual) {
        if (expected instanceof NgcRegistrySource(String ref) && actual instanceof NgcRegistrySource(String imageRef)) {
            Assertions.assertEquals(ref, imageRef, "source.imageRef");
        } else {
            Assertions.assertEquals(expected, actual, "source");
        }
    }

    private static void assertInferenceSourceEquals(Source expected, Source actual) {
        if (expected instanceof HuggingFaceSource(String modelName) && actual instanceof HuggingFaceSource(String name)) {
            Assertions.assertEquals(modelName, name, "source.modelName");
        } else {
            Assertions.assertEquals(expected, actual, "source");
        }
    }

    private static String extractFirstEntryFromZip(byte[] zipBytes, String expectedEntryName) throws Exception {
        try (var zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            var entry = zis.getNextEntry();
            Assertions.assertNotNull(entry, "ZIP should contain at least one entry");
            Assertions.assertEquals(expectedEntryName, entry.getName(), "ZIP entry name");
            return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record ExportTestData(UUID firstMcpImageDefId, UUID secondMcpImageDefId, UUID adapterImageDefId, UUID interceptorImageDefId) { }
}
