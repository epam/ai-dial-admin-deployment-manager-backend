package com.epam.aidial.deployment.manager.mcpregistry.web.controller;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.registry.mcp.client.McpRegistryClientException;
import com.epam.aidial.deployment.manager.registry.mcp.model.ServerDetail;
import com.epam.aidial.deployment.manager.registry.mcp.model.ServerListMetadata;
import com.epam.aidial.deployment.manager.registry.mcp.service.McpRegistryService;
import com.epam.aidial.deployment.manager.registry.mcp.web.controller.McpRegistryController;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerListResponseDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerResponseDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServersRequestDto;
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
        var serviceResponse = objectMapper.readValue(serviceResponseJson, ServerListResponseDto.class);

        when(mcpRegistryService.getServers(any())).thenReturn(serviceResponse);

        mockMvc.perform(get("/api/v1/mcp-registry/servers")
                        .param("search", "everything")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(content().json(serviceResponseJson, JsonCompareMode.LENIENT));

        var captor = forClass(ServersRequestDto.class);
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
        var serviceResponse = ServerListResponseDto.builder()
                .servers(List.of(ServerResponseDto.builder()
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
        var serviceResponse = ServerResponseDto.builder()
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
        var serviceResponse = ServerListResponseDto.builder()
                .servers(List.of(ServerResponseDto.builder()
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
        var serviceResponse = ServerResponseDto.builder()
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

    // --- Filter param tests (T010, T012) ---

    @Test
    void getServers_shouldPassRemoteTypesFilter() throws Exception {
        var serviceResponse = emptyServerListResponse();
        when(mcpRegistryService.getServers(any())).thenReturn(serviceResponse);

        mockMvc.perform(get("/api/v1/mcp-registry/servers")
                        .param("remoteTypes", "sse"))
                .andExpect(status().isOk());

        var captor = forClass(ServersRequestDto.class);
        verify(mcpRegistryService).getServers(captor.capture());
        assertThat(captor.getValue().getFilter()).isNotNull();
        assertThat(captor.getValue().getFilter().getRemoteTypes()).containsExactly("sse");
    }

    @Test
    void getServers_shouldPassPackageRegistryTypesFilter() throws Exception {
        var serviceResponse = emptyServerListResponse();
        when(mcpRegistryService.getServers(any())).thenReturn(serviceResponse);

        mockMvc.perform(get("/api/v1/mcp-registry/servers")
                        .param("packageRegistryTypes", "npm", "oci"))
                .andExpect(status().isOk());

        var captor = forClass(ServersRequestDto.class);
        verify(mcpRegistryService).getServers(captor.capture());
        assertThat(captor.getValue().getFilter()).isNotNull();
        assertThat(captor.getValue().getFilter().getPackageRegistryTypes()).containsExactly("npm", "oci");
    }

    @Test
    void getServers_shouldPassRepositoryExistsFilter() throws Exception {
        var serviceResponse = emptyServerListResponse();
        when(mcpRegistryService.getServers(any())).thenReturn(serviceResponse);

        mockMvc.perform(get("/api/v1/mcp-registry/servers")
                        .param("repositoryExists", "true"))
                .andExpect(status().isOk());

        var captor = forClass(ServersRequestDto.class);
        verify(mcpRegistryService).getServers(captor.capture());
        assertThat(captor.getValue().getFilter()).isNotNull();
        assertThat(captor.getValue().getFilter().getRepositoryExists()).isTrue();
    }

    @Test
    void getServers_shouldCombineMultipleFilterParams() throws Exception {
        var serviceResponse = emptyServerListResponse();
        when(mcpRegistryService.getServers(any())).thenReturn(serviceResponse);

        mockMvc.perform(get("/api/v1/mcp-registry/servers")
                        .param("remoteTypes", "sse")
                        .param("packageRegistryTypes", "npm")
                        .param("repositoryExists", "true"))
                .andExpect(status().isOk());

        var captor = forClass(ServersRequestDto.class);
        verify(mcpRegistryService).getServers(captor.capture());
        var filter = captor.getValue().getFilter();
        assertThat(filter).isNotNull();
        assertThat(filter.getRemoteTypes()).containsExactly("sse");
        assertThat(filter.getPackageRegistryTypes()).containsExactly("npm");
        assertThat(filter.getRepositoryExists()).isTrue();
    }

    @Test
    void getServers_shouldPassNullFilter_whenNoFilterParams() throws Exception {
        var serviceResponse = emptyServerListResponse();
        when(mcpRegistryService.getServers(any())).thenReturn(serviceResponse);

        mockMvc.perform(get("/api/v1/mcp-registry/servers"))
                .andExpect(status().isOk());

        var captor = forClass(ServersRequestDto.class);
        verify(mcpRegistryService).getServers(captor.capture());
        assertThat(captor.getValue().getFilter()).isNull();
    }

    @Test
    void postListServers_shouldPassFilterFromBody() throws Exception {
        var serviceResponse = emptyServerListResponse();
        when(mcpRegistryService.getServers(any())).thenReturn(serviceResponse);

        var body = """
                {
                    "limit": 50,
                    "filter": {
                        "remoteTypes": ["sse", "streamable-http"],
                        "packageRegistryTypes": ["npm"],
                        "repositoryExists": true
                    }
                }
                """;

        mockMvc.perform(post("/api/v1/mcp-registry/servers/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        var captor = forClass(ServersRequestDto.class);
        verify(mcpRegistryService).getServers(captor.capture());
        var filter = captor.getValue().getFilter();
        assertThat(filter).isNotNull();
        assertThat(filter.getRemoteTypes()).containsExactly("sse", "streamable-http");
        assertThat(filter.getPackageRegistryTypes()).containsExactly("npm");
        assertThat(filter.getRepositoryExists()).isTrue();
    }

    @Test
    void getServers_shouldIncludeNextCursor_whenMoreResultsExist() throws Exception {
        var serviceResponse = ServerListResponseDto.builder()
                .servers(List.of())
                .metadata(ServerListMetadata.builder().nextCursor("cursor-abc").count(0).build())
                .build();
        when(mcpRegistryService.getServers(any())).thenReturn(serviceResponse);

        mockMvc.perform(get("/api/v1/mcp-registry/servers")
                        .param("remoteTypes", "sse"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"metadata\":{\"nextCursor\":\"cursor-abc\"}}", JsonCompareMode.LENIENT));
    }

    @Test
    void getServers_shouldPassCursorWithFilter() throws Exception {
        var serviceResponse = emptyServerListResponse();
        when(mcpRegistryService.getServers(any())).thenReturn(serviceResponse);

        mockMvc.perform(get("/api/v1/mcp-registry/servers")
                        .param("cursor", "cursor-abc")
                        .param("remoteTypes", "sse"))
                .andExpect(status().isOk());

        var captor = forClass(ServersRequestDto.class);
        verify(mcpRegistryService).getServers(captor.capture());
        assertThat(captor.getValue().getCursor()).isEqualTo("cursor-abc");
        assertThat(captor.getValue().getFilter()).isNotNull();
    }

    private static ServerListResponseDto emptyServerListResponse() {
        return ServerListResponseDto.builder()
                .servers(List.of())
                .metadata(ServerListMetadata.builder().count(0).build())
                .build();
    }
}
