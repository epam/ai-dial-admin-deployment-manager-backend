package com.epam.aidial.deployment.manager.mcpregistry.client;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.registry.mcp.client.McpRegistryClient;
import com.epam.aidial.deployment.manager.registry.mcp.client.McpRegistryClientException;
import com.epam.aidial.deployment.manager.registry.mcp.properties.McpRegistryProperties;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServersRequestDto;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpRegistryClientTest {

    private static final String SERVER_NAME = "ai.com.mcp/petstore";

    @Mock
    private OkHttpClient httpClient;
    @Mock
    private McpRegistryProperties properties;
    @Mock
    private Call call;

    private McpRegistryClient client;

    @BeforeEach
    void setUp() {
        when(properties.getBaseUrl()).thenReturn("https://registry.modelcontextprotocol.io");
        client = new McpRegistryClient(httpClient, JsonMapperConfiguration.createJsonMapper(), properties);
    }

    @Test
    void getServers_shouldReturnServers_whenRequestIsSuccessful() throws IOException {
        var request = ServersRequestDto.builder().search("filesystem").limit(10).build();
        var responseBodyString = ResourceUtils.readResource("/mcp-registry/servers_page.json");

        mockSuccessfulResponse(responseBodyString);

        var result = client.getServers(request);

        assertThat(result).isNotNull();
        assertThat(result.getServers()).hasSize(1);
        assertThat(result.getMetadata()).isNotNull();
        assertThat(result.getMetadata().getCount()).isEqualTo(1);
        assertThat(result.getMetadata().getNextCursor()).isEqualTo("ai.com.mcp%2Fpetstore:1.0.0");
        assertThat(result.getServers().get(0).getServer().getName()).isEqualTo(SERVER_NAME);
        assertThat(result.getServers().get(0).getServer().getVersion()).isEqualTo("1.0.0");
        assertThat(result.getServers().get(0).getServer().getTitle()).isEqualTo("Petstore");
        verify(httpClient).newCall(any(Request.class));
    }

    @Test
    void getServers_shouldThrowException_whenResponseIsNotSuccessful() throws IOException {
        var request = ServersRequestDto.builder().build();
        mockFailedResponse(500);

        assertThatThrownBy(() -> client.getServers(request))
                .isInstanceOf(McpRegistryClientException.class)
                .hasMessageContaining("Unexpected response code: 500");
    }

    @Test
    void getServerVersion_shouldReturnServer_whenRequestIsSuccessful() throws IOException {
        var version = "1.0.0";
        var responseBodyString = ResourceUtils.readResource("/mcp-registry/server_version.json");

        mockSuccessfulResponse(responseBodyString);

        var result = client.getServerVersion(SERVER_NAME, version);

        assertThat(result).isNotNull();
        assertThat(result.getServer()).isNotNull();
        assertThat(result.getServer().getName()).isEqualTo(SERVER_NAME);
        assertThat(result.getServer().getVersion()).isEqualTo("1.0.0");
        assertThat(result.getServer().getTitle()).isEqualTo("Petstore");
        assertThat(result.getServer().getDescription()).isEqualTo("MCP server that exposes all tools, resources and prompts");
        assertThat(result.getMeta()).isNotNull();
        verify(httpClient).newCall(any(Request.class));
    }

    @Test
    void getServerVersion_shouldThrowException_whenResponseIsNotFound() throws IOException {
        mockFailedResponse(404);

        assertThatThrownBy(() -> client.getServerVersion("io.example/unknown", "1.0.0"))
                .isInstanceOf(McpRegistryClientException.class)
                .hasMessageContaining("Unexpected response code: 404");
    }

    @Test
    void getServerVersions_shouldReturnList_whenRequestIsSuccessful() throws IOException {
        var responseBodyString = ResourceUtils.readResource("/mcp-registry/servers_page.json");

        mockSuccessfulResponse(responseBodyString);

        var result = client.getServerVersions(SERVER_NAME);

        assertThat(result).isNotNull();
        assertThat(result.getServers()).hasSize(1);
        assertThat(result.getMetadata()).isNotNull();
        assertThat(result.getMetadata().getCount()).isEqualTo(1);
        assertThat(result.getServers().get(0).getServer().getName()).isEqualTo(SERVER_NAME);
        assertThat(result.getServers().get(0).getServer().getVersion()).isEqualTo("1.0.0");
        verify(httpClient).newCall(any(Request.class));
    }

    private void mockSuccessfulResponse(String body) throws IOException {
        var response = new Response.Builder()
                .request(new Request.Builder().url("https://registry.modelcontextprotocol.io").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build();

        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
    }

    private void mockFailedResponse(int code) throws IOException {
        var response = new Response.Builder()
                .request(new Request.Builder().url("https://registry.modelcontextprotocol.io").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Error")
                .body(ResponseBody.create("error", MediaType.get("application/json")))
                .build();

        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
    }
}
