package com.epam.aidial.deployment.manager.huggingface.client;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceFileRequest;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModel;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModelsPageResponse;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModelsRequest;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceTagInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class HuggingFaceClient {

    private static final String MODELS_ENDPOINT = "/api/models";
    private static final String TAGS_BY_TYPE_ENDPOINT = "/api/models-tags-by-type";
    private static final Pattern LINK_PATTERN = Pattern.compile("<([^>]+)>;\\s*rel=\"([^\"]+)\"");

    private static final List<String> FIELDS_TO_RETURN = List.of(
            "author",
            "cardData",
            "createdAt",
            "downloads",
            "lastModified",
            "likes",
            "safetensors",
            "tags"
    );

    private final OkHttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final HuggingFaceClientProperties properties;

    /**
     * Get a single page of models from Hugging Face API.
     *
     * @param request request parameters
     * @param pageUrl optional URL for a specific page (if null, fetches first page)
     * @return paginated response with models and next page URL
     */
    public HuggingFaceModelsPageResponse getModelsPage(HuggingFaceModelsRequest request, String pageUrl) {
        var url = pageUrl != null ? pageUrl : buildUrl(request);

        try {
            var pageResponse = fetchModelsPage(url);
            var nextPageUrl = extractUrlByRel(pageResponse.linkHeader(), "next");
            var prevPageUrl = extractUrlByRel(pageResponse.linkHeader(), "prev");

            return HuggingFaceModelsPageResponse.builder()
                    .models(pageResponse.models())
                    .nextPageUrl(nextPageUrl)
                    .hasNextPage(nextPageUrl != null)
                    .prevPageUrl(prevPageUrl)
                    .hasPrevPage(prevPageUrl != null)
                    .build();
        } catch (IOException e) {
            log.warn("Error fetching models page from Hugging Face API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch models page from Hugging Face API", e);
        }
    }

    public Map<String, List<HuggingFaceTagInfo>> getTagsByType() {
        var url = HttpUrl.parse(properties.getBaseUrl() + TAGS_BY_TYPE_ENDPOINT);
        var request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (var response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response.code());
            }

            var body = response.body();
            if (body == null) {
                return Collections.emptyMap();
            }

            return jsonMapper.readValue(body.string(), new TypeReference<>() {
            });
        } catch (IOException e) {
            log.error("Error fetching tags by type from Hugging Face API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch tags by type from Hugging Face API", e);
        }
    }

    /**
     * Download a file from Hugging Face Hub using the resolve URL pattern.
     * This method handles LFS files which redirect to CDN locations.
     *
     * @param fileRequest request containing repo ID, revision, file path, and repo type
     * @return response body containing the file content
     * @throws RuntimeException if download fails
     */
    public ResponseBody downloadFile(HuggingFaceFileRequest fileRequest) {
        var url = buildFileUrl(fileRequest);

        var requestBuilder = new Request.Builder().url(url).get();

        if (properties.getApiToken() != null && !properties.getApiToken().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + properties.getApiToken());
        }

        var request = requestBuilder.build();

        try {
            var response = httpClient.newCall(request).execute();

            if (!response.isSuccessful()) {
                response.close();
                var errorMessage = String.format(
                        "Failed to download file: %s (HTTP %d)",
                        fileRequest.getFilePath(),
                        response.code());
                throw new HuggingFaceClientException(errorMessage, response.code());
            }

            var body = response.body();
            if (body == null) {
                response.close(); // Close on error
                throw new IOException("Response body is null");
            }

            return body;

        } catch (IOException e) {
            var message = "Failed to download file from Hugging Face";
            log.warn(message, e);
            throw new RuntimeException(message, e);
        }
    }

    private ModelsPageResponse fetchModelsPage(String url) throws IOException {
        var requestBuilder = new Request.Builder().url(url).get();

        if (properties.getApiToken() != null && !properties.getApiToken().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + properties.getApiToken());
        }

        var request = requestBuilder.build();

        try (var response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response.code());
            }

            var body = response.body();
            if (body == null) {
                throw new IOException("Response body is null");
            }

            var linkHeader = response.header("Link");
            var models = jsonMapper.readValue(body.string(), new TypeReference<List<HuggingFaceModel>>() {
            });

            return new ModelsPageResponse(models, linkHeader);
        }
    }

    private String buildUrl(HuggingFaceModelsRequest request) {
        var urlBuilder = HttpUrl.parse(properties.getBaseUrl() + MODELS_ENDPOINT).newBuilder();
        FIELDS_TO_RETURN.forEach(value -> urlBuilder.addQueryParameter("expand", value));

        if (request.getSearch() != null && !request.getSearch().isBlank()) {
            urlBuilder.addQueryParameter("search", request.getSearch());
        }
        if (request.getAuthor() != null && !request.getAuthor().isBlank()) {
            urlBuilder.addQueryParameter("author", request.getAuthor());
        }
        if (request.getFilter() != null && !request.getFilter().isBlank()) {
            urlBuilder.addQueryParameter("filter", request.getFilter());
        }
        if (request.getSort() != null && !request.getSort().isBlank()) {
            urlBuilder.addQueryParameter("sort", request.getSort());
        }
        if (request.getDirection() != null && !request.getDirection().isBlank()) {
            urlBuilder.addQueryParameter("direction", request.getDirection());
        }
        if (request.getLimit() != null) {
            urlBuilder.addQueryParameter("limit", String.valueOf(request.getLimit()));
        }
        if (request.getFull() != null) {
            urlBuilder.addQueryParameter("full", String.valueOf(request.getFull()));
        }
        if (request.getConfig() != null) {
            urlBuilder.addQueryParameter("config", String.valueOf(request.getConfig()));
        }

        return urlBuilder.build().toString();
    }

    /**
     * Build the Hugging Face resolve URL for file download from models.
     * Pattern: /{repoId}/resolve/{revision}/{filePath}
     */
    private String buildFileUrl(HuggingFaceFileRequest fileRequest) {
        var path = String.format("/%s/resolve/%s/%s",
                fileRequest.getRepoId(),
                fileRequest.getRevision(),
                fileRequest.getFilePath());

        return properties.getBaseUrl() + path;
    }

    private String extractUrlByRel(String linkHeader, String relType) {
        if (linkHeader == null || linkHeader.isBlank()) {
            return null;
        }

        var links = linkHeader.split(",");
        for (var link : links) {
            var matcher = LINK_PATTERN.matcher(link.trim());
            if (matcher.matches()) {
                var url = matcher.group(1);
                var rel = matcher.group(2);
                if (relType.equals(rel)) {
                    return url;
                }
            }
        }

        return null;
    }

    private record ModelsPageResponse(
            List<HuggingFaceModel> models,
            String linkHeader
    ) {
    }
}
