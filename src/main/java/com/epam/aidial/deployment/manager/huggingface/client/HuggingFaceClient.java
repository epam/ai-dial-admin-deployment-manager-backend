package com.epam.aidial.deployment.manager.huggingface.client;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModel;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModelsPageResponse;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModelsRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class HuggingFaceClient {

    private static final String MODELS_ENDPOINT = "/api/models";
    private static final Pattern LINK_PATTERN = Pattern.compile("<([^>]+)>;\\s*rel=\"([^\"]+)\"");

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
        String url = pageUrl != null ? pageUrl : buildUrl(request);
        
        try {
            ModelsPageResponse pageResponse = fetchModelsPage(url);
            String nextPageUrl = extractUrlByRel(pageResponse.linkHeader(), "next");
            String prevPageUrl = extractUrlByRel(pageResponse.linkHeader(), "prev");
            
            return HuggingFaceModelsPageResponse.builder()
                    .models(pageResponse.models())
                    .nextPageUrl(nextPageUrl)
                    .hasNextPage(nextPageUrl != null)
                    .prevPageUrl(prevPageUrl)
                    .hasPrevPage(prevPageUrl != null)
                    .build();
        } catch (IOException e) {
            log.error("Error fetching models page from Hugging Face API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch models page from Hugging Face API", e);
        }
    }

    private ModelsPageResponse fetchModelsPage(String url) throws IOException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .get();

        if (properties.getApiToken() != null && !properties.getApiToken().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + properties.getApiToken());
        }

        Request request = requestBuilder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response body is null");
            }

            String linkHeader = response.header("Link");
            List<HuggingFaceModel> models = jsonMapper.readValue(body.string(), new TypeReference<>() {});

            return new ModelsPageResponse(models, linkHeader);
        }
    }

    private String buildUrl(HuggingFaceModelsRequest request) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(properties.getBaseUrl() + MODELS_ENDPOINT)
                .newBuilder();

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

    private String extractUrlByRel(String linkHeader, String relType) {
        if (linkHeader == null || linkHeader.isBlank()) {
            return null;
        }

        String[] links = linkHeader.split(",");
        for (String link : links) {
            Matcher matcher = LINK_PATTERN.matcher(link.trim());
            if (matcher.matches()) {
                String url = matcher.group(1);
                String rel = matcher.group(2);
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
