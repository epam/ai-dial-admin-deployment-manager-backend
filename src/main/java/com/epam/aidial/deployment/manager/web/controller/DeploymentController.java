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
import com.epam.aidial.deployment.manager.service.deployment.metrics.DeploymentMetricsService;
import com.epam.aidial.deployment.manager.web.dto.DeploymentImageChangeRequestDto;
import com.epam.aidial.deployment.manager.web.dto.DeploymentInfoDto;
import com.epam.aidial.deployment.manager.web.dto.DeploymentTypeDto;
import com.epam.aidial.deployment.manager.web.dto.DuplicateDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.PodInfoDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.DeploymentDto;
import com.epam.aidial.deployment.manager.web.dto.metrics.DeploymentMetricsDto;
import com.epam.aidial.deployment.manager.web.mapper.DeploymentDtoMapper;
import com.epam.aidial.deployment.manager.web.mapper.DeploymentMetricsDtoMapper;
import com.epam.aidial.deployment.manager.web.security.FullAdminOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    private final DeploymentMetricsService deploymentMetricsService;
    private final DeploymentDtoMapper dtoMapper;
    private final DeploymentMetricsDtoMapper metricsDtoMapper;

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
    public DeploymentDto getDeploymentById(@PathVariable String id) {
        return deploymentService.getDeployment(id)
                .map(dtoMapper::toDeploymentDto)
                .orElseThrow(() -> new EntityNotFoundException("Deployment not found by ID: %s".formatted(id)));
    }

    @GetMapping(path = "/{id}/revision/{revision}",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public DeploymentDto getDeploymentSnapshot(@PathVariable String id, @PathVariable Integer revision) {
        return dtoMapper.toDeploymentDto(deploymentService.getDeploymentSnapshot(id, revision));
    }

    @GetMapping(path = "/revision/{revision}",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public List<DeploymentDto> getAllDeploymentsAtRevision(@PathVariable Integer revision) {
        return deploymentService.getAllDeploymentsAtRevision(revision).stream()
                .map(dtoMapper::toDeploymentDto)
                .collect(Collectors.toList());
    }

    @FullAdminOnly
    @PostMapping(path = "/{id}/revision/{revision}/rollback",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Roll back a deployment to a past revision",
            description = "Restores the deployment's stored configuration to its snapshot at the given audit revision. "
                    + "Allowed only when the deployment is in NOT_DEPLOYED or STOPPED. "
                    + "If the deployment was deleted but existed at the revision, it is re-created (in NOT_DEPLOYED, "
                    + "with sensitive env values reset and to be re-supplied before deploying). "
                    + "Does not modify live Kubernetes state — the operator must trigger deploy afterwards to apply.")
    @ApiResponse(responseCode = "200", description = "Rollback applied, deployment re-created, or identical-state no-op")
    @ApiResponse(responseCode = "400", description = "Active-state, validation, or missing-reference rejection")
    @ApiResponse(responseCode = "403", description = "Read-only role")
    @ApiResponse(responseCode = "404", description = "Revision not found, or deployment never existed at the revision")
    public DeploymentDto rollbackDeployment(@PathVariable String id, @PathVariable Integer revision) {
        var rolledBack = deploymentService.rollback(id, revision);
        return dtoMapper.toDeploymentDto(rolledBack);
    }

    @FullAdminOnly
    @PostMapping(consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentDto createDeployment(@RequestBody @Valid CreateDeploymentRequestDto requestDto) {
        var createDeployment = dtoMapper.toCreateDeployment(requestDto);
        var created = deploymentService.createDeployment(createDeployment);
        return dtoMapper.toDeploymentDto(created);
    }

    @FullAdminOnly
    @PostMapping(path = "/duplicate", consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentDto duplicateDeployment(@RequestBody @Valid DuplicateDeploymentRequestDto requestDto) {
        var duplicated = deploymentService.duplicateDeployment(
                requestDto.sourceDeploymentName(),
                requestDto.newDeploymentName(),
                requestDto.newDeploymentDisplayName()
        );
        return dtoMapper.toDeploymentDto(duplicated);
    }

    @FullAdminOnly
    @PostMapping(path = "/change-image",
            consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public void changeImage(@RequestBody @Valid DeploymentImageChangeRequestDto requestDto) {
        deploymentService.updateImageDefinitionForDeployments(
                requestDto.imageDefinitionId(),
                requestDto.deployments()
        );
    }

    @FullAdminOnly
    @PutMapping(path = "/{id}",
            consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public DeploymentDto updateDeployment(@PathVariable String id,
                                          @RequestBody @Valid CreateDeploymentRequestDto requestDto) {
        var createDeployment = dtoMapper.toCreateDeployment(requestDto);
        var updated = deploymentService.updateDeployment(id, createDeployment);
        return dtoMapper.toDeploymentDto(updated);
    }

    @FullAdminOnly
    @DeleteMapping(path = "/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDeployment(@PathVariable String id) {
        deploymentService.deleteDeployment(id);
    }

    @FullAdminOnly
    @Operation(summary = "Deploy a deployment by id")
    @PostMapping(path = "{id}/deploy",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public DeploymentDto deploy(@PathVariable("id") String id) {
        var deployment = deploymentService.deploy(id);
        return dtoMapper.toDeploymentDto(deployment);
    }

    @FullAdminOnly
    @PostMapping(path = "{id}/undeploy",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public DeploymentDto undeploy(@PathVariable("id") String id) {
        var deployment = deploymentService.undeploy(id);
        return dtoMapper.toDeploymentDto(deployment);
    }

    @GetMapping(path = "{id}/pods",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public List<PodInfoDto> getPods(@PathVariable("id") String id) {
        var pods = deploymentService.getInstances(id);
        return dtoMapper.toPodInfoDto(pods);
    }

    @GetMapping(path = "{id}/active-pods",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public List<PodInfoDto> getActivePods(@PathVariable("id") String id) {
        var pods = deploymentService.getActiveInstances(id);
        return dtoMapper.toPodInfoDto(pods);
    }

    @GetMapping(path = "{id}/pods/{podId}/logs",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToLogs(
            @PathVariable(value = "id") String id,
            @PathVariable(value = "podId") String podId,
            @RequestParam(value = "sinceTime", required = false) Instant sinceTime,
            @RequestParam(value = "sinceSeconds", required = false) Integer sinceSeconds,
            @RequestParam(value = "tail", required = false) Integer tailLogs,
            @RequestParam(value = "previous", required = false, defaultValue = "false") boolean previous
    ) {
        var logReadConfig = PodLogReaderConfiguration.builder()
                .sinceTime(sinceTime)
                .sinceSeconds(sinceSeconds)
                .tailLogs(tailLogs)
                .previous(previous)
                .build();
        return deploymentLogsService.streamLogs(id, podId, logReadConfig);
    }

    @GetMapping(path = "{id}/metrics",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a live metrics snapshot for a deployment",
            description = "Resource metrics (replica counts and per-pod CPU/memory) are reported for any deployment "
                    + "type; serving-quality metrics are additionally scraped for INFERENCE deployments. Missing "
                    + "telemetry never fails the request: affected blocks are null with the reason recorded in "
                    + "'availability' and the response is still 200. Counter-derived values are lifetime aggregates "
                    + "(window=lifetime); raw cumulative counters are echoed in 'rawCounters' for client-side rate "
                    + "derivation. Exactly one Ready pod is scraped for serving metrics and named in 'scrapedPod'.")
    @ApiResponse(responseCode = "200", description = "Live metrics snapshot (possibly partial — see availability)")
    @ApiResponse(responseCode = "400", description = "Metrics collection is disabled by configuration")
    @ApiResponse(responseCode = "404", description = "Deployment not found")
    public DeploymentMetricsDto getMetrics(@PathVariable String id) {
        return metricsDtoMapper.toDto(deploymentMetricsService.getSnapshot(id));
    }

    @GetMapping(path = "{id}/events/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToEvents(
            @PathVariable(value = "id") String id,
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
