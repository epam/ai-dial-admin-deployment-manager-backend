package com.epam.aidial.deployment.manager.web.controller.none;

import com.epam.aidial.deployment.manager.configuration.ConfigExportProperties;
import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.config.ImportConfigPreview;
import com.epam.aidial.deployment.manager.model.config.ImportValidationError;
import com.epam.aidial.deployment.manager.model.config.SelectedItemsExportRequest;
import com.epam.aidial.deployment.manager.service.config.ConfigTransferService;
import com.epam.aidial.deployment.manager.web.controller.ConfigController;
import com.epam.aidial.deployment.manager.web.dto.config.ExportComponentInfoDto;
import com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentTypeDto;
import com.epam.aidial.deployment.manager.web.dto.config.ExportConfigPreviewDto;
import com.epam.aidial.deployment.manager.web.dto.config.ImportConfigPreviewDto;
import com.epam.aidial.deployment.manager.web.mapper.ExportConfigMapper;
import com.epam.aidial.deployment.manager.web.mapper.ImportConfigDtoMapper;
import com.epam.aidial.deployment.manager.web.validation.ImportConfigValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ConfigController.class)
@Import({JsonMapperConfiguration.class})
class ConfigControllerTest extends AbstractControllerNoneSecureTest {

    @MockitoBean
    private ConfigExportProperties configExportProperties;
    @MockitoBean
    private ConfigTransferService configTransferService;
    @MockitoBean
    private ExportConfigMapper exportConfigMapper;
    @MockitoBean
    private ImportConfigDtoMapper importConfigDtoMapper;
    @MockitoBean
    private ImportConfigValidator importConfigValidator;

    @Test
    void previewConfig_validRequest_returns200WithPreview() throws Exception {
        // Given
        var previewDto = new ExportConfigPreviewDto(
                List.of("domain.com"),
                List.of(new ExportComponentInfoDto("img-1", "MCP Image", "1.0",
                        "desc", ExportConfigComponentTypeDto.MCP_IMAGE_DEFINITION)),
                List.of(new ExportComponentInfoDto("dep-1", "MCP Dep", null,
                        "dep desc", ExportConfigComponentTypeDto.MCP_DEPLOYMENT))
        );

        when(exportConfigMapper.toExportRequest(any())).thenReturn(new SelectedItemsExportRequest());
        when(configTransferService.getExportConfig(any())).thenReturn(new ExportConfig());
        when(exportConfigMapper.toExportConfigPreviewDto(any())).thenReturn(previewDto);

        String requestBody = """
                {
                    "$type": "custom",
                    "addSecrets": false,
                    "addGlobalImageBuildDomainWhitelist": true,
                    "components": [
                        {"name": "mcp-image", "type": "MCP_IMAGE_DEFINITION"}
                    ]
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/v1/configs/export/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalImageBuildDomainWhitelist[0]").value("domain.com"))
                .andExpect(jsonPath("$.imageDefinitions[0].id").value("img-1"))
                .andExpect(jsonPath("$.imageDefinitions[0].type").value("mcp_image_definition"))
                .andExpect(jsonPath("$.deployments[0].id").value("dep-1"))
                .andExpect(jsonPath("$.deployments[0].type").value("mcp_deployment"));
    }

    @Test
    void previewConfig_emptyComponents_returns200() throws Exception {
        // Given — verifies that empty components are accepted
        var emptyPreview = new ExportConfigPreviewDto(List.of(), List.of(), List.of());

        when(exportConfigMapper.toExportRequest(any())).thenReturn(new SelectedItemsExportRequest());
        when(configTransferService.getExportConfig(any())).thenReturn(new ExportConfig());
        when(exportConfigMapper.toExportConfigPreviewDto(any())).thenReturn(emptyPreview);

        String requestBody = """
                {
                    "$type": "custom",
                    "addSecrets": false,
                    "addGlobalImageBuildDomainWhitelist": false,
                    "components": []
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/v1/configs/export/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageDefinitions").isEmpty())
                .andExpect(jsonPath("$.deployments").isEmpty())
                .andExpect(jsonPath("$.globalImageBuildDomainWhitelist").isEmpty());
    }

    @Test
    void previewConfig_invalidComponent_returns400() throws Exception {
        // Given — component with blank name should be rejected by @NotBlank
        String requestBody = """
                {
                    "$type": "custom",
                    "addSecrets": false,
                    "addGlobalImageBuildDomainWhitelist": false,
                    "components": [
                        {"name": "", "type": "MCP_IMAGE_DEFINITION"}
                    ]
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/v1/configs/export/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewImport_validFile_returns200WithPreviewAndEmptyValidationErrors() throws Exception {
        // Given
        var previewDto = new ImportConfigPreviewDto(
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, List.of()
        );

        when(configTransferService.parseAndSanitizeExportConfig(any())).thenReturn(new ExportConfig());
        when(configTransferService.getImportConfigPreview(any(ExportConfig.class), any())).thenReturn(ImportConfigPreview.builder().build());
        when(importConfigValidator.collectErrors(any())).thenReturn(List.of());
        when(importConfigDtoMapper.toImportConfigPreviewDto(any())).thenReturn(previewDto);

        var file = new MockMultipartFile("file", "export.zip", "application/zip", new byte[]{1, 2, 3});

        // When & Then
        mockMvc.perform(multipart("/api/v1/configs/import/preview")
                        .file(file)
                        .param("resolutionPolicy", "OVERWRITE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationErrors").isEmpty());
    }

    @Test
    void previewImport_invalidEntities_returns200WithValidationErrors() throws Exception {
        // Given
        var validationErrors = List.of(
                new ImportValidationError("MCP_DEPLOYMENT", "bad-dep", "name", "must match pattern"),
                new ImportValidationError("MCP_IMAGE_DEFINITION", "bad-img", "version", "invalid version")
        );
        var previewDto = new ImportConfigPreviewDto(
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, validationErrors
        );

        when(configTransferService.parseAndSanitizeExportConfig(any())).thenReturn(new ExportConfig());
        when(configTransferService.getImportConfigPreview(any(ExportConfig.class), any())).thenReturn(ImportConfigPreview.builder().build());
        when(importConfigValidator.collectErrors(any())).thenReturn(validationErrors);
        when(importConfigDtoMapper.toImportConfigPreviewDto(any())).thenReturn(previewDto);

        var file = new MockMultipartFile("file", "export.zip", "application/zip", new byte[]{1, 2, 3});

        // When & Then
        mockMvc.perform(multipart("/api/v1/configs/import/preview")
                        .file(file)
                        .param("resolutionPolicy", "OVERWRITE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationErrors").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.length()").value(2))
                .andExpect(jsonPath("$.validationErrors[0].entityType").value("MCP_DEPLOYMENT"))
                .andExpect(jsonPath("$.validationErrors[0].entityIdentifier").value("bad-dep"))
                .andExpect(jsonPath("$.validationErrors[0].fieldPath").value("name"))
                .andExpect(jsonPath("$.validationErrors[1].entityType").value("MCP_IMAGE_DEFINITION"));
    }

    @Test
    void previewImport_missingResolutionPolicy_returns400() throws Exception {
        var file = new MockMultipartFile("file", "export.zip", "application/zip", new byte[]{1, 2, 3});

        // When & Then — missing required resolutionPolicy param
        mockMvc.perform(multipart("/api/v1/configs/import/preview")
                        .file(file))
                .andExpect(status().isBadRequest());
    }
}
