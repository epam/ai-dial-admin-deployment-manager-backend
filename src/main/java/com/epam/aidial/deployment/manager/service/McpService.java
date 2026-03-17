package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.exception.McpClientException;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.McpHttpClientTransportAuthorizationException;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class McpService {

    private final DeploymentService deploymentService;
    private final McpClientFactory mcpClientFactory;
    private final McpEndpointPathResolver mcpEndpointPathResolver;

    public McpSchema.ListToolsResult getTools(String deploymentId, String nextCursor) {
        return get(deploymentId, nextCursor, McpSyncClient::listTools);
    }

    public McpSchema.ListResourcesResult getResources(String deploymentId, String nextCursor) {
        return get(deploymentId, nextCursor, McpSyncClient::listResources);
    }

    public McpSchema.ListPromptsResult getPrompts(String deploymentId, String nextCursor) {
        return get(deploymentId, nextCursor, McpSyncClient::listPrompts);
    }

    private <T> T get(String deploymentId, String nextCursor, BiFunction<McpSyncClient, String, T> function) {
        return execute(deploymentId, client -> function.apply(client, nextCursor), "connect to");
    }

    public McpSchema.CallToolResult callTool(String deploymentId, McpSchema.CallToolRequest callToolRequest) {
        return execute(deploymentId, client -> client.callTool(callToolRequest), "call a tool via");
    }

    private <T> T execute(String deploymentId, Function<McpSyncClient, T> operation, String operationName) {
        var deployment = getDeployment(deploymentId);
        var endpointPath = mcpEndpointPathResolver.resolveEndpointPath(deployment);
        try (var mcpClient = mcpClientFactory.create(deployment.getUrl(), endpointPath, deployment.getTransport())) {
            mcpClient.initialize();
            return operation.apply(mcpClient);
        } catch (Exception e) {
            var root = ExceptionUtils.getRootCause(e);
            String reason = root != null ? root.getMessage() : e.getMessage();
            String message = "Failed to %s MCP server. Deployment id: %s. Transport: '%s'. Path: '%s'"
                    .formatted(operationName, deploymentId, deployment.getTransport(), Optional.ofNullable(deployment.getMcpEndpointPath()).orElse(""));
            HttpStatus status = root instanceof McpHttpClientTransportAuthorizationException
                    ? HttpStatus.UNAUTHORIZED
                    : HttpStatus.INTERNAL_SERVER_ERROR;
            log.warn("{}. Reason: {}", message, reason);
            throw new McpClientException(message, status, e);
        }
    }

    private McpDeployment getDeployment(String deploymentId) {
        var deployment = deploymentService.getDeployment(deploymentId)
                .orElseThrow(() -> new EntityNotFoundException("Deployment not found by id: %s"
                        .formatted(deploymentId)));

        if (deployment.getStatus() != DeploymentStatus.RUNNING) {
            throw new IllegalStateException("Deployment is not deployed yet. Deployment id: %s"
                    .formatted(deploymentId));
        }

        if (StringUtils.isBlank(deployment.getUrl())) {
            throw new IllegalStateException("Deployment does not have URL yet. Deployment id: %s"
                    .formatted(deploymentId));
        }

        if (deployment instanceof McpDeployment mcpDeployment) {
            return mcpDeployment;
        }

        throw new IllegalStateException("Deployment is not of MCP type. Deployment id: %s".formatted(deploymentId));
    }

}
