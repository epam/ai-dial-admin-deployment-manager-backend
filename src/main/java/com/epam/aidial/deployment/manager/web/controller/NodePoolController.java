package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.service.nodepool.NodePoolService;
import com.epam.aidial.deployment.manager.web.dto.nodepool.NodePoolDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/node-pools")
@RequiredArgsConstructor
@LogExecution
@Tag(name = "Node Pools", description = "Node pool management")
public class NodePoolController {

    private final NodePoolService nodePoolService;

    @GetMapping
    @Operation(summary = "List available node pools with live utilization",
            description = "Returns all configured node pools enriched with live Kubernetes node resource utilization data")
    @ApiResponse(responseCode = "200", description = "Node pools retrieved successfully")
    public List<NodePoolDto> getNodePools() {
        return nodePoolService.getNodePools();
    }
}
