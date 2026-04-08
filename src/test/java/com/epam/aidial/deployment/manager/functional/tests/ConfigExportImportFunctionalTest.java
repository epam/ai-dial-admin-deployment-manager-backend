package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.configuration.ConfigExportProperties;
import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.mapper.DeploymentMapper;
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
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.McpRegistryRef;
import com.epam.aidial.deployment.manager.model.McpTransport;
import com.epam.aidial.deployment.manager.model.McpTransportType;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.Scaling;
import com.epam.aidial.deployment.manager.model.ScalingStrategy;
import com.epam.aidial.deployment.manager.model.ScalingStrategyType;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.config.ExportConfigComponent;
import com.epam.aidial.deployment.manager.model.config.ExportConfigComponentType;
import com.epam.aidial.deployment.manager.model.config.ImportAction;
import com.epam.aidial.deployment.manager.model.config.ImportComponent;
import com.epam.aidial.deployment.manager.model.config.ImportConfigPreview;
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
import com.epam.aidial.deployment.manager.web.dto.config.ExportComponentInfoDto;
import com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentTypeDto;
import com.epam.aidial.deployment.manager.web.mapper.ExportConfigMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Autowired
    private ExportConfigMapper exportConfigMapper;

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
            assertThat(expectedNode).as("Exported JSON should contain key: " + key).isNotNull();
            assertThat(entry.getValue()).as("Exported JSON for key '%s' should match expected export config".formatted(key)).isEqualTo(expectedNode);
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

        // Set pre-existing whitelist with domains not in the import to verify merge behavior
        List<String> preExisting = List.of("pre-existing-a.com", "pre-existing-b.com");
        globalDomainWhitelistService.setDomainWhitelistOrCreate(preExisting);

        // Import
        configTransferService.importConfig(multipartFile, ConflictResolutionPolicy.OVERWRITE);

        // Validate imported image definitions
        var expectedImageDefs = buildExportImageDefinitions();
        for (var expected : expectedImageDefs) {
            var actual = imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(
                    ImageType.of(expected), expected.getName(), expected.getVersion()).orElseThrow();
            assertImageDefinitionEquals(actual, expected);
        }

        // Validate imported deployments
        var expectedDeployments = buildExpectedDeployments();
        for (var expected : expectedDeployments) {
            var actual = deploymentService.getDeployment(expected.getId()).orElseThrow();
            assertDeploymentEquals(actual, expected);
        }

        // Validate whitelist — merge preserves pre-existing entries and adds imported entries
        List<String> whitelist = globalDomainWhitelistService.getDomainWhitelist();
        assertThat(whitelist.containsAll(EXPORT_WHITELIST))
                .as("Whitelist should contain imported entries %s, got: %s".formatted(EXPORT_WHITELIST, whitelist)).isTrue();
        assertThat(whitelist.containsAll(preExisting))
                .as("Whitelist should preserve pre-existing entries %s, got: %s".formatted(preExisting, whitelist)).isTrue();
    }

    @Test
    void previewConfig_returnsCorrectComponentInfoDtos_whenValidSelectionProvided() {
        var data = createExportTestData();
        var request = getSelectedItemsExportRequest(data);

        var config = configTransferService.getExportConfig(request);
        var preview = exportConfigMapper.toExportConfigPreviewDto(config);

        assertThat(preview.globalImageBuildDomainWhitelist()).isEqualTo(EXPORT_WHITELIST);

        var imageDefIds = preview.imageDefinitions().stream().map(ExportComponentInfoDto::id).toList();
        assertThat(imageDefIds.contains(data.firstMcpImageDefId().toString())).as("should contain first MCP image def").isTrue();
        assertThat(imageDefIds.contains(data.secondMcpImageDefId().toString())).as("should contain second MCP image def").isTrue();
        assertThat(imageDefIds.contains(data.adapterImageDefId().toString())).as("should contain adapter image def").isTrue();
        assertThat(imageDefIds.contains(data.interceptorImageDefId().toString())).as("should contain interceptor image def").isTrue();

        // Assert all fields of an image definition (MCP)
        var firstMcpImageDef = findById(preview.imageDefinitions(), data.firstMcpImageDefId().toString());
        assertThat(firstMcpImageDef.displayName()).as("image def displayName").isEqualTo(MCP_IMAGE_NAME);
        assertThat(firstMcpImageDef.version()).as("image def version").isEqualTo(VERSION);
        assertThat(firstMcpImageDef.description()).as("image def description").isEqualTo("MCP for export/import test");
        assertThat(firstMcpImageDef.type()).as("image def type").isEqualTo(ExportConfigComponentTypeDto.MCP_IMAGE_DEFINITION);

        // Assert all fields of an image definition (Adapter)
        var adapterImageDef = findById(preview.imageDefinitions(), data.adapterImageDefId().toString());
        assertThat(adapterImageDef.displayName()).as("adapter image def displayName").isEqualTo(ADAPTER_IMAGE_NAME);
        assertThat(adapterImageDef.version()).as("adapter image def version").isEqualTo(VERSION);
        assertThat(adapterImageDef.description()).as("adapter image def description").isEqualTo("Adapter for export/import test");
        assertThat(adapterImageDef.type()).as("adapter image def type").isEqualTo(ExportConfigComponentTypeDto.ADAPTER_IMAGE_DEFINITION);

        var deploymentIds = preview.deployments().stream().map(ExportComponentInfoDto::id).toList();
        assertThat(deploymentIds.containsAll(
                List.of(MCP_DEP_ID, ADAPTER_DEP_ID, INTERCEPTOR_DEP_ID, NIM_DEP_ID, INFERENCE_DEP_ID)))
                .as("deployments should contain all selected deployment ids").isTrue();

        // Assert all fields of a deployment (MCP)
        var mcpDep = findById(preview.deployments(), MCP_DEP_ID);
        assertThat(mcpDep.displayName()).as("deployment displayName").isEqualTo("MCP deployment export test");
        assertThat(mcpDep.version()).as("deployment version should be null").isNull();
        assertThat(mcpDep.description()).as("deployment description").isEqualTo("MCP deployment for import test");
        assertThat(mcpDep.type()).as("deployment type").isEqualTo(ExportConfigComponentTypeDto.MCP_DEPLOYMENT);

        // Assert all fields of a deployment (NIM)
        var nimDep = findById(preview.deployments(), NIM_DEP_ID);
        assertThat(nimDep.displayName()).as("nim deployment displayName").isEqualTo("NIM deployment export test");
        assertThat(nimDep.version()).as("nim deployment version should be null").isNull();
        assertThat(nimDep.description()).as("nim deployment description").isEqualTo("NIM deployment for import test");
        assertThat(nimDep.type()).as("nim deployment type").isEqualTo(ExportConfigComponentTypeDto.NIM_DEPLOYMENT);

        preview.deployments().forEach(d ->
                assertThat(d.version()).as("deployment version should be null").isNull());
        preview.imageDefinitions().forEach(d ->
                assertThat(d.version()).as("image definition version should not be null").isNotNull());
    }

    @Test
    void previewConfig_returnsEmptyLists_whenEmptySelectionProvided() {
        var request = new SelectedItemsExportRequest();
        request.setAddGlobalImageBuildDomainWhitelist(false);
        request.setComponents(List.of());

        var config = configTransferService.getExportConfig(request);
        var preview = exportConfigMapper.toExportConfigPreviewDto(config);

        assertThat(preview.globalImageBuildDomainWhitelist().isEmpty()).as("whitelist should be empty").isTrue();
        assertThat(preview.imageDefinitions().isEmpty()).as("imageDefinitions should be empty").isTrue();
        assertThat(preview.deployments().isEmpty()).as("deployments should be empty").isTrue();
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
        assertThat(actual.getName()).as("name").isEqualTo(expected.getName());
        assertThat(actual.getVersion()).as("version").isEqualTo(expected.getVersion());
        assertThat(actual.getDescription()).as("description").isEqualTo(expected.getDescription());
        assertThat(actual.getImageBuilder()).as("imageBuilder").isEqualTo(expected.getImageBuilder());
        assertThat(actual.getLicense()).as("license").isEqualTo(expected.getLicense());
        assertThat(actual.getTopics()).as("topics").isEqualTo(expected.getTopics());
        assertThat(actual.getAllowedDomains() != null ? actual.getAllowedDomains() : List.of())
                .as("allowedDomains")
                .isEqualTo(expected.getAllowedDomains() != null ? expected.getAllowedDomains() : List.of());
        assertImageSourceEquals(expected.getSource(), actual.getSource());
        if (expected instanceof McpImageDefinition expectedMcp && actual instanceof McpImageDefinition actualMcp) {
            assertThat(actualMcp.getTransportType()).as("transportType").isEqualTo(expectedMcp.getTransportType());
        }
    }

    private static void assertImageSourceEquals(ImageSource expected, ImageSource actual) {
        if (expected == null && actual == null) {
            return;
        }

        assertThat(expected).as("expected source").isNotNull();
        assertThat(actual).as("actual source").isNotNull();

        if (expected instanceof DockerImageSource expDocker && actual instanceof DockerImageSource actDocker) {
            assertThat(actDocker.getImageUri()).as("source.imageUri").isEqualTo(expDocker.getImageUri());
            assertThat(actDocker.getEntrypoint()).as("source.entrypoint").isEqualTo(expDocker.getEntrypoint());
            assertThat(actDocker.getExternalRegistryRef()).as("source.externalRegistryRef").isEqualTo(expDocker.getExternalRegistryRef());
        } else if (expected instanceof GitDockerfileImageSource expGit && actual instanceof GitDockerfileImageSource actGit) {
            assertThat(actGit.getUrl()).as("source.url").isEqualTo(expGit.getUrl());
            assertThat(actGit.getBranchName()).as("source.branchName").isEqualTo(expGit.getBranchName());
            assertThat(actGit.getExternalRegistryRef()).as("source.externalRegistryRef").isEqualTo(expGit.getExternalRegistryRef());
        } else {
            org.assertj.core.api.Assertions.fail("Source type mismatch: expected=%s, actual=%s"
                    .formatted(expected.getClass().getSimpleName(), actual.getClass().getSimpleName()));
        }
    }

    private void assertDeploymentEquals(Deployment actual, Deployment expected) {
        assertThat(actual.getId()).as("id").isEqualTo(expected.getId());
        assertThat(actual.getDisplayName()).as("displayName").isEqualTo(expected.getDisplayName());
        assertThat(actual.getDescription()).as("description").isEqualTo(expected.getDescription());
        assertThat(actual.getScaling()).as("scaling").isEqualTo(expected.getScaling());
        assertThat(actual.getContainerPort()).as("containerPort").isEqualTo(expected.getContainerPort());
        assertThat(actual.getResources()).as("resources").isEqualTo(expected.getResources());
        assertThat(actual.getAllowedDomains()).as("allowedDomains").isEqualTo(expected.getAllowedDomains());
        assertThat(Set.copyOf(actual.getTopics() != null ? actual.getTopics() : List.of()))
                .as("topics")
                .isEqualTo(Set.copyOf(expected.getTopics() != null ? expected.getTopics() : List.of()));
        assertProbePropertiesEquals(expected.getProbeProperties(), actual.getProbeProperties());
        assertMetadataEnvsEquals(
                expected.getMetadata() != null ? expected.getMetadata().getEnvs() : null,
                actual.getMetadata() != null ? actual.getMetadata().getEnvs() : null);

        if (expected.getSource() instanceof InternalImageSource expSource) {
            assertThat(actual.getSource()).as("source should be InternalImageSource").isInstanceOf(InternalImageSource.class);
            InternalImageSource actSource = (InternalImageSource) actual.getSource();
            assertThat(actSource.imageDefinitionName()).as("imageDefinitionName").isEqualTo(expSource.imageDefinitionName());
            assertThat(actSource.imageDefinitionVersion()).as("imageDefinitionVersion").isEqualTo(expSource.imageDefinitionVersion());
        }

        switch (expected) {
            case McpDeployment expMcp when actual instanceof McpDeployment actMcp -> {
                assertThat(actMcp.getTransport()).as("transport").isEqualTo(expMcp.getTransport());
                assertThat(actMcp.getMcpEndpointPath()).as("mcpEndpointPath").isEqualTo(expMcp.getMcpEndpointPath());
            }
            case NimDeployment expNim when actual instanceof NimDeployment actNim -> {
                assertThat(actNim.getContainerGrpcPort()).as("containerGrpcPort").isEqualTo(expNim.getContainerGrpcPort());
                assertNimSourceEquals(expNim.getSource(), actNim.getSource());
            }
            case InferenceDeployment expInf when actual instanceof InferenceDeployment actInf -> {
                assertThat(actInf.getModelFormat()).as("modelFormat").isEqualTo(expInf.getModelFormat());
                assertInferenceSourceEquals(expInf.getSource(), actInf.getSource());
            }
            default -> { }
        }
    }

    private static void assertProbePropertiesEquals(ProbeProperties expected, ProbeProperties actual) {
        if (expected == null && actual == null) {
            return;
        }

        assertThat(expected).as("expected probeProperties").isNotNull();
        assertThat(actual).as("actual probeProperties").isNotNull();
        assertThat(actual.isEnabled()).as("probeProperties.enabled").isEqualTo(expected.isEnabled());
        assertThat(actual.getInitialDelaySeconds()).as("probeProperties.initialDelaySeconds").isEqualTo(expected.getInitialDelaySeconds());
        assertThat(actual.getPeriodSeconds()).as("probeProperties.periodSeconds").isEqualTo(expected.getPeriodSeconds());
        assertThat(actual.getTimeoutSeconds()).as("probeProperties.timeoutSeconds").isEqualTo(expected.getTimeoutSeconds());
        assertThat(actual.getFailureThreshold()).as("probeProperties.failureThreshold").isEqualTo(expected.getFailureThreshold());

        if (expected.getProbe() instanceof TcpSocketProbe expTcp && actual.getProbe() instanceof TcpSocketProbe actTcp) {
            assertThat(actTcp.getPort()).as("probe.port").isEqualTo(expTcp.getPort());
        } else if (expected.getProbe() instanceof HttpGetProbe expHttp && actual.getProbe() instanceof HttpGetProbe actHttp) {
            assertThat(actHttp.getPath()).as("probe.path").isEqualTo(expHttp.getPath());
            assertThat(actHttp.getPort()).as("probe.port").isEqualTo(expHttp.getPort());
        } else {
            assertThat(actual.getProbe()).as("probe").isEqualTo(expected.getProbe());
        }
    }

    private static void assertMetadataEnvsEquals(List<EnvVarDefinition> expected, List<EnvVarDefinition> actual) {
        if (expected == null && actual == null) {
            return;
        }

        assertThat(expected).as("expected metadata.envs").isNotNull();
        assertThat(actual).as("actual metadata.envs").isNotNull();
        assertThat(actual.size()).as("metadata.envs.size").isEqualTo(expected.size());

        for (int i = 0; i < expected.size(); i++) {
            var exp = expected.get(i);
            var act = actual.get(i);
            assertThat(act.getName()).as("metadata.envs[%d].name".formatted(i)).isEqualTo(exp.getName());
            assertThat(act.getMountType()).as("metadata.envs[%d].mountType".formatted(i)).isEqualTo(exp.getMountType());
            if (exp.getValue() != null && act.getValue() != null && exp.getValue() instanceof SimpleEnvVarValue expVal && act.getValue() instanceof SimpleEnvVarValue actVal) {
                assertThat(actVal.getValue()).as("metadata.envs[%d].value".formatted(i)).isEqualTo(expVal.getValue());
            }
        }
    }

    private static void assertNimSourceEquals(Source expected, Source actual) {
        if (expected instanceof NgcRegistrySource(String ref) && actual instanceof NgcRegistrySource(String imageRef)) {
            assertThat(imageRef).as("source.imageRef").isEqualTo(ref);
        } else {
            assertThat(actual).as("source").isEqualTo(expected);
        }
    }

    private static void assertInferenceSourceEquals(Source expected, Source actual) {
        if (expected instanceof HuggingFaceSource(String modelName) && actual instanceof HuggingFaceSource(String name)) {
            assertThat(name).as("source.modelName").isEqualTo(modelName);
        } else {
            assertThat(actual).as("source").isEqualTo(expected);
        }
    }

    private static String extractFirstEntryFromZip(byte[] zipBytes, String expectedEntryName) throws Exception {
        try (var zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            var entry = zis.getNextEntry();
            assertThat(entry).as("ZIP should contain at least one entry").isNotNull();
            assertThat(entry.getName()).as("ZIP entry name").isEqualTo(expectedEntryName);
            return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void importPreview_returnsCreateForAllTypes_withoutMutatingDatabase() throws Exception {
        // Verify entity doesn't exist before preview
        assertThat(imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(ImageType.MCP, MCP_IMAGE_NAME, VERSION))
                .isEmpty();

        MultipartFile zipFile = buildImportZipFromResource();

        // First preview - empty DB → CREATE for all 8 entity types
        ImportConfigPreview preview = configTransferService.getImportConfigPreview(zipFile, ConflictResolutionPolicy.OVERWRITE);
        assertPreviewAction(preview, ImportAction.CREATE);

        // Whitelist: Flyway pre-seeds empty row → UPDATE
        assertThat(preview.getGlobalImageBuildDomainWhitelist()).isNotNull();
        assertThat(preview.getGlobalImageBuildDomainWhitelist().getAction()).isEqualTo(ImportAction.UPDATE);
        assertThat(preview.getGlobalImageBuildDomainWhitelist().getPrev()).isEmpty();
        assertThat(preview.getGlobalImageBuildDomainWhitelist().getNext()).containsAll(EXPORT_WHITELIST);

        // Second preview - must not mutate DB
        configTransferService.getImportConfigPreview(zipFile, ConflictResolutionPolicy.OVERWRITE);
        assertThat(imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(ImageType.MCP, MCP_IMAGE_NAME, VERSION))
                .isEmpty();

        // Real import creates the entity
        configTransferService.importConfig(zipFile, ConflictResolutionPolicy.OVERWRITE);
        assertThat(imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(ImageType.MCP, MCP_IMAGE_NAME, VERSION))
                .isPresent();
    }

    @Test
    void importPreview_respectsConflictResolutionPolicy_whenEntitiesExist() throws Exception {
        // Seed DB with all entities
        MultipartFile zipFile = buildImportZipFromResource();
        configTransferService.importConfig(zipFile, ConflictResolutionPolicy.OVERWRITE);

        // Add extra domain to DB whitelist to verify merge semantics in preview
        List<String> extendedWhitelist = List.of("test-export-a.com", "test-export-b.com", "extra-domain.com");
        globalDomainWhitelistService.setDomainWhitelistOrCreate(extendedWhitelist);

        // OVERWRITE → UPDATE, next = merged list preserving extra-domain.com
        ImportConfigPreview overwrite = configTransferService.getImportConfigPreview(zipFile, ConflictResolutionPolicy.OVERWRITE);
        assertPreviewAction(overwrite, ImportAction.UPDATE);
        assertThat(overwrite.getGlobalImageBuildDomainWhitelist()).isNotNull();
        assertThat(overwrite.getGlobalImageBuildDomainWhitelist().getAction()).isEqualTo(ImportAction.UPDATE);
        assertThat(overwrite.getGlobalImageBuildDomainWhitelist().getPrev()).containsExactlyElementsOf(extendedWhitelist);
        assertThat(overwrite.getGlobalImageBuildDomainWhitelist().getNext()).containsAll(EXPORT_WHITELIST);
        assertThat(overwrite.getGlobalImageBuildDomainWhitelist().getNext()).contains("extra-domain.com");

        // SKIP_IF_EXISTS → SKIP
        ImportConfigPreview skip = configTransferService.getImportConfigPreview(zipFile, ConflictResolutionPolicy.SKIP_IF_EXISTS);
        assertPreviewAction(skip, ImportAction.SKIP);
        assertThat(skip.getGlobalImageBuildDomainWhitelist()).isNotNull();
        assertThat(skip.getGlobalImageBuildDomainWhitelist().getAction()).isEqualTo(ImportAction.SKIP);
        assertThat(skip.getGlobalImageBuildDomainWhitelist().getPrev()).isNotNull();
        assertThat(skip.getGlobalImageBuildDomainWhitelist().getNext()).isNull();

        // FAIL_IF_EXISTS → FAIL
        ImportConfigPreview fail = configTransferService.getImportConfigPreview(zipFile, ConflictResolutionPolicy.FAIL_IF_EXISTS);
        assertPreviewAction(fail, ImportAction.FAIL);
        assertThat(fail.getGlobalImageBuildDomainWhitelist()).isNotNull();
        assertThat(fail.getGlobalImageBuildDomainWhitelist().getAction()).isEqualTo(ImportAction.FAIL);
        assertThat(fail.getGlobalImageBuildDomainWhitelist().getPrev()).isNotNull();
        assertThat(fail.getGlobalImageBuildDomainWhitelist().getNext()).isNotNull();
    }

    @Test
    void importPreview_handlesEmptyAndInvalidInput() throws Exception {
        // Empty config → all lists empty, no whitelist
        String emptyJson = jsonMapper.writeValueAsString(ExportConfig.builder().build());
        byte[] emptyZipBytes = ConfigExportImportTestHelper.buildZipFromJson(configExportProperties.getFileName(), emptyJson);
        MultipartFile emptyZipFile = ConfigExportImportTestHelper.createZipMultipartFile("empty-export.zip", emptyZipBytes);

        ImportConfigPreview preview = configTransferService.getImportConfigPreview(emptyZipFile, ConflictResolutionPolicy.OVERWRITE);
        assertThat(preview.getMcpImageDefinitions()).isEmpty();
        assertThat(preview.getAdapterImageDefinitions()).isEmpty();
        assertThat(preview.getInterceptorImageDefinitions()).isEmpty();
        assertThat(preview.getMcpDeployments()).isEmpty();
        assertThat(preview.getAdapterDeployments()).isEmpty();
        assertThat(preview.getInterceptorDeployments()).isEmpty();
        assertThat(preview.getNimDeployments()).isEmpty();
        assertThat(preview.getInferenceDeployments()).isEmpty();
        assertThat(preview.getGlobalImageBuildDomainWhitelist()).isNull();

        // Not a ZIP → IllegalArgumentException
        byte[] textBytes = "not a zip file".getBytes(StandardCharsets.UTF_8);
        MultipartFile textFile = ConfigExportImportTestHelper.createZipMultipartFile("not-a-zip.txt", textBytes);
        assertThatThrownBy(() ->
                configTransferService.getImportConfigPreview(textFile, ConflictResolutionPolicy.OVERWRITE))
                .isInstanceOf(IllegalArgumentException.class);

        // ZIP without recognized config file → IllegalArgumentException
        byte[] badZipBytes = ConfigExportImportTestHelper.buildZipFromJson("unknown-config.json", "{}");
        MultipartFile badZipFile = ConfigExportImportTestHelper.createZipMultipartFile("export.zip", badZipBytes);
        assertThatThrownBy(() ->
                configTransferService.getImportConfigPreview(badZipFile, ConflictResolutionPolicy.OVERWRITE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No valid export configuration file");
    }

    private MultipartFile buildImportZipFromResource() throws Exception {
        String exportConfigJson = ResourceUtils.readResource(EXPECTED_EXPORT_CONFIG_RESOURCE);
        byte[] zipBytes = ConfigExportImportTestHelper.buildZipFromJson(configExportProperties.getFileName(), exportConfigJson);
        return ConfigExportImportTestHelper.createZipMultipartFile("export.zip", zipBytes);
    }

    private void assertPreviewAction(ImportConfigPreview preview, ImportAction expectedAction) {
        assertComponentListAction(preview.getMcpImageDefinitions(), expectedAction, "mcpImageDefinitions");
        assertComponentListAction(preview.getAdapterImageDefinitions(), expectedAction, "adapterImageDefinitions");
        assertComponentListAction(preview.getInterceptorImageDefinitions(), expectedAction, "interceptorImageDefinitions");
        assertComponentListAction(preview.getMcpDeployments(), expectedAction, "mcpDeployments");
        assertComponentListAction(preview.getAdapterDeployments(), expectedAction, "adapterDeployments");
        assertComponentListAction(preview.getInterceptorDeployments(), expectedAction, "interceptorDeployments");
        assertComponentListAction(preview.getNimDeployments(), expectedAction, "nimDeployments");
        assertComponentListAction(preview.getInferenceDeployments(), expectedAction, "inferenceDeployments");
    }

    private <T> void assertComponentListAction(List<ImportComponent<T>> components, ImportAction expectedAction, String listName) {
        assertThat(components).as(listName + " should not be empty").isNotEmpty();
        for (ImportComponent<T> component : components) {
            assertThat(component.getAction()).as(listName + " component action").isEqualTo(expectedAction);
            switch (expectedAction) {
                case CREATE -> {
                    assertThat(component.getPrev()).as(listName + " prev for CREATE").isNull();
                    assertThat(component.getNext()).as(listName + " next for CREATE").isNotNull();
                }
                case UPDATE, FAIL -> {
                    assertThat(component.getPrev()).as(listName + " prev for " + expectedAction).isNotNull();
                    assertThat(component.getNext()).as(listName + " next for " + expectedAction).isNotNull();
                }
                case SKIP -> {
                    assertThat(component.getPrev()).as(listName + " prev for SKIP").isNotNull();
                    assertThat(component.getNext()).as(listName + " next for SKIP").isNull();
                }
                default -> throw new IllegalArgumentException("Unhandled action: " + expectedAction);
            }
        }
    }

    private static ExportComponentInfoDto findById(List<ExportComponentInfoDto> components, String id) {
        return components.stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Component with id '%s' not found".formatted(id)));
    }

    private record ExportTestData(UUID firstMcpImageDefId, UUID secondMcpImageDefId, UUID adapterImageDefId, UUID interceptorImageDefId) { }
}
