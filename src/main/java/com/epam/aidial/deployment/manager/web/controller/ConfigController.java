package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.configuration.ConfigExportProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.config.ExportRequest;
import com.epam.aidial.deployment.manager.service.config.ConfigTransferService;
import com.epam.aidial.deployment.manager.web.dto.config.ExportRequestDto;
import com.epam.aidial.deployment.manager.web.mapper.ExportConfigMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/configs")
@LogExecution
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigExportProperties properties;
    private final ConfigTransferService configTransfer;
    private final ExportConfigMapper exportConfigMapper;

    @PostMapping(path = "/export", consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> exportConfig(@Valid @RequestBody ExportRequestDto dto) {
        ExportRequest request = exportConfigMapper.toExportRequest(dto);
        StreamingResponseBody stream = configTransfer.exportConfig(request);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + properties.getZipName() + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(stream);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void importConfig(@RequestPart("file") MultipartFile file,
                             @RequestParam("resolutionPolicy") ConflictResolutionPolicy resolutionPolicy) {
        configTransfer.importConfig(file, resolutionPolicy);
    }
}
