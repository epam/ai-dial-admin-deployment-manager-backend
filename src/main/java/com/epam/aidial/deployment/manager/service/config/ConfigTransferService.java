package com.epam.aidial.deployment.manager.service.config;

import com.epam.aidial.deployment.manager.configuration.ConfigExportProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.config.ExportRequest;
import com.epam.aidial.deployment.manager.model.config.ImportConfigPreview;
import com.epam.aidial.deployment.manager.model.config.SelectedItemsExportRequest;
import com.epam.aidial.deployment.manager.service.config.previews.ConfigImportPreviewer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class ConfigTransferService {

    private final ConfigExportProperties properties;
    private final ConfigExporter configExporter;
    private final ConfigImporter configImporter;
    private final ConfigImportPreviewer configImportPreviewer;

    @Qualifier("exportJsonMapper")
    private final JsonMapper exportJsonMapper;
    private final JsonMapper jsonMapper;

    @Transactional(readOnly = true)
    public StreamingResponseBody exportConfig(ExportRequest request) {
        if (!(request instanceof SelectedItemsExportRequest customExportRequest)) {
            throw new IllegalArgumentException("Unsupported export request type: " + request.getClass());
        }

        int componentsSize = customExportRequest.getComponents() != null ? customExportRequest.getComponents().size() : 0;
        log.info("Exporting config (components={})", componentsSize);
        ExportConfig config = configExporter.getConfig(customExportRequest);

        return outputStream -> {
            try (var zos = new ZipOutputStream(outputStream)) {
                zos.putNextEntry(new ZipEntry(properties.getFileName()));
                zos.write(exportJsonMapper.writeValueAsString(config).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                log.info("Export config stream written successfully");
            } catch (IOException e) {
                log.warn("Export config stream failed", e);
                throw new RuntimeException(e);
            }
        };
    }

    @Transactional(readOnly = true)
    public ExportConfig getExportConfig(ExportRequest request) {
        if (!(request instanceof SelectedItemsExportRequest customExportRequest)) {
            throw new IllegalArgumentException("Unsupported export request type: " + request.getClass());
        }
        return configExporter.getConfig(customExportRequest);
    }

    public void importConfig(MultipartFile zipFile, ConflictResolutionPolicy resolutionPolicy) {
        ExportConfig config = parseAndSanitizeExportConfig(zipFile);
        importConfig(config, resolutionPolicy);
    }

    public void importConfig(ExportConfig config, ConflictResolutionPolicy resolutionPolicy) {
        try {
            configImporter.importConfig(config, resolutionPolicy);
            log.info("Config import completed successfully");
        } catch (Exception ex) {
            log.warn("Config import failed", ex);
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public ImportConfigPreview getImportConfigPreview(MultipartFile zipFile, ConflictResolutionPolicy resolutionPolicy) {
        ExportConfig config = parseAndSanitizeExportConfig(zipFile);
        return getImportConfigPreview(config, resolutionPolicy);
    }

    @Transactional(readOnly = true)
    public ImportConfigPreview getImportConfigPreview(ExportConfig config, ConflictResolutionPolicy resolutionPolicy) {
        return configImportPreviewer.previewImport(config, resolutionPolicy);
    }

    public ExportConfig parseAndSanitizeExportConfig(MultipartFile zipFile) {
        try {
            ExportConfig config = parseExportConfig(zipFile);
            return sanitizeExportConfig(config);
        } catch (Exception ex) {
            log.warn("Config parse/sanitize failed", ex);
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private ExportConfig parseExportConfig(MultipartFile zipFile) throws Exception {
        String fileName = zipFile.getOriginalFilename() != null ? zipFile.getOriginalFilename() : "unknown";
        log.info("Parsing config from file '{}'", fileName);
        try (var zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipFile.getBytes()))) {
            ZipEntry entry;
            int validEntryCount = 0;
            ExportConfig result = null;
            var exportConfigFileName = properties.getFileName();
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().equals(exportConfigFileName)) {
                    validEntryCount++;
                    if (validEntryCount > 1) {
                        throw new IllegalArgumentException("Multiple files '%s' with data found in the ZIP archive."
                                .formatted(exportConfigFileName));
                    }
                    result = jsonMapper.readValue(zipInputStream, ExportConfig.class);
                } else {
                    log.info("Ignoring file {} in zip archive during import config parsing", entry.getName());
                }
                zipInputStream.closeEntry();
            }
            if (validEntryCount == 0 || result == null) {
                throw new IllegalArgumentException("No valid export configuration file '%s' found in the ZIP archive."
                        .formatted(exportConfigFileName));
            }
            return result;
        }
    }

    private ExportConfig sanitizeExportConfig(ExportConfig config) throws Exception {
        return exportJsonMapper.readValue(exportJsonMapper.writeValueAsBytes(config), ExportConfig.class);
    }
}
