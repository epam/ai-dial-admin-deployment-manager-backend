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
        ExportConfig config = configExporter.getConfig(customExportRequest);

        return outputStream -> {
            try (var zos = new ZipOutputStream(outputStream)) {
                zos.putNextEntry(new ZipEntry(properties.getFileName()));
                zos.write(exportJsonMapper.writeValueAsString(config).getBytes());
                zos.closeEntry();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public void importConfig(MultipartFile zipFile, ConflictResolutionPolicy resolutionPolicy) {
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
        } catch (IOException ioException) {
            if (ioException.getMessage().startsWith("Stream closed")) {
                log.debug("Stream is already closed");
            }
        } catch (Exception ex) {
            log.debug("Config file {} import failed", zipFile.getOriginalFilename(), ex);
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }
}
