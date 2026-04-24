package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.configuration.HubbleRelayProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.service.HubbleDomainFlowService;
import com.epam.aidial.deployment.manager.service.ImageBuildLogsService;
import com.epam.aidial.deployment.manager.service.ImageBuildRunner;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.web.dto.CreateBuildImageRequestDto;
import com.epam.aidial.deployment.manager.web.dto.ImageBuildDetailsDto;
import com.epam.aidial.deployment.manager.web.mapper.ImageBuildDetailsDtoMapper;
import com.epam.aidial.deployment.manager.web.security.FullAdminOnly;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Slf4j
@LogExecution
@RestController
@RequestMapping("/api/v1/images/builds")
@RequiredArgsConstructor
public class ImageBuildController {

    private final ImageBuildLogsService imageBuildLogsService;
    private final ImageDefinitionService imageDefinitionService;
    private final ImageBuildRunner imageBuildRunner;
    private final ImageBuildDetailsDtoMapper dtoMapper;
    private final HubbleDomainFlowService hubbleDomainFlowService;
    private final HubbleRelayProperties hubbleRelayProperties;

    @FullAdminOnly
    @PostMapping(consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public void buildImage(@RequestBody @Valid CreateBuildImageRequestDto requestDto) {
        imageBuildRunner.buildImage(requestDto.imageDefinitionId());
    }

    @GetMapping(path = "{id}/status",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToStatus(@PathVariable UUID id) {
        return imageBuildLogsService.streamStatus(id);
    }

    @GetMapping(path = "/{id}/details",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ImageBuildDetailsDto getImageBuildLogsById(@PathVariable UUID id) {
        var domainEntries = hubbleRelayProperties.isEnabled()
                ? hubbleDomainFlowService.getDomainEntriesForBuild(id)
                : null;
        return imageDefinitionService.getImageDefinition(id)
                .map(def -> dtoMapper.toDto(def, domainEntries))
                .orElseThrow(() -> new EntityNotFoundException("ImageDefinition not found by id: %s".formatted(id)));
    }

    @GetMapping(path = "{id}/logs",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToLogs(@PathVariable UUID id) {
        return imageBuildLogsService.streamLogs(id);
    }

}
