package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.Page;
import com.epam.aidial.deployment.manager.model.audit.AuditRevision;
import com.epam.aidial.deployment.manager.model.page.PageRequestModel;
import com.epam.aidial.deployment.manager.service.audit.HistoryService;
import com.epam.aidial.deployment.manager.web.dto.PageDto;
import com.epam.aidial.deployment.manager.web.dto.audit.AuditRevisionDto;
import com.epam.aidial.deployment.manager.web.dto.audit.BaseGetRevisionQuery;
import com.epam.aidial.deployment.manager.web.dto.audit.GetRevisionByIdQuery;
import com.epam.aidial.deployment.manager.web.dto.audit.GetRevisionByTimestampQuery;
import com.epam.aidial.deployment.manager.web.dto.page.PageRequestDto;
import com.epam.aidial.deployment.manager.web.mapper.AuditRevisionDtoMapper;
import com.epam.aidial.deployment.manager.web.mapper.PageDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@LogExecution
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class HistoryController {

    private final HistoryService historyService;
    private final AuditRevisionDtoMapper auditRevisionDtoMapper;
    private final PageDtoMapper pageDtoMapper;

    @PostMapping("/history/revisions")
    @Operation(summary = "List revisions with pagination, sorting, and filtering")
    @ApiResponse(responseCode = "200", description = "Paginated list of revisions")
    public PageDto<AuditRevisionDto> listRevisions(@RequestBody @Valid PageRequestDto request) {
        PageRequestModel pageRequest = pageDtoMapper.toPageRequestModel(request);
        Page<AuditRevision> page = historyService.getRevisionsList(pageRequest);

        List<AuditRevisionDto> data = page.getData().stream()
                .map(auditRevisionDtoMapper::toDto)
                .toList();

        return PageDto.<AuditRevisionDto>builder()
                .data(data)
                .total(page.getTotal())
                .totalPages(page.getTotalPages())
                .build();
    }

    @PostMapping("/history/revisions/query")
    @Operation(summary = "Query a specific revision by timestamp or ID")
    @ApiResponse(responseCode = "200", description = "Revision details")
    @ApiResponse(responseCode = "404", description = "Revision not found")
    public AuditRevisionDto queryRevision(@RequestBody @Valid BaseGetRevisionQuery query) {
        AuditRevision revision;
        if (query instanceof GetRevisionByTimestampQuery timestampQuery) {
            revision = historyService.getRevisionByTimestamp(timestampQuery.getTimestamp());
        } else if (query instanceof GetRevisionByIdQuery idQuery) {
            revision = historyService.getRevisionById(idQuery.getId());
        } else {
            throw new IllegalArgumentException("Unsupported query type: " + query.getClass().getSimpleName());
        }
        return auditRevisionDtoMapper.toDto(revision);
    }
}
