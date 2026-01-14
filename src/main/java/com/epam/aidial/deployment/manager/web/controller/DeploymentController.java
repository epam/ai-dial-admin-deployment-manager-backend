package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReaderConfiguration;
import com.epam.aidial.deployment.manager.kubernetes.event.EventStreamerConfiguration;
import com.epam.aidial.deployment.manager.model.EventType;
import com.epam.aidial.deployment.manager.model.ObjectKind;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentLogsService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.service.deployment.EventStreamingService;
import com.epam.aidial.deployment.manager.web.dto.DeploymentImageChangeRequestDto;
import com.epam.aidial.deployment.manager.web.dto.DeploymentInfoDto;
import com.epam.aidial.deployment.manager.web.dto.DeploymentTypeDto;
import com.epam.aidial.deployment.manager.web.dto.DuplicateDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.PodInfoDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.DeploymentDto;
import com.epam.aidial.deployment.manager.web.mapper.DeploymentDtoMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@LogExecution
@RestController
@RequestMapping("/api/v1/deployments")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final EventStreamingService eventStreamingService;
    private final DeploymentLogsService deploymentLogsService;
    private final DeploymentDtoMapper dtoMapper;

    @GetMapping(produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public List<DeploymentInfoDto> getAllDeployments(
            @RequestParam(value = "imageDefinitionId", required = false) UUID imageDefinitionId,
            @RequestParam(required = false) List<DeploymentTypeDto> type
    ) {
        Collection<Deployment> deployments;
        if (imageDefinitionId != null) {
            deployments = deploymentService.getAllDeployments(imageDefinitionId);
        } else if (!CollectionUtils.isEmpty(type)) {
            deployments = deploymentService.getAllDeploymentsByType(type);
        } else {
            deployments = deploymentService.getAllDeployments();
        }
        return deployments.stream()
                .map(dtoMapper::toDeploymentInfoDto)
                .collect(Collectors.toList());
    }

    @GetMapping(path = "/{id}",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public DeploymentDto getDeploymentById(@PathVariable UUID id) {
        return deploymentService.getDeployment(id)
                .map(dtoMapper::toDeploymentDto)
                .orElseThrow(() -> new EntityNotFoundException("Deploy not found by id: %s".formatted(id)));
    }

    @PostMapping(consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentDto createDeployment(@RequestBody @Valid CreateDeploymentRequestDto requestDto) {
        var createDeployment = dtoMapper.toCreateDeployment(requestDto);
        var created = deploymentService.createDeployment(createDeployment);
        return dtoMapper.toDeploymentDto(created);
    }

    @PostMapping(path = "/duplicate", consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentDto duplicateDeployment(@RequestBody @Valid DuplicateDeploymentRequestDto requestDto) {
        var duplicated = deploymentService.duplicateDeployment(
                requestDto.sourceDeploymentId(),
                requestDto.newDeploymentName()
        );
        return dtoMapper.toDeploymentDto(duplicated);
    }

    @PostMapping(path = "/change-image",
            consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public void changeImage(@RequestBody @Valid DeploymentImageChangeRequestDto requestDto) {
        deploymentService.updateImageDefinitionForDeployments(
                requestDto.imageDefinitionId(),
                requestDto.deployments()
        );
    }

    @PutMapping(path = "/{id}",
            consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public DeploymentDto updateDeployment(@PathVariable UUID id,
                                          @RequestBody @Valid CreateDeploymentRequestDto requestDto) {
        var createDeployment = dtoMapper.toCreateDeployment(requestDto);
        var updated = deploymentService.updateDeployment(id, createDeployment);
        return dtoMapper.toDeploymentDto(updated);
    }

    @DeleteMapping(path = "/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDeployment(@PathVariable UUID id) {
        deploymentService.deleteDeployment(id);
    }

    @PostMapping(path = "{id}/deploy",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public DeploymentDto deploy(@PathVariable("id") UUID id) {
        var deployment = deploymentService.deploy(id);
        return dtoMapper.toDeploymentDto(deployment);
    }

    @PostMapping(path = "{id}/undeploy",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public DeploymentDto undeploy(@PathVariable("id") UUID id) {
        var deployment = deploymentService.undeploy(id);
        return dtoMapper.toDeploymentDto(deployment);
    }

    @GetMapping(path = "{id}/pods",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public List<PodInfoDto> getPods(@PathVariable("id") UUID id) {
        var pods = deploymentService.getInstances(id);
        return dtoMapper.toPodInfoDto(pods);
    }

    @GetMapping(path = "{id}/active-pods",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public List<PodInfoDto> getActivePods(@PathVariable("id") UUID id) {
        var pods = deploymentService.getActiveInstances(id);
        return dtoMapper.toPodInfoDto(pods);
    }

    @GetMapping(path = "{id}/pods/{podId}/logs",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToLogs(
            @PathVariable(value = "id") UUID id,
            @PathVariable(value = "podId") String podId,
            @RequestParam(value = "sinceTime", required = false) Instant sinceTime,
            @RequestParam(value = "sinceSeconds", required = false) Integer sinceSeconds,
            @RequestParam(value = "tail", required = false) Integer tailLogs
    ) {
        var logReadConfig = PodLogReaderConfiguration.builder()
                .sinceTime(sinceTime)
                .sinceSeconds(sinceSeconds)
                .tailLogs(tailLogs)
                .build();
        return deploymentLogsService.streamLogs(id, podId, logReadConfig);
    }

    @GetMapping(path = "{id}/events/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToEvents(
            @PathVariable(value = "id") UUID id,
            @RequestParam(value = "sinceTime", required = false) Instant sinceTime,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "involvedObjectKind", required = false) String involvedObjectKind
    ) {
        var eventStreamerConfig = EventStreamerConfiguration.builder()
                .sinceTime(sinceTime)
                .eventType(EventType.fromString(eventType))
                .involvedObjectKind(ObjectKind.fromString(involvedObjectKind))
                .build();
        return eventStreamingService.streamEvents(id, eventStreamerConfig);
    }

}
