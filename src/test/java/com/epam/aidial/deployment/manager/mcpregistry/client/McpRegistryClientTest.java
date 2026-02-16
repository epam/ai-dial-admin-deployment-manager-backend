package com.epam.aidial.deployment.manager.mcpregistry.client;

import com.epam.aidial.deployment.manager.mcpregistry.model.ServerDetail;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServerListResponse;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServerResponse;
import com.epam.aidial.deployment.manager.mcpregistry.model.ServersRequest;
import com.epam.aidial.deployment.manager.mcpregistry.properties.McpRegistryProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpRegistryClientTest {

    private static final String SERVER_NAME = "ai.com.mcp/petstore";

    @Mock
    private OkHttpClient httpClient;
    @Mock
    private JsonMapper jsonMapper;
    @Mock
    private McpRegistryProperties properties;
    @Mock
    private Call call;

    private McpRegistryClient client;

    @BeforeEach
    void setUp() {
        when(properties.getBaseUrl()).thenReturn("https://registry.modelcontextprotocol.io");
        client = new McpRegistryClient(httpClient, jsonMapper, properties);
    }

    @Test
    void getServers_shouldReturnServers_whenRequestIsSuccessful() throws IOException {
        var request = ServersRequest.builder().search("filesystem").limit(10).build();
        var responseBodyString = "{\"servers\":[],\"metadata\":{\"count\":0}}";
        var serverListResponse = ServerListResponse.builder()
                .servers(List.of())
                .build();

        mockSuccessfulResponse(responseBodyString);
        when(jsonMapper.readValue(eq(responseBodyString), any(TypeReference.class))).thenReturn(serverListResponse);

        var result = client.getServers(request);

        assertThat(result).isNotNull();
        assertThat(result.getServers()).isEmpty();
        verify(httpClient).newCall(any(Request.class));
    }

    @Test
    void getServers_shouldThrowException_whenResponseIsNotSuccessful() throws IOException {
        var request = ServersRequest.builder().build();
        mockFailedResponse(500);

        assertThatThrownBy(() -> client.getServers(request))
                .isInstanceOf(McpRegistryClientException.class)
                .hasMessageContaining("Unexpected response code: 500");
    }

    @Test
    void getServerVersion_shouldReturnServer_whenRequestIsSuccessful() throws IOException {
        var version = "1.0.0";
        var responseBodyString = "{\"server\":{\"name\":\"ai.com.mcp/petstore\",\"version\":\"1.0.0\"}}";
        var serverResponse = ServerResponse.builder()
                .server(ServerDetail.builder()
                        .name(SERVER_NAME)
                        .version("1.0.0")
                        .build())
                .build();

        mockSuccessfulResponse(responseBodyString);
        when(jsonMapper.readValue(eq(responseBodyString), any(TypeReference.class))).thenReturn(serverResponse);

        var result = client.getServerVersion(SERVER_NAME, version);

        assertThat(result).isNotNull();
        assertThat(result.getServer()).isNotNull();
        assertThat(result.getServer().getName()).isEqualTo(SERVER_NAME);
        assertThat(result.getServer().getVersion()).isEqualTo("1.0.0");
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
        var responseBodyString = "{\"servers\":[],\"metadata\":{\"count\":0}}";
        var serverListResponse = ServerListResponse.builder().servers(List.of()).build();

        mockSuccessfulResponse(responseBodyString);
        when(jsonMapper.readValue(eq(responseBodyString), any(TypeReference.class))).thenReturn(serverListResponse);

        var result = client.getServerVersions(SERVER_NAME);

        assertThat(result).isNotNull();
        assertThat(result.getServers()).isEmpty();
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
