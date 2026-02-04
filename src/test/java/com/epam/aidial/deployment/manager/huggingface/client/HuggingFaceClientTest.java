package com.epam.aidial.deployment.manager.huggingface.client;

import com.epam.aidial.deployment.manager.huggingface.model.FileRequest;
import com.epam.aidial.deployment.manager.huggingface.model.Model;
import com.epam.aidial.deployment.manager.huggingface.model.ModelsRequest;
import com.epam.aidial.deployment.manager.huggingface.model.TagsInfo;
import com.epam.aidial.deployment.manager.huggingface.properties.HuggingFaceProperties;
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
class HuggingFaceClientTest {

    @Mock
    private OkHttpClient httpClient;
    @Mock
    private JsonMapper jsonMapper;
    @Mock
    private HuggingFaceProperties properties;
    @Mock
    private Call call;

    private HuggingFaceClient client;

    @BeforeEach
    void setUp() {
        // Setup default base URL for tests
        when(properties.getBaseUrl()).thenReturn("https://huggingface.co");
        client = new HuggingFaceClient(httpClient, jsonMapper, properties);
    }

    @Test
    void getModelsPage_shouldReturnModels_whenRequestIsSuccessful() throws IOException {
        // Given
        var request = ModelsRequest.builder().search("gpt").build();
        var responseBodyString = "[{\"id\":\"gpt2\"}]";
        var models = List.of(Model.builder().id("gpt2").build());

        mockSuccessfulResponse(responseBodyString);
        when(jsonMapper.readValue(eq(responseBodyString), any(TypeReference.class))).thenReturn(models);

        // When
        var result = client.getModelsPage(request, null);

        // Then
        assertThat(result.getModels()).hasSize(1);
        assertThat(result.getModels().get(0).getId()).isEqualTo("gpt2");
        verify(httpClient).newCall(any(Request.class));
    }

    @Test
    void getModelsPage_shouldThrowException_whenResponseIsNotSuccessful() throws IOException {
        // Given
        var request = ModelsRequest.builder().build();
        mockFailedResponse(404);

        // When/Then
        assertThatThrownBy(() -> client.getModelsPage(request, null))
                .isInstanceOf(HuggingFaceClientException.class)
                .hasMessageContaining("Unexpected response code: 404");
    }

    @Test
    void getTagsByType_shouldReturnTags_whenRequestIsSuccessful() throws IOException {
        // Given
        var responseBodyString = "{\"libraries\":[]}";
        var tagsInfo = new TagsInfo();

        mockSuccessfulResponse(responseBodyString);
        when(jsonMapper.readValue(eq(responseBodyString), any(TypeReference.class))).thenReturn(tagsInfo);

        // When
        var result = client.getTagsByType();

        // Then
        assertThat(result).isNotNull();
        verify(httpClient).newCall(any(Request.class));
    }

    @Test
    void downloadFile_shouldReturnBody_whenRequestIsSuccessful() throws IOException {
        // Given
        var fileRequest = FileRequest.builder()
                .modelName("gpt2")
                .revision("main")
                .filePath("config.json")
                .build();
        var expectedContent = "{}";

        mockSuccessfulResponse(expectedContent);

        // When
        var result = client.downloadFile(fileRequest);

        // Then
        assertThat(result).isNotNull();
        // Since we are mocking the response body, we can't easily read it strictly without consuming the mocked stream
        // but verifying we got a body is enough for unit test of logic flow
    }

    @Test
    void getModelsPage_shouldThrowException_whenHostMismatch() {
        var request = ModelsRequest.builder().build();
        var invalidUrl = "https://evil.com/api/models";

        assertThatThrownBy(() -> client.getModelsPage(request, invalidUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid page URL: https://evil.com/api/models")
                .hasMessageContaining("It must match base URL scheme, host and port. Base URL: https://huggingface.co");
    }

    @Test
    void getModelsPage_shouldThrowException_whenSchemeMismatch() {
        var request = ModelsRequest.builder().build();
        var invalidUrl = "http://huggingface.co/api/models";

        assertThatThrownBy(() -> client.getModelsPage(request, invalidUrl))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getModelsPage_shouldThrowException_whenPortMismatch() {
        var request = ModelsRequest.builder().build();
        // Update base URL to include port for this specific test setup if needed, 
        // or just test against standard (implicitly 443 for https) vs explicit different port
        var invalidUrl = "https://huggingface.co:8080/api/models";

        assertThatThrownBy(() -> client.getModelsPage(request, invalidUrl))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getModelsPage_shouldThrowException_whenUrlIsMalformed() {
        var request = ModelsRequest.builder().build();
        var invalidUrl = "not-a-url";

        assertThatThrownBy(() -> client.getModelsPage(request, invalidUrl))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void mockSuccessfulResponse(String body) throws IOException {
        var response = new Response.Builder()
                .request(new Request.Builder().url("https://huggingface.co").build())
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
                .request(new Request.Builder().url("https://huggingface.co").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Error")
                .body(ResponseBody.create("error", MediaType.get("application/json")))
                .build();

        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
    }
}
