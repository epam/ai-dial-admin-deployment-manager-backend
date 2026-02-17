package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.exception.McpClientException;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.function.BiFunction;

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
        var deployment = getDeployment(deploymentId);
        var endpointPath = mcpEndpointPathResolver.resolveEndpointPath(deployment);

        try (var mcpClient = mcpClientFactory.create(deployment.getUrl(), endpointPath, deployment.getTransport())) {
            mcpClient.initialize();
            return function.apply(mcpClient, nextCursor);
        } catch (Exception e) {
            throw new McpClientException(("Failed to connect to MCP server. Make sure transport '%s' and path '%s' are correct."
                    + " Deployment id: %s").formatted(deployment.getTransport(), deployment.getMcpEndpointPath(), deploymentId), e);
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
