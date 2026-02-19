package com.epam.aidial.deployment.manager.service.config;

import com.epam.aidial.deployment.manager.configuration.ConfigExportProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.config.ExportRequest;
import com.epam.aidial.deployment.manager.model.config.SelectedItemsExportRequest;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
                log.warn("Export config stream failed: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        };
    }

    public void importConfig(MultipartFile zipFile, ConflictResolutionPolicy resolutionPolicy) {
        String fileName = zipFile.getOriginalFilename() != null ? zipFile.getOriginalFilename() : "unknown";
        log.info("Importing config from file '{}' (resolutionPolicy={})", fileName, resolutionPolicy);
        try (var zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipFile.getBytes()))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().equals(properties.getFileName())) {
                    ExportConfig config = jsonMapper.readValue(zipInputStream, ExportConfig.class);
                    configImporter.importConfig(config, resolutionPolicy);
                } else {
                    log.info("Ignoring file {} in zip archive during import", entry.getName());
                }
                zipInputStream.closeEntry();
            }
            log.info("Config import completed successfully");
        } catch (Exception ex) {
            if (ex instanceof IOException && ex.getMessage() != null && ex.getMessage().startsWith("Stream closed")) {
                log.debug("Stream is already closed");
                return;
            }
            log.warn("Config import failed: {}", ex.getMessage(), ex);
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }
}
