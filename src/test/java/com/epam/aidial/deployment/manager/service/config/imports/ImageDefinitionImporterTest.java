package com.epam.aidial.deployment.manager.service.config.imports;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.configuration.export.ImageDefinitionExportMixIn;
import com.epam.aidial.deployment.manager.model.AdapterImageDefinition;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.McpTransportType;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageDefinitionImporterTest {

    private static final String NAME = "test-mcp";
    private static final String VERSION = "1.0";
    private static final String KEY = "%s(%s)".formatted(NAME, VERSION);
    private static final UUID EXISTING_ID = UUID.randomUUID();

    @Mock
    private ImageDefinitionService imageDefinitionService;

    @Spy
    private JsonMapper exportJsonMapper = createExportJsonMapper();

    @InjectMocks
    private ImageDefinitionImporter imageDefinitionImporter;

    private static JsonMapper createExportJsonMapper() {
        JsonMapper mapper = JsonMapperConfiguration.createPrettyJsonMapper();
        mapper.addMixIn(ImageDefinition.class, ImageDefinitionExportMixIn.class);
        return mapper;
    }

    @Test
    void importImageDefinitions_notExists_createsImageDefinition() {
        McpImageDefinition imported = McpImageDefinition.builder()
                .name(NAME)
                .version(VERSION)
                .transportType(McpTransportType.LOCAL)
                .build();
        ExportConfig config = new ExportConfig();
        config.getMcpImageDefinitions().put(KEY, imported);

        when(imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(ImageType.MCP, NAME, VERSION))
                .thenReturn(Optional.empty());

        imageDefinitionImporter.importImageDefinitions(config, ConflictResolutionPolicy.OVERWRITE);

        ArgumentCaptor<ImageDefinition> captor = ArgumentCaptor.forClass(ImageDefinition.class);
        verify(imageDefinitionService).createImageDefinition(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getName()).isEqualTo(NAME);
        assertThat(captor.getValue().getVersion()).isEqualTo(VERSION);
        verify(imageDefinitionService, never()).updateImageDefinition(any(), any(), any(Boolean.class));
    }

    @Test
    void importImageDefinitions_exists_FAIL_IF_EXISTS_throws() {
        McpImageDefinition imported = McpImageDefinition.builder()
                .name(NAME)
                .version(VERSION)
                .transportType(McpTransportType.LOCAL)
                .build();
        McpImageDefinition existing = McpImageDefinition.builder()
                .id(EXISTING_ID)
                .name(NAME)
                .version(VERSION)
                .transportType(McpTransportType.LOCAL)
                .build();
        ExportConfig config = new ExportConfig();
        config.getMcpImageDefinitions().put(KEY, imported);

        when(imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(ImageType.MCP, NAME, VERSION))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> imageDefinitionImporter.importImageDefinitions(config, ConflictResolutionPolicy.FAIL_IF_EXISTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists")
                .hasMessageContaining(NAME)
                .hasMessageContaining(VERSION);

        verify(imageDefinitionService, never()).createImageDefinition(any());
        verify(imageDefinitionService, never()).updateImageDefinition(any(), any(), any(Boolean.class));
    }

    @Test
    void importImageDefinitions_exists_SKIP_IF_EXISTS_doesNotUpdate() {
        McpImageDefinition imported = McpImageDefinition.builder()
                .name(NAME)
                .version(VERSION)
                .transportType(McpTransportType.LOCAL)
                .build();
        McpImageDefinition existing = McpImageDefinition.builder()
                .id(EXISTING_ID)
                .name(NAME)
                .version(VERSION)
                .transportType(McpTransportType.LOCAL)
                .build();
        ExportConfig config = new ExportConfig();
        config.getMcpImageDefinitions().put(KEY, imported);

        when(imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(ImageType.MCP, NAME, VERSION))
                .thenReturn(Optional.of(existing));

        imageDefinitionImporter.importImageDefinitions(config, ConflictResolutionPolicy.SKIP_IF_EXISTS);

        verify(imageDefinitionService, never()).createImageDefinition(any());
        verify(imageDefinitionService, never()).updateImageDefinition(any(), any(), any(Boolean.class));
    }

    @Test
    void importImageDefinitions_exists_OVERWRITE_updatesWithMerge() {
        McpImageDefinition imported = McpImageDefinition.builder()
                .name(NAME)
                .version(VERSION)
                .description("new-desc")
                .transportType(McpTransportType.REMOTE)
                .build();
        McpImageDefinition existing = McpImageDefinition.builder()
                .id(EXISTING_ID)
                .name(NAME)
                .version(VERSION)
                .author("existing-author")
                .transportType(McpTransportType.LOCAL)
                .build();
        ExportConfig config = new ExportConfig();
        config.getMcpImageDefinitions().put(KEY, imported);

        when(imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(ImageType.MCP, NAME, VERSION))
                .thenReturn(Optional.of(existing));

        imageDefinitionImporter.importImageDefinitions(config, ConflictResolutionPolicy.OVERWRITE);

        verify(imageDefinitionService, never()).createImageDefinition(any());
        ArgumentCaptor<ImageDefinition> updateCaptor = ArgumentCaptor.forClass(ImageDefinition.class);
        verify(imageDefinitionService).updateImageDefinition(eq(EXISTING_ID), updateCaptor.capture(), eq(true));
        assertThat(updateCaptor.getValue().getAuthor()).isEqualTo("existing-author");
        assertThat(updateCaptor.getValue().getDescription()).isEqualTo("new-desc");
    }

    @Test
    void importImageDefinitions_emptyMaps_noCalls() {
        ExportConfig config = ExportConfig.builder().build();

        imageDefinitionImporter.importImageDefinitions(config, ConflictResolutionPolicy.OVERWRITE);

        verify(imageDefinitionService, never()).getImageDefinitionByTypeAndNameAndVersion(any(), any(), any());
        verify(imageDefinitionService, never()).createImageDefinition(any());
        verify(imageDefinitionService, never()).updateImageDefinition(any(), any(), any(Boolean.class));
    }

    @Test
    void importImageDefinitions_multipleTypes_importsAllMaps() {
        McpImageDefinition mcp = McpImageDefinition.builder()
                .name("mcp")
                .version("1")
                .transportType(McpTransportType.LOCAL)
                .build();
        McpImageDefinition mcp2 = McpImageDefinition.builder()
                .name("mcp")
                .version("2")
                .transportType(McpTransportType.LOCAL)
                .build();
        InterceptorImageDefinition interceptor = InterceptorImageDefinition.builder()
                .name("interceptor")
                .version("1")
                .build();
        AdapterImageDefinition adapter = AdapterImageDefinition.builder()
                .name("adapter")
                .version("1")
                .build();
        ExportConfig config = new ExportConfig();
        config.getMcpImageDefinitions().put("mcp(1)", mcp);
        config.getMcpImageDefinitions().put("mcp(2)", mcp2);
        config.getInterceptorImageDefinitions().put("interceptor(1)", interceptor);
        config.getAdapterImageDefinitions().put("adapter(1)", adapter);

        when(imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(ImageType.MCP, "mcp", "1"))
                .thenReturn(Optional.empty());
        when(imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(ImageType.MCP, "mcp", "2"))
                .thenReturn(Optional.empty());
        when(imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(ImageType.INTERCEPTOR, "interceptor", "1"))
                .thenReturn(Optional.empty());
        when(imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(ImageType.ADAPTER, "adapter", "1"))
                .thenReturn(Optional.empty());

        imageDefinitionImporter.importImageDefinitions(config, ConflictResolutionPolicy.OVERWRITE);

        verify(imageDefinitionService, times(2)).createImageDefinition(any(McpImageDefinition.class));
        verify(imageDefinitionService).createImageDefinition(any(InterceptorImageDefinition.class));
        verify(imageDefinitionService).createImageDefinition(any(AdapterImageDefinition.class));
    }
}
