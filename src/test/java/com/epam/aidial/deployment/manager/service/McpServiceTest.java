package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.exception.McpClientException;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.McpTransport;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.utils.TestException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpServiceTest {

    private static final String DEPLOYMENT_ID = "facade00-0000-0001-a000-000000000000";
    private static final UUID IMAGE_DEFINITION_ID = UUID.fromString("facade00-0000-0003-a000-000000000000");
    private static final String NEXT_CURSOR = "test-cursor";
    private static final String DEPLOYMENT_URL = "http://test-deployment.com";

    @Mock
    private DeploymentService deploymentService;
    @Mock
    private McpClientFactory mcpClientFactory;
    @Mock
    private McpSyncClient mcpSyncClient;
    @Spy
    private McpEndpointPathResolver mcpEndpointPathResolver;

    @InjectMocks
    private McpService mcpService;

    @Test
    void testGetTools_success() {
        // Given
        var deployment = createDeployment();
        deployment.setTransport(McpTransport.HTTP_STREAMING);
        String endpointPath = "/mcp";
        when(deploymentService.getDeployment(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(mcpClientFactory.create(DEPLOYMENT_URL, endpointPath, McpTransport.HTTP_STREAMING)).thenReturn(mcpSyncClient);
        var expectedResult = mock(McpSchema.ListToolsResult.class);
        when(mcpSyncClient.listTools(NEXT_CURSOR)).thenReturn(expectedResult);

        // When
        var result = mcpService.getTools(DEPLOYMENT_ID, NEXT_CURSOR);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(deploymentService).getDeployment(DEPLOYMENT_ID);
        verify(mcpClientFactory).create(DEPLOYMENT_URL, endpointPath, McpTransport.HTTP_STREAMING);
        verify(mcpSyncClient).initialize();
        verify(mcpSyncClient).listTools(NEXT_CURSOR);
        verify(mcpSyncClient).close();
    }

    @Test
    void testGetResources_success() {
        // Given
        var deployment = createDeployment();
        String endpointPath = "/mcpNonStandard";
        deployment.setMcpEndpointPath(endpointPath);
        deployment.setTransport(McpTransport.HTTP_STREAMING);
        when(deploymentService.getDeployment(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(mcpClientFactory.create(DEPLOYMENT_URL, endpointPath, McpTransport.HTTP_STREAMING)).thenReturn(mcpSyncClient);
        var expectedResult = mock(McpSchema.ListResourcesResult.class);
        when(mcpSyncClient.listResources(NEXT_CURSOR)).thenReturn(expectedResult);

        // When
        var result = mcpService.getResources(DEPLOYMENT_ID, NEXT_CURSOR);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(deploymentService).getDeployment(DEPLOYMENT_ID);
        verify(mcpClientFactory).create(DEPLOYMENT_URL, endpointPath, McpTransport.HTTP_STREAMING);
        verify(mcpSyncClient).initialize();
        verify(mcpSyncClient).listResources(NEXT_CURSOR);
        verify(mcpSyncClient).close();
    }

    @Test
    void testGetPrompts_success() {
        // Given
        var deployment = createDeployment();
        deployment.setTransport(McpTransport.HTTP_STREAMING);
        String endpointPath = "/mcp";
        when(deploymentService.getDeployment(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        var expectedResult = mock(McpSchema.ListPromptsResult.class);
        when(mcpClientFactory.create(DEPLOYMENT_URL, endpointPath, McpTransport.HTTP_STREAMING)).thenReturn(mcpSyncClient);
        when(mcpSyncClient.listPrompts(NEXT_CURSOR)).thenReturn(expectedResult);

        // When
        var result = mcpService.getPrompts(DEPLOYMENT_ID, NEXT_CURSOR);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(deploymentService).getDeployment(DEPLOYMENT_ID);
        verify(mcpClientFactory).create(DEPLOYMENT_URL, endpointPath, McpTransport.HTTP_STREAMING);
        verify(mcpSyncClient).initialize();
        verify(mcpSyncClient).listPrompts(NEXT_CURSOR);
        verify(mcpSyncClient).close();
    }

    @Test
    void testGetTools_deploymentNotFound() {
        // Given
        when(deploymentService.getDeployment(DEPLOYMENT_ID)).thenReturn(Optional.empty());

        // When/Then
        var exception = assertThrows(EntityNotFoundException.class,
                () -> mcpService.getTools(DEPLOYMENT_ID, NEXT_CURSOR));

        assertThat(exception).hasMessageContaining("Deployment not found by id");
        verify(deploymentService).getDeployment(DEPLOYMENT_ID);
        verifyNoInteractions(mcpClientFactory, mcpSyncClient);
    }

    @Test
    void testGetTools_notDeployed() {
        // Given
        var deployment = createDeployment();
        deployment.setStatus(DeploymentStatus.NOT_DEPLOYED);
        when(deploymentService.getDeployment(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        // When/Then
        var exception = assertThrows(IllegalStateException.class,
                () -> mcpService.getTools(DEPLOYMENT_ID, NEXT_CURSOR));

        assertThat(exception).hasMessageContaining("Deployment is not deployed yet");
        verify(deploymentService).getDeployment(DEPLOYMENT_ID);
        verifyNoInteractions(mcpClientFactory, mcpSyncClient);
    }

    @Test
    void testGetTools_urlIsNull() {
        // Given
        var deployment = createDeployment();
        deployment.setUrl(null);
        when(deploymentService.getDeployment(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));

        // When/Then
        var exception = assertThrows(IllegalStateException.class,
                () -> mcpService.getTools(DEPLOYMENT_ID, NEXT_CURSOR));

        assertThat(exception).hasMessageContaining("Deployment does not have URL yet");
        verify(deploymentService).getDeployment(DEPLOYMENT_ID);
        verifyNoInteractions(mcpClientFactory, mcpSyncClient);
    }

    @Test
    void testGetTools_clientClosedAfterException() {
        // Given
        var deployment = createDeployment();
        String endpointPath = "/sse";
        deployment.setMcpEndpointPath(endpointPath);
        deployment.setTransport(McpTransport.SSE);
        when(deploymentService.getDeployment(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(mcpClientFactory.create(DEPLOYMENT_URL, endpointPath, McpTransport.SSE)).thenReturn(mcpSyncClient);
        Mockito.doThrow(new TestException("Test exception")).when(mcpSyncClient).initialize();

        // When/Then
        var exception = assertThrows(McpClientException.class, () -> mcpService.getTools(DEPLOYMENT_ID, NEXT_CURSOR));

        assertThat(exception).hasMessageContaining("Failed to connect to MCP server");

        verify(deploymentService).getDeployment(DEPLOYMENT_ID);
        verify(mcpClientFactory).create(DEPLOYMENT_URL, endpointPath, McpTransport.SSE);
        verify(mcpSyncClient).initialize();
        verify(mcpSyncClient).close();
    }

    @Test
    void testGetTools_withNullCursor() {
        // Given
        var deployment = createDeployment();
        String endpointPath = "/sseNonStandard";
        deployment.setMcpEndpointPath(endpointPath);
        deployment.setTransport(McpTransport.SSE);
        when(deploymentService.getDeployment(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(mcpClientFactory.create(DEPLOYMENT_URL, endpointPath, McpTransport.SSE)).thenReturn(mcpSyncClient);
        var expectedResult = mock(McpSchema.ListToolsResult.class);
        when(mcpSyncClient.listTools(null)).thenReturn(expectedResult);

        // When
        var result = mcpService.getTools(DEPLOYMENT_ID, null);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(mcpSyncClient).listTools(null);
    }

    @Test
    void testCallTool() {
        // Given
        var deployment = createDeployment();
        String endpointPath = "/sseCustom";
        deployment.setMcpEndpointPath(endpointPath);
        deployment.setTransport(McpTransport.SSE);

        when(deploymentService.getDeployment(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(mcpClientFactory.create(DEPLOYMENT_URL, endpointPath, McpTransport.SSE)).thenReturn(mcpSyncClient);

        var callToolRequest = Mockito.mock(McpSchema.CallToolRequest.class);
        var expectedCallToolResult = Mockito.mock(McpSchema.CallToolResult.class);
        when(mcpSyncClient.callTool(callToolRequest)).thenReturn(expectedCallToolResult);

        // When
        var result = mcpService.callTool(DEPLOYMENT_ID, callToolRequest);

        // Then
        assertThat(result).isEqualTo(expectedCallToolResult);
        verify(mcpSyncClient).callTool(callToolRequest);
    }

    private McpDeployment createDeployment() {
        var deployment = new McpDeployment();
        deployment.setId(DEPLOYMENT_ID);
        deployment.setImageDefinitionId(IMAGE_DEFINITION_ID);
        deployment.setStatus(DeploymentStatus.RUNNING);
        deployment.setUrl(DEPLOYMENT_URL);
        deployment.setTransport(McpTransport.SSE);
        return deployment;
    }

}
