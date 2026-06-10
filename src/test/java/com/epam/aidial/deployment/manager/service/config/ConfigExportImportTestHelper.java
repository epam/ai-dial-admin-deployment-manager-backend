package com.epam.aidial.deployment.manager.service.config;

import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Test helper for building ZIP and MultipartFile for config export/import tests.
 */
public final class ConfigExportImportTestHelper {

    private ConfigExportImportTestHelper() { }

    /**
     * Builds a ZIP file containing a single entry with the given filename and JSON content.
     */
    public static byte[] buildZipFromJson(String entryFileName, String jsonContent) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryFileName));
            zos.write(jsonContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    /**
     * Builds a ZIP file containing the given ExportConfig as JSON under the given entry filename.
     */
    public static byte[] buildZipFromExportConfig(String entryFileName, ExportConfig config, JsonMapper mapper) throws IOException {
        String json = mapper.writeValueAsString(config);
        return buildZipFromJson(entryFileName, json);
    }

    /**
     * Creates a MockMultipartFile for import tests from the given ZIP bytes.
     */
    public static MultipartFile createZipMultipartFile(String originalFilename, byte[] zipBytes) {
        return new MockMultipartFile(
                "file",
                originalFilename,
                "application/zip",
                zipBytes
        );
    }
}
