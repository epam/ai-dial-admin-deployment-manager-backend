package com.epam.aidial.deployment.manager.service.config;

import com.epam.aidial.deployment.manager.model.AdapterImageDefinition;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.McpTransport;
import com.epam.aidial.deployment.manager.model.McpTransportType;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.config.ExportConfigComponent;
import com.epam.aidial.deployment.manager.model.config.ExportConfigComponentType;
import com.epam.aidial.deployment.manager.model.config.SelectedItemsExportRequest;
import com.epam.aidial.deployment.manager.model.deployment.AdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeploymentHuggingFaceSource;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeploymentNgcRegistrySource;
import com.epam.aidial.deployment.manager.service.GlobalDomainWhitelistService;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigExporterTest {

    private static final String IMAGE_DEFINITION_KEY_FORMAT = "%s(%s)";

    private static final UUID MCP_IMAGE_ID = UUID.randomUUID();
    private static final UUID ADAPTER_IMAGE_ID = UUID.randomUUID();
    private static final UUID INTERCEPTOR_IMAGE_ID = UUID.randomUUID();
    private static final String IMAGE_VERSION = "1.0";
    private static final String MCP_NAME = "test-mcp";
    private static final String ADAPTER_NAME = "test-adapter";
    private static final String INTERCEPTOR_NAME = "test-interceptor";
    private static final String DEPLOYMENT_ID = "deployment-1";

    @Mock
    private ImageDefinitionService imageDefinitionService;
    @Mock
    private DeploymentService deploymentService;
    @Mock
    private ExportSanitizer exportSanitizer;
    @Mock
    private GlobalDomainWhitelistService globalDomainWhitelistService;

    @InjectMocks
    private ConfigExporter configExporter;

    @Test
    void getConfig_emptyComponents_returnsEmptyConfig() {
        SelectedItemsExportRequest request = new SelectedItemsExportRequest();
        request.setAddSecrets(false);
        request.setAddGlobalImageBuildDomainWhitelist(false);
        request.setComponents(List.of());

        ExportConfig config = configExporter.getConfig(request);

        assertThat(config.getMcpImageDefinitions()).isEmpty();
        assertThat(config.getMcpDeployments()).isEmpty();
        verify(imageDefinitionService, never()).getImageDefinition(any());
        verify(deploymentService, never()).getDeployment(any(), any(Boolean.class));
    }

    @Test
    void getConfig_emptyComponents_addGlobalWhitelistTrue_addsWhitelist() {
        SelectedItemsExportRequest request = new SelectedItemsExportRequest();
        request.setAddSecrets(false);
        request.setAddGlobalImageBuildDomainWhitelist(true);
        request.setComponents(List.of());
        when(globalDomainWhitelistService.getDomainWhitelist()).thenReturn(List.of("a.com"));

        ExportConfig config = configExporter.getConfig(request);

        assertThat(config.getGlobalImageBuildDomainWhitelist()).containsExactly("a.com");
        verify(globalDomainWhitelistService).getDomainWhitelist();
    }

    @Test
    void getConfig_imageDefinitionComponents_loadsAndAddsImageDefinitions() {
        // Given
        McpImageDefinition mcp = McpImageDefinition.builder()
                .id(MCP_IMAGE_ID)
                .name(MCP_NAME)
                .version(IMAGE_VERSION)
                .transportType(McpTransportType.LOCAL)
                .build();
        AdapterImageDefinition adapter = AdapterImageDefinition.builder()
                .id(ADAPTER_IMAGE_ID)
                .name(ADAPTER_NAME)
                .version(IMAGE_VERSION)
                .build();
        InterceptorImageDefinition interceptor = InterceptorImageDefinition.builder()
                .id(INTERCEPTOR_IMAGE_ID)
                .name(INTERCEPTOR_NAME)
                .version(IMAGE_VERSION)
                .build();

        SelectedItemsExportRequest request = getSelectedItemsExportRequest();

        when(imageDefinitionService.getImageDefinition(MCP_IMAGE_ID)).thenReturn(Optional.of(mcp));
        when(imageDefinitionService.getImageDefinition(ADAPTER_IMAGE_ID)).thenReturn(Optional.of(adapter));
        when(imageDefinitionService.getImageDefinition(INTERCEPTOR_IMAGE_ID)).thenReturn(Optional.of(interceptor));

        // When
        ExportConfig config = configExporter.getConfig(request);

        // Then
        String mcpKey = IMAGE_DEFINITION_KEY_FORMAT.formatted(MCP_NAME, IMAGE_VERSION);
        String adapterKey = IMAGE_DEFINITION_KEY_FORMAT.formatted(ADAPTER_NAME, IMAGE_VERSION);
        String interceptorKey = IMAGE_DEFINITION_KEY_FORMAT.formatted(INTERCEPTOR_NAME, IMAGE_VERSION);

        assertThat(config.getMcpImageDefinitions()).containsKey(mcpKey);
        assertThat(config.getMcpImageDefinitions().get(mcpKey)).isSameAs(mcp);
        assertThat(config.getAdapterImageDefinitions()).containsKey(adapterKey);
        assertThat(config.getAdapterImageDefinitions().get(adapterKey)).isSameAs(adapter);
        assertThat(config.getInterceptorImageDefinitions()).containsKey(interceptorKey);
        assertThat(config.getInterceptorImageDefinitions().get(interceptorKey)).isSameAs(interceptor);

        verify(imageDefinitionService).getImageDefinition(MCP_IMAGE_ID);
        verify(imageDefinitionService).getImageDefinition(ADAPTER_IMAGE_ID);
        verify(imageDefinitionService).getImageDefinition(INTERCEPTOR_IMAGE_ID);
    }

    private static SelectedItemsExportRequest getSelectedItemsExportRequest() {
        SelectedItemsExportRequest request = new SelectedItemsExportRequest();
        request.setAddSecrets(false);
        request.setAddGlobalImageBuildDomainWhitelist(false);
        request.setComponents(List.of(
                new ExportConfigComponent(MCP_IMAGE_ID.toString(), ExportConfigComponentType.MCP_IMAGE_DEFINITION),
                new ExportConfigComponent(ADAPTER_IMAGE_ID.toString(), ExportConfigComponentType.ADAPTER_IMAGE_DEFINITION),
                new ExportConfigComponent(INTERCEPTOR_IMAGE_ID.toString(), ExportConfigComponentType.INTERCEPTOR_IMAGE_DEFINITION)
        ));
        return request;
    }

    @Test
    void getConfig_imageDefinitionNotFound_skipsEntry() {
        SelectedItemsExportRequest request = new SelectedItemsExportRequest();
        request.setComponents(List.of(
                new ExportConfigComponent(MCP_IMAGE_ID.toString(), ExportConfigComponentType.MCP_IMAGE_DEFINITION)
        ));
        when(imageDefinitionService.getImageDefinition(MCP_IMAGE_ID)).thenReturn(Optional.empty());

        ExportConfig config = configExporter.getConfig(request);

        assertThat(config.getMcpImageDefinitions()).isEmpty();
    }

    @Test
    void getConfig_deploymentComponents_loadsAndAddsAllDeploymentTypes() {
        // Given
        String adapterDepId = "dep-adapter";
        String interceptorDepId = "dep-interceptor";
        String nimDepId = "dep-nim";
        String inferenceDepId = "dep-inference";

        McpDeployment mcpDeployment = McpDeployment.builder()
                .id(DEPLOYMENT_ID)
                .transport(McpTransport.SSE)
                .build();
        AdapterDeployment adapterDeployment = AdapterDeployment.builder()
                .id(adapterDepId)
                .build();
        InterceptorDeployment interceptorDeployment = InterceptorDeployment.builder()
                .id(interceptorDepId)
                .build();
        NimDeployment nimDeployment = NimDeployment.builder()
                .id(nimDepId)
                .source(new NimDeploymentNgcRegistrySource("ngc://img"))
                .build();
        InferenceDeployment inferenceDeployment = InferenceDeployment.builder()
                .id(inferenceDepId)
                .source(new InferenceDeploymentHuggingFaceSource("model-1"))
                .build();

        SelectedItemsExportRequest request = new SelectedItemsExportRequest();
        request.setAddSecrets(true);
        request.setComponents(List.of(
                new ExportConfigComponent(DEPLOYMENT_ID, ExportConfigComponentType.MCP_DEPLOYMENT),
                new ExportConfigComponent(adapterDepId, ExportConfigComponentType.ADAPTER_DEPLOYMENT),
                new ExportConfigComponent(interceptorDepId, ExportConfigComponentType.INTERCEPTOR_DEPLOYMENT),
                new ExportConfigComponent(nimDepId, ExportConfigComponentType.NIM_DEPLOYMENT),
                new ExportConfigComponent(inferenceDepId, ExportConfigComponentType.INFERENCE_DEPLOYMENT)
        ));

        when(deploymentService.getDeployment(DEPLOYMENT_ID, true)).thenReturn(Optional.of(mcpDeployment));
        when(deploymentService.getDeployment(adapterDepId, true)).thenReturn(Optional.of(adapterDeployment));
        when(deploymentService.getDeployment(interceptorDepId, true)).thenReturn(Optional.of(interceptorDeployment));
        when(deploymentService.getDeployment(nimDepId, true)).thenReturn(Optional.of(nimDeployment));
        when(deploymentService.getDeployment(inferenceDepId, true)).thenReturn(Optional.of(inferenceDeployment));
        when(exportSanitizer.sanitizeDeploymentForExport(any(Deployment.class), eq(true)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ExportConfig config = configExporter.getConfig(request);

        // Then
        assertThat(config.getMcpDeployments()).containsKey(DEPLOYMENT_ID);
        assertThat(config.getAdapterDeployments()).containsKey(adapterDepId);
        assertThat(config.getInterceptorDeployments()).containsKey(interceptorDepId);
        assertThat(config.getNimDeployments()).containsKey(nimDepId);
        assertThat(config.getInferenceDeployments()).containsKey(inferenceDepId);

        verify(deploymentService).getDeployment(DEPLOYMENT_ID, true);
        verify(deploymentService).getDeployment(adapterDepId, true);
        verify(deploymentService).getDeployment(interceptorDepId, true);
        verify(deploymentService).getDeployment(nimDepId, true);
        verify(deploymentService).getDeployment(inferenceDepId, true);
        verify(exportSanitizer, times(5)).sanitizeDeploymentForExport(any(Deployment.class), eq(true));
    }

    @Test
    void getConfig_deploymentNotFound_skipsEntry() {
        SelectedItemsExportRequest request = new SelectedItemsExportRequest();
        request.setComponents(List.of(
                new ExportConfigComponent(DEPLOYMENT_ID, ExportConfigComponentType.MCP_DEPLOYMENT)
        ));
        when(deploymentService.getDeployment(DEPLOYMENT_ID, false)).thenReturn(Optional.empty());

        ExportConfig config = configExporter.getConfig(request);

        assertThat(config.getMcpDeployments()).isEmpty();
    }

    @Test
    void getConfig_addSecretsFalse_sanitizerCalledWithFalse() {
        McpDeployment deployment = McpDeployment.builder().id(DEPLOYMENT_ID).transport(McpTransport.SSE).build();
        SelectedItemsExportRequest request = new SelectedItemsExportRequest();
        request.setAddSecrets(false);
        request.setComponents(List.of(new ExportConfigComponent(DEPLOYMENT_ID, ExportConfigComponentType.MCP_DEPLOYMENT)));
        when(deploymentService.getDeployment(DEPLOYMENT_ID, false)).thenReturn(Optional.of(deployment));
        when(exportSanitizer.sanitizeDeploymentForExport(any(Deployment.class), eq(false))).thenReturn(deployment);

        configExporter.getConfig(request);

        verify(exportSanitizer).sanitizeDeploymentForExport(deployment, false);
    }

    @Test
    void getConfig_addSecretsTrue_sanitizerReceivesTrue() {
        McpDeployment deployment = McpDeployment.builder().id(DEPLOYMENT_ID).transport(McpTransport.SSE).build();
        SelectedItemsExportRequest request = new SelectedItemsExportRequest();
        request.setAddSecrets(true);
        request.setComponents(List.of(new ExportConfigComponent(DEPLOYMENT_ID, ExportConfigComponentType.MCP_DEPLOYMENT)));
        when(deploymentService.getDeployment(DEPLOYMENT_ID, true)).thenReturn(Optional.of(deployment));
        when(exportSanitizer.sanitizeDeploymentForExport(any(Deployment.class), eq(true))).thenReturn(deployment);

        configExporter.getConfig(request);

        verify(exportSanitizer).sanitizeDeploymentForExport(deployment, true);
    }

    @Test
    void getConfig_referencedImageDefinition_addedWithDeployment() {
        // Given
        McpImageDefinition imageDef = McpImageDefinition.builder()
                .id(MCP_IMAGE_ID)
                .name(MCP_NAME)
                .version(IMAGE_VERSION)
                .transportType(McpTransportType.LOCAL)
                .build();
        McpDeployment deployment = McpDeployment.builder()
                .id(DEPLOYMENT_ID)
                .transport(McpTransport.SSE)
                .imageDefinitionType(ImageType.MCP)
                .imageDefinitionName(MCP_NAME)
                .imageDefinitionVersion(IMAGE_VERSION)
                .build();

        SelectedItemsExportRequest request = new SelectedItemsExportRequest();
        request.setAddSecrets(false);
        request.setComponents(List.of(new ExportConfigComponent(DEPLOYMENT_ID, ExportConfigComponentType.MCP_DEPLOYMENT)));

        when(deploymentService.getDeployment(DEPLOYMENT_ID, false)).thenReturn(Optional.of(deployment));
        when(exportSanitizer.sanitizeDeploymentForExport(any(), any(Boolean.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(ImageType.MCP, MCP_NAME, IMAGE_VERSION))
                .thenReturn(Optional.of(imageDef));

        // When
        ExportConfig config = configExporter.getConfig(request);

        // Then
        assertThat(config.getMcpImageDefinitions()).containsKey(IMAGE_DEFINITION_KEY_FORMAT.formatted(MCP_NAME, IMAGE_VERSION));
        assertThat(config.getMcpDeployments()).containsKey(DEPLOYMENT_ID);
        verify(imageDefinitionService).getImageDefinitionByTypeAndNameAndVersion(ImageType.MCP, MCP_NAME, IMAGE_VERSION);
    }

    @Test
    void getConfig_nullOrBlankNameOrType_skipsComponent() {
        SelectedItemsExportRequest request = new SelectedItemsExportRequest();
        request.setComponents(List.of(
                new ExportConfigComponent(null, ExportConfigComponentType.MCP_IMAGE_DEFINITION),
                new ExportConfigComponent("  ", ExportConfigComponentType.MCP_IMAGE_DEFINITION),
                new ExportConfigComponent(MCP_IMAGE_ID.toString(), null)
        ));

        ExportConfig config = configExporter.getConfig(request);

        assertThat(config.getMcpImageDefinitions()).isEmpty();
        verify(imageDefinitionService, never()).getImageDefinition(any());
    }

    @Test
    void getConfig_deploymentWithEnvs_populatesMetadataEnvsValuesFromDeploymentEnvs() {
        // Given
        String envName = "MY_VAR";
        String envValue = "my-value";
        EnvVarDefinition envDef = new EnvVarDefinition(envName, null, null, null);
        DeploymentMetadata metadata = new DeploymentMetadata(List.of(envDef));

        SimpleEnvVar envVar = SimpleEnvVar.builder()
                .name(envName)
                .value(new SimpleEnvVarValue(envValue))
                .build();
        McpDeployment deployment = McpDeployment.builder()
                .id(DEPLOYMENT_ID)
                .transport(McpTransport.SSE)
                .metadata(metadata)
                .envs(List.of(envVar))
                .build();

        SelectedItemsExportRequest request = new SelectedItemsExportRequest();
        request.setAddSecrets(true);
        request.setComponents(List.of(new ExportConfigComponent(DEPLOYMENT_ID, ExportConfigComponentType.MCP_DEPLOYMENT)));

        when(deploymentService.getDeployment(DEPLOYMENT_ID, true)).thenReturn(Optional.of(deployment));
        when(exportSanitizer.sanitizeDeploymentForExport(eq(deployment), eq(true))).thenReturn(deployment);

        // When
        ExportConfig config = configExporter.getConfig(request);
        McpDeployment exported = config.getMcpDeployments().get(DEPLOYMENT_ID);

        // Then
        assertThat(exported).isNotNull();
        assertThat(exported.getEnvs()).isNull();
        assertThat(exported.getMetadata()).isNotNull();
        assertThat(exported.getMetadata().getEnvs()).hasSize(1);
        assertThat(exported.getMetadata().getEnvs().getFirst().getName()).isEqualTo(envName);
        assertThat(exported.getMetadata().getEnvs().getFirst().getValue()).isNotNull();
        assertThat(exported.getMetadata().getEnvs().getFirst().getValue().getValue()).isEqualTo(envValue);
    }
}
