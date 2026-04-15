package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.Page;
import com.epam.aidial.deployment.manager.model.audit.AuditActivity;
import com.epam.aidial.deployment.manager.model.page.PageRequestModel;
import com.epam.aidial.deployment.manager.service.audit.AuditActivityService;
import com.epam.aidial.deployment.manager.web.dto.PageDto;
import com.epam.aidial.deployment.manager.web.dto.audit.AuditActivityDto;
import com.epam.aidial.deployment.manager.web.dto.page.PageRequestDto;
import com.epam.aidial.deployment.manager.web.mapper.AuditActivityDtoMapper;
import com.epam.aidial.deployment.manager.web.mapper.PageDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@LogExecution
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class AuditActivityController {

    private final AuditActivityService auditActivityService;
    private final AuditActivityDtoMapper auditActivityDtoMapper;
    private final PageDtoMapper pageDtoMapper;

    @PostMapping("/activities")
    @Operation(summary = "List activities with pagination, sorting, and filtering")
    @ApiResponse(responseCode = "200", description = "Paginated list of activities")
    public PageDto<AuditActivityDto> listActivities(@RequestBody @Valid PageRequestDto request) {
        PageRequestModel pageRequest = pageDtoMapper.toPageRequestModel(request);
        Page<AuditActivity> page = auditActivityService.getActivitiesList(pageRequest);

        List<AuditActivityDto> data = page.getData().stream()
                .map(auditActivityDtoMapper::toDto)
                .toList();

        return PageDto.<AuditActivityDto>builder()
                .data(data)
                .total(page.getTotal())
                .totalPages(page.getTotalPages())
                .build();
    }

    @GetMapping("/activities/{activityId}")
    @Operation(summary = "Get a single activity by ID")
    @ApiResponse(responseCode = "200", description = "Activity details")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    public AuditActivityDto getActivity(@PathVariable UUID activityId) {
        return auditActivityDtoMapper.toDto(auditActivityService.getActivity(activityId));
    }
}
