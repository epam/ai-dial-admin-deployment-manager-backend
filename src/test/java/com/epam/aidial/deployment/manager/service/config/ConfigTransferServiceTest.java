package com.epam.aidial.deployment.manager.service.config;

import com.epam.aidial.deployment.manager.configuration.ConfigExportProperties;
import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.config.ExportRequest;
import com.epam.aidial.deployment.manager.model.config.SelectedItemsExportRequest;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigTransferServiceTest {

    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String ZIP_NAME = "export.zip";

    @Mock
    private ConfigExporter configExporter;
    @Mock
    private ConfigImporter configImporter;

    private JsonMapper jsonMapper;
    private ConfigTransferService configTransferService;

    @BeforeEach
    void setUp() {
        var properties = new ConfigExportProperties();
        properties.setFileName(CONFIG_FILE_NAME);
        properties.setZipName(ZIP_NAME);

        var exportJsonMapper = JsonMapperConfiguration.createPrettyJsonMapper();
        jsonMapper = JsonMapperConfiguration.createJsonMapper();

        configTransferService = new ConfigTransferService(
                properties,
                configExporter,
                configImporter,
                exportJsonMapper,
                jsonMapper
        );
    }

    @Test
    void exportConfig_returnsStreamingBody_thatWritesZipWithSingleEntry() throws IOException {
        // Given
        ExportConfig config = ExportConfig.builder().build();
        when(configExporter.getConfig(any(SelectedItemsExportRequest.class))).thenReturn(config);

        SelectedItemsExportRequest req = new SelectedItemsExportRequest(null);
        req.setAddSecrets(false);
        req.setAddGlobalImageBuildDomainWhitelist(false);

        // When
        StreamingResponseBody body = configTransferService.exportConfig(req);

        // Then
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        body.writeTo(baos);
        byte[] zipBytes = baos.toByteArray();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zis.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo(CONFIG_FILE_NAME);
            assertThat(zis.getNextEntry()).isNull();
        }
    }

    @Test
    void exportConfig_zipEntryContent_utf8Encoded() throws IOException {
        // Given
        ExportConfig config = ExportConfig.builder().build();
        when(configExporter.getConfig(any(SelectedItemsExportRequest.class))).thenReturn(config);

        SelectedItemsExportRequest req = new SelectedItemsExportRequest(null);
        req.setAddSecrets(false);
        req.setAddGlobalImageBuildDomainWhitelist(false);

        // When
        StreamingResponseBody body = configTransferService.exportConfig(req);

        // Then
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        body.writeTo(baos);
        byte[] zipBytes = baos.toByteArray();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            zis.getNextEntry();
            byte[] content = zis.readAllBytes();
            String asString = new String(content, StandardCharsets.UTF_8);
            assertThat(asString).contains("\"mcpImageDefinitions\"");
        }
    }

    @Test
    void exportConfig_exporterBuildsConfig_configWrittenToZip() throws IOException {
        // Given
        ExportConfig expected = ExportConfig.builder().build();
        expected.getGlobalImageBuildDomainWhitelist().add("x.com");
        when(configExporter.getConfig(any(SelectedItemsExportRequest.class))).thenReturn(expected);

        SelectedItemsExportRequest req = new SelectedItemsExportRequest(null);
        req.setAddSecrets(false);
        req.setAddGlobalImageBuildDomainWhitelist(true);

        // When
        StreamingResponseBody body = configTransferService.exportConfig(req);

        // Then
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        body.writeTo(baos);

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            zis.getNextEntry();
            ExportConfig parsed = jsonMapper.readValue(zis, ExportConfig.class);
            assertThat(parsed.getGlobalImageBuildDomainWhitelist()).containsExactly("x.com");
        }
    }

    @Test
    void importConfig_validZip_callsImporterWithParsedConfig() throws IOException {
        // Given
        ExportConfig config = ExportConfig.builder().build();
        config.getGlobalImageBuildDomainWhitelist().add("a.com");
        byte[] zipBytes = ConfigExportImportTestHelper.buildZipFromExportConfig(
                CONFIG_FILE_NAME, config, jsonMapper
        );
        MultipartFile multipartFile = ConfigExportImportTestHelper.createZipMultipartFile(ZIP_NAME, zipBytes);

        // When
        configTransferService.importConfig(multipartFile, ConflictResolutionPolicy.OVERWRITE);

        // Then
        verify(configImporter).importConfig(any(ExportConfig.class), eq(ConflictResolutionPolicy.OVERWRITE));
    }

    @Test
    void importConfig_shouldNotCallImporterIfUnknownFileName() throws IOException {
        // Given
        ExportConfig config = ExportConfig.builder().build();

        byte[] zipBytes = ConfigExportImportTestHelper.buildZipFromExportConfig(
                "unknown-config.json", config, jsonMapper
        );
        MultipartFile multipartFile = ConfigExportImportTestHelper.createZipMultipartFile(ZIP_NAME, zipBytes);

        // When
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> configTransferService.importConfig(multipartFile, ConflictResolutionPolicy.OVERWRITE)
        );

        // Then
        assertThat(exception.getMessage()).isEqualTo("No valid export configuration file 'config.json' found in the ZIP archive.");
        verify(configImporter, never()).importConfig(any(ExportConfig.class), any(ConflictResolutionPolicy.class));
    }

    @Test
    void exportConfig_unsupportedRequestType_throws() {
        assertThatThrownBy(() -> configTransferService.exportConfig(new OtherExportRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported export request type");
    }

    private static class OtherExportRequest extends ExportRequest { }
}
