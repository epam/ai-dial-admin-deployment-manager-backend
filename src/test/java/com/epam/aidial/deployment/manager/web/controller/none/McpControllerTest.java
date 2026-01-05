package com.epam.aidial.deployment.manager.web.controller.none;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.service.McpService;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.epam.aidial.deployment.manager.web.controller.McpController;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = McpController.class)
@Import({
        JsonMapperConfiguration.class
})
class McpControllerTest extends AbstractControllerNoneSecureTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private McpService mcpService;

    @Test
    void testGetTools() throws Exception {
        var deploymentId = UUID.randomUUID();
        String nextCursor = null;

        // Read JSON response from file
        var responseJson = ResourceUtils.readResource("/mcp/mcp_tools_response.json");
        var result = objectMapper.readValue(responseJson, McpSchema.ListToolsResult.class);

        when(mcpService.getTools(deploymentId, nextCursor)).thenReturn(result);

        System.out.println("[DEBUG_LOG] Result: " + objectMapper.writeValueAsString(result));

        mockMvc.perform(get("/api/v1/deployments/mcp/{deploymentId}/tools", deploymentId))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson, JsonCompareMode.LENIENT));

        verify(mcpService).getTools(deploymentId, nextCursor);
    }

    @Test
    void testGetToolsWithCursor() throws Exception {
        var deploymentId = UUID.randomUUID();
        var nextCursor = "some-cursor";

        // Read JSON response from file
        var responseJson = ResourceUtils.readResource("/mcp/mcp_tools_response.json");
        var result = objectMapper.readValue(responseJson, McpSchema.ListToolsResult.class);

        when(mcpService.getTools(deploymentId, nextCursor)).thenReturn(result);

        mockMvc.perform(get("/api/v1/deployments/mcp/{deploymentId}/tools", deploymentId)
                        .param("nextCursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson, JsonCompareMode.LENIENT));

        verify(mcpService).getTools(deploymentId, nextCursor);
    }

    @Test
    void testGetResources() throws Exception {
        var deploymentId = UUID.randomUUID();
        String nextCursor = null;

        // Read JSON response from file
        var responseJson = ResourceUtils.readResource("/mcp/mcp_resources_response.json");
        var result = objectMapper.readValue(responseJson, McpSchema.ListResourcesResult.class);

        when(mcpService.getResources(deploymentId, nextCursor)).thenReturn(result);

        mockMvc.perform(get("/api/v1/deployments/mcp/{deploymentId}/resources", deploymentId))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson, JsonCompareMode.LENIENT));

        verify(mcpService).getResources(deploymentId, nextCursor);
    }

    @Test
    void testGetResourcesWithCursor() throws Exception {
        var deploymentId = UUID.randomUUID();
        var nextCursor = "some-cursor";

        // Read JSON response from file
        var responseJson = ResourceUtils.readResource("/mcp/mcp_resources_response.json");
        var result = objectMapper.readValue(responseJson, McpSchema.ListResourcesResult.class);

        when(mcpService.getResources(deploymentId, nextCursor)).thenReturn(result);

        mockMvc.perform(get("/api/v1/deployments/mcp/{deploymentId}/resources", deploymentId)
                        .param("nextCursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson, JsonCompareMode.LENIENT));

        verify(mcpService).getResources(deploymentId, nextCursor);
    }

    @Test
    void testGetPrompts() throws Exception {
        var deploymentId = UUID.randomUUID();
        String nextCursor = null;

        // Read JSON response from file
        var responseJson = ResourceUtils.readResource("/mcp/mcp_prompts_response.json");
        var result = objectMapper.readValue(responseJson, McpSchema.ListPromptsResult.class);

        when(mcpService.getPrompts(deploymentId, nextCursor)).thenReturn(result);

        mockMvc.perform(get("/api/v1/deployments/mcp/{deploymentId}/prompts", deploymentId))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson, JsonCompareMode.LENIENT));

        verify(mcpService).getPrompts(deploymentId, nextCursor);
    }

    @Test
    void testGetPromptsWithCursor() throws Exception {
        var deploymentId = UUID.randomUUID();
        var nextCursor = "some-cursor";

        // Read JSON response from file
        var responseJson = ResourceUtils.readResource("/mcp/mcp_prompts_response.json");
        var result = objectMapper.readValue(responseJson, McpSchema.ListPromptsResult.class);

        when(mcpService.getPrompts(deploymentId, nextCursor)).thenReturn(result);

        mockMvc.perform(get("/api/v1/deployments/mcp/{deploymentId}/prompts", deploymentId)
                        .param("nextCursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson, JsonCompareMode.LENIENT));

        verify(mcpService).getPrompts(deploymentId, nextCursor);
    }
}
