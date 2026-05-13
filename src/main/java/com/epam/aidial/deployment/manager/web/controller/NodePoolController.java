package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.service.nodepool.NodePoolService;
import com.epam.aidial.deployment.manager.web.dto.nodepool.NodePoolListResponseDto;
import com.epam.aidial.deployment.manager.web.mapper.NodePoolDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/node-pools")
@RequiredArgsConstructor
@LogExecution
@Tag(name = "Node Pools", description = "Node pool management")
public class NodePoolController {

    private final NodePoolService nodePoolService;
    private final NodePoolDtoMapper nodePoolDtoMapper;

    @GetMapping
    @Operation(summary = "List available node pools",
            description = "Returns the configured pool catalogue (id, name, optional description). "
                    + "Scheduling primitives are internal configuration and not exposed via this endpoint. "
                    + "The `pools` array is always present and is empty when no pools are configured.")
    @ApiResponse(responseCode = "200", description = "Node pools retrieved successfully")
    public NodePoolListResponseDto getNodePools() {
        return nodePoolDtoMapper.toListResponse(nodePoolService.getNodePools());
    }
}
