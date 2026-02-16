package com.epam.aidial.deployment.manager.mcpregistry.web.controller;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.mcpregistry.client.McpRegistryClientException;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServerDetail;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServerListMetadata;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServerListResponse;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServerResponse;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServersRequest;
import com.epam.aidial.deployment.manager.mcpregistry.service.McpRegistryService;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.epam.aidial.deployment.manager.web.controller.none.AbstractControllerNoneSecureTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = McpRegistryController.class)
@Import(JsonMapperConfiguration.class)
class McpRegistryControllerTest extends AbstractControllerNoneSecureTest {

    /** Server name (namespace/name). Path uses two segments so slash does not cause 400. */
    private static final String SERVER_NAME = "ai.com.mcp/petstore";
    private static final String NAMESPACE = "ai.com.mcp";
    private static final String NAME = "petstore";

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private McpRegistryService mcpRegistryService;

    @Test
    void getServers_shouldReturnServers_whenRequestIsSuccessful() throws Exception {
        var serviceResponseJson = ResourceUtils.readResource("/mcp-registry/servers_page.json");
        var serviceResponse = objectMapper.readValue(serviceResponseJson, ServerListResponse.class);

        when(mcpRegistryService.getServers(any())).thenReturn(serviceResponse);

        mockMvc.perform(get("/api/v1/mcp-registry/servers")
                        .param("search", "everything")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(content().json(serviceResponseJson, JsonCompareMode.LENIENT));

        var captor = forClass(ServersRequest.class);
        verify(mcpRegistryService).getServers(captor.capture());
        var capturedRequest = captor.getValue();
        assertThat(capturedRequest.getSearch()).isEqualTo("everything");
        assertThat(capturedRequest.getLimit()).isEqualTo(50);
    }

    @Test
    void getServers_shouldReturnErrorStatus_whenUpstreamFails() throws Exception {
        when(mcpRegistryService.getServers(any()))
                .thenThrow(new McpRegistryClientException("Server not found", 404));

        mockMvc.perform(get("/api/v1/mcp-registry/servers"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getServerVersions_shouldReturnVersions_whenRequestIsSuccessful() throws Exception {
        var serviceResponse = ServerListResponse.builder()
                .servers(List.of(ServerResponse.builder()
                        .server(ServerDetail.builder()
                                .name(SERVER_NAME)
                                .version("1.0.0")
                                .build())
                        .build()))
                .metadata(ServerListMetadata.builder().count(1).build())
                .build();

        when(mcpRegistryService.getServerVersions(eq(SERVER_NAME))).thenReturn(serviceResponse);

        mockMvc.perform(get("/api/v1/mcp-registry/servers/{namespace}/{name}/versions", NAMESPACE, NAME))
                .andExpect(status().isOk());

        verify(mcpRegistryService).getServerVersions(SERVER_NAME);
    }

    @Test
    void getServerVersions_shouldReturnNotFound_whenUpstreamReturns404() throws Exception {
        when(mcpRegistryService.getServerVersions(any()))
                .thenThrow(new McpRegistryClientException("Server not found", 404));

        mockMvc.perform(get("/api/v1/mcp-registry/servers/{namespace}/{name}/versions", "io.example", "unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getServerVersion_shouldReturnServer_whenRequestIsSuccessful() throws Exception {
        var version = "1.0.0";
        var serviceResponse = ServerResponse.builder()
                .server(ServerDetail.builder()
                        .name(SERVER_NAME)
                        .description("MCP server that exposes all tools")
                        .version("1.0.0")
                        .build())
                .build();

        when(mcpRegistryService.getServerVersion(eq(SERVER_NAME), eq(version))).thenReturn(serviceResponse);

        mockMvc.perform(get("/api/v1/mcp-registry/servers/{namespace}/{name}/versions/{version}", NAMESPACE, NAME, version))
                .andExpect(status().isOk());

        verify(mcpRegistryService).getServerVersion(SERVER_NAME, version);
    }

    @Test
    void getServerVersion_shouldReturnNotFound_whenUpstreamReturns404() throws Exception {
        when(mcpRegistryService.getServerVersion(any(), any()))
                .thenThrow(new McpRegistryClientException("Server version not found", 404));

        mockMvc.perform(get("/api/v1/mcp-registry/servers/{namespace}/{name}/versions/{version}",
                        "io.example", "unknown", "1.0.0"))
                .andExpect(status().isNotFound());
    }

    @Test
    void postServerVersions_shouldListVersions_whenVersionOmitted() throws Exception {
        var serviceResponse = ServerListResponse.builder()
                .servers(List.of(ServerResponse.builder()
                        .server(ServerDetail.builder()
                                .name(SERVER_NAME)
                                .version("1.0.0")
                                .build())
                        .build()))
                .metadata(ServerListMetadata.builder().count(1).build())
                .build();

        when(mcpRegistryService.getServerVersions(eq(SERVER_NAME))).thenReturn(serviceResponse);

        mockMvc.perform(post("/api/v1/mcp-registry/servers/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serverName\":\"ai.com.mcp/petstore\"}"))
                .andExpect(status().isOk());

        verify(mcpRegistryService).getServerVersions(SERVER_NAME);
    }

    @Test
    void postServerVersions_shouldGetVersion_whenVersionPresent() throws Exception {
        var version = "1.0.0";
        var serviceResponse = ServerResponse.builder()
                .server(ServerDetail.builder()
                        .name(SERVER_NAME)
                        .version(version)
                        .build())
                .build();

        when(mcpRegistryService.getServerVersion(eq(SERVER_NAME), eq(version))).thenReturn(serviceResponse);

        mockMvc.perform(post("/api/v1/mcp-registry/servers/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serverName\":\"ai.com.mcp/petstore\",\"version\":\"1.0.0\"}"))
                .andExpect(status().isOk());

        verify(mcpRegistryService).getServerVersion(SERVER_NAME, version);
    }

    @Test
    void postServerVersions_shouldReturnBadRequest_whenServerNameMissing() throws Exception {
        mockMvc.perform(post("/api/v1/mcp-registry/servers/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isBadRequest());
    }
}
