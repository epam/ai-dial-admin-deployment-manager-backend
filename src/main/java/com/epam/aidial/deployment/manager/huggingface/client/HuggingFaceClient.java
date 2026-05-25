package com.epam.aidial.deployment.manager.huggingface.client;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.huggingface.model.FileRequest;
import com.epam.aidial.deployment.manager.huggingface.model.Model;
import com.epam.aidial.deployment.manager.huggingface.model.ModelConfig;
import com.epam.aidial.deployment.manager.huggingface.model.ModelsPageResponse;
import com.epam.aidial.deployment.manager.huggingface.model.ModelsRequest;
import com.epam.aidial.deployment.manager.huggingface.model.TagsInfo;
import com.epam.aidial.deployment.manager.huggingface.properties.HuggingFaceProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
@LogExecution
public class HuggingFaceClient {

    private static final String MODELS_ENDPOINT = "/api/models";
    private static final String TAGS_BY_TYPE_ENDPOINT = "/api/models-tags-by-type";
    private static final String CONFIG_JSON_PATH = "config.json";
    private static final String MAIN_REVISION = "main";
    private static final Pattern LINK_PATTERN = Pattern.compile("<([^>]+)>;\\s*rel=\"([^\"]+)\"");

    private final OkHttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final HuggingFaceProperties properties;
    private final HttpUrl baseHttpUrl;

    public HuggingFaceClient(OkHttpClient httpClient, JsonMapper jsonMapper, HuggingFaceProperties properties) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.baseHttpUrl = HttpUrl.parse(properties.getBaseUrl());
    }

    /**
     * Get a single page of models from Hugging Face API.
     *
     * @param request request parameters
     * @param pageUrl optional URL for a specific page (if null, fetches first page)
     * @return paginated response with models and next page URL
     */
    public ModelsPageResponse getModelsPage(ModelsRequest request, String pageUrl) {
        log.debug("Retrieving huggingface models. Request: {}. Page URL: {}. Base URL: {}.",
                request, pageUrl, properties.getBaseUrl());
        String url;
        if (StringUtils.isNotBlank(pageUrl)) {
            validatePageUrl(pageUrl);
            url = pageUrl;
        } else {
            url = buildUrl(request);
        }

        var pageResponse = fetchModelsPage(url);
        var nextPageUrl = extractUrlByRel(pageResponse.linkHeader(), "next");
        var prevPageUrl = extractUrlByRel(pageResponse.linkHeader(), "prev");

        var result = ModelsPageResponse.builder()
                .models(pageResponse.models())
                .nextPageUrl(nextPageUrl)
                .prevPageUrl(prevPageUrl)
                .build();
        log.debug("huggingface models were retrieved. Models count: {}. Request: {}. Page URL: {}. Base URL: {}.",
                pageResponse.models().size(), request, pageUrl, properties.getBaseUrl());
        return result;
    }

    /**
     * Get a single model's metadata by repository identifier.
     *
     * @param modelName HuggingFace repository identifier (e.g. {@code "distilbert/...sst-2-english"})
     * @return populated {@link Model} record
     * @throws HuggingFaceClientException on any non-2xx response or transport error
     */
    public Model getModel(String modelName) {
        log.debug("Retrieving huggingface model. Model: {}. Base URL: {}", modelName, properties.getBaseUrl());
        var url = HttpUrl.parse(properties.getBaseUrl() + MODELS_ENDPOINT + "/" + modelName);
        var requestBuilder = new Request.Builder().url(url).get();
        if (StringUtils.isNotBlank(properties.getApiToken())) {
            requestBuilder.header("Authorization", "Bearer " + properties.getApiToken());
        }
        var request = requestBuilder.build();

        try (var response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new HuggingFaceClientException("Unexpected response code: " + response.code(), response.code());
            }
            var body = response.body();
            if (body == null) {
                throw new HuggingFaceClientException("Response body is null", HttpStatus.INTERNAL_SERVER_ERROR.value());
            }
            var result = jsonMapper.readValue(body.string(), Model.class);
            log.debug("huggingface model retrieved. Model: {}. Base URL: {}", modelName, properties.getBaseUrl());
            return result;
        } catch (HuggingFaceClientException e) {
            throw e;
        } catch (Exception e) {
            var errorMessage = "Error fetching model from Hugging Face API";
            log.warn(errorMessage, e);
            throw new HuggingFaceClientException(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /**
     * Fetch and parse the model's {@code config.json} at the given revision. Pass the
     * {@code sha} returned by {@link #getModel(String)} to pin the config to the same
     * snapshot — otherwise it can drift if the model is updated between calls. Falls back
     * to {@code main} when the revision is blank.
     *
     * @param modelName HuggingFace repository identifier
     * @param revision  commit sha or branch name; if blank, {@code main} is used
     * @return parsed {@link ModelConfig} with the fields relevant to inference-task detection
     * @throws HuggingFaceClientException on any non-2xx response or transport error
     */
    public ModelConfig fetchModelConfig(String modelName, @Nullable String revision) {
        var resolvedRevision = StringUtils.defaultIfBlank(revision, MAIN_REVISION);
        log.debug("Retrieving huggingface model config.json. Model: {}. Revision: {}. Base URL: {}",
                modelName, resolvedRevision, properties.getBaseUrl());
        var fileRequest = FileRequest.builder()
                .modelName(modelName)
                .revision(resolvedRevision)
                .filePath(CONFIG_JSON_PATH)
                .build();
        try (var body = downloadFile(fileRequest)) {
            var result = jsonMapper.readValue(body.string(), ModelConfig.class);
            log.debug("huggingface model config.json retrieved. Model: {}. Revision: {}", modelName, resolvedRevision);
            return result;
        } catch (HuggingFaceClientException e) {
            throw e;
        } catch (JsonProcessingException e) {
            var errorMessage = "Malformed config.json from Hugging Face for model '%s'".formatted(modelName);
            log.warn(errorMessage, e);
            throw new HuggingFaceMalformedResponseException(errorMessage, e);
        } catch (Exception e) {
            var errorMessage = "Error parsing config.json from Hugging Face for model '%s'".formatted(modelName);
            log.warn(errorMessage, e);
            throw new HuggingFaceClientException(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    public TagsInfo getTagsByType() {
        log.debug("Retrieving huggingface tags. Base URL: {}", properties.getBaseUrl());
        var url = HttpUrl.parse(properties.getBaseUrl() + TAGS_BY_TYPE_ENDPOINT);
        var request = new Request.Builder().url(url).get().build();

        try (var response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new HuggingFaceClientException("Unexpected response code: " + response.code(), response.code());
            }

            var body = response.body();
            if (body == null) {
                throw new HuggingFaceClientException("Response does not have response body. Response code: "
                        + response.code(), response.code());
            }

            var result = jsonMapper.readValue(body.string(), new TypeReference<TagsInfo>() {
            });
            log.debug("huggingface tags has been successfully retrieved. Base URL: {}", properties.getBaseUrl());
            return result;
        } catch (HuggingFaceClientException e) {
            throw e;
        } catch (Exception e) {
            var errorMessage = "Error fetching tags by type from Hugging Face API";
            log.warn(errorMessage, e);
            throw new HuggingFaceClientException(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /**
     * Download a file from Hugging Face Hub using the resolve URL pattern.
     * This method handles LFS files which redirect to CDN locations.
     *
     * @param fileRequest request containing repo ID, revision, file path, and repo type
     * @return response body containing the file content
     * @throws HuggingFaceClientException if download fails
     */
    public ResponseBody downloadFile(FileRequest fileRequest) {
        log.debug("Downloading huggingface model file. Request: {}. Base URL: {}",
                fileRequest, properties.getBaseUrl());
        var url = buildFileUrl(fileRequest);

        var requestBuilder = new Request.Builder().url(url).get();

        if (StringUtils.isNotBlank(properties.getApiToken())) {
            requestBuilder.header("Authorization", "Bearer " + properties.getApiToken());
        }

        var request = requestBuilder.build();

        try {
            var response = httpClient.newCall(request).execute();

            if (!response.isSuccessful()) {
                response.close();
                var errorMessage = "Failed to download file: %s (HTTP %d)"
                        .formatted(fileRequest.getFilePath(), response.code());
                throw new HuggingFaceClientException(errorMessage, response.code());
            }

            var body = response.body();
            if (body == null) {
                response.close(); // Close on error
                throw new HuggingFaceClientException("Response body is null", HttpStatus.INTERNAL_SERVER_ERROR.value());
            }

            log.debug("Stream to download huggingface model file has been successfully opened."
                    + " Request: {}. Base URL: {}", fileRequest, properties.getBaseUrl());
            return body;

        } catch (HuggingFaceClientException e) {
            throw e;
        } catch (Exception e) {
            var errorMessage = "Failed to download file from Hugging Face";
            log.warn(errorMessage, e);
            throw new HuggingFaceClientException(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private InternalModelsPageResponse fetchModelsPage(String url) {
        var requestBuilder = new Request.Builder().url(url).get();

        if (StringUtils.isNotBlank(properties.getApiToken())) {
            requestBuilder.header("Authorization", "Bearer " + properties.getApiToken());
        }

        var request = requestBuilder.build();

        try (var response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new HuggingFaceClientException("Unexpected response code: " + response.code(), response.code());
            }

            var body = response.body();
            if (body == null) {
                throw new HuggingFaceClientException("Response body is null", HttpStatus.INTERNAL_SERVER_ERROR.value());
            }

            var linkHeader = response.header("Link");
            var models = jsonMapper.readValue(body.string(), new TypeReference<List<Model>>() {
            });

            return new InternalModelsPageResponse(models, linkHeader);
        } catch (HuggingFaceClientException e) {
            throw e;
        } catch (Exception e) {
            var errorMessage = "Error fetching models page from Hugging Face API";
            log.warn(errorMessage, e);
            throw new HuggingFaceClientException(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private void validatePageUrl(String pageUrl) {
        var parsedPageUrl = HttpUrl.parse(pageUrl);

        if (baseHttpUrl == null || parsedPageUrl == null
                || !parsedPageUrl.scheme().equals(baseHttpUrl.scheme())
                || !parsedPageUrl.host().equals(baseHttpUrl.host())
                || parsedPageUrl.port() != baseHttpUrl.port()) {
            throw new IllegalArgumentException(String.format(
                    "Invalid page URL: %s. It must match base URL scheme, host and port. Base URL: %s",
                    pageUrl, baseHttpUrl));
        }
    }

    private String buildUrl(ModelsRequest request) {
        var urlBuilder = HttpUrl.parse(properties.getBaseUrl() + MODELS_ENDPOINT).newBuilder();

        if (StringUtils.isNotBlank(request.getSearch())) {
            urlBuilder.addQueryParameter("search", request.getSearch());
        }
        if (StringUtils.isNotBlank(request.getAuthor())) {
            urlBuilder.addQueryParameter("author", request.getAuthor());
        }
        if (StringUtils.isNotBlank(request.getFilter())) {
            urlBuilder.addQueryParameter("filter", request.getFilter());
        }
        if (StringUtils.isNotBlank(request.getSort())) {
            urlBuilder.addQueryParameter("sort", request.getSort());
        }
        if (request.getLimit() != null) {
            urlBuilder.addQueryParameter("limit", String.valueOf(request.getLimit()));
        }
        if (request.getExpand() != null) {
            request.getExpand().forEach(value -> urlBuilder.addQueryParameter("expand", value));
        }

        return urlBuilder.build().toString();
    }

    /**
     * Build the Hugging Face resolve URL for file download from models.
     * Pattern: /{repoId}/resolve/{revision}/{filePath}
     */
    private String buildFileUrl(FileRequest fileRequest) {
        var path = String.format("/%s/resolve/%s/%s",
                fileRequest.getModelName(),
                fileRequest.getRevision(),
                fileRequest.getFilePath());

        return properties.getBaseUrl() + path;
    }

    private String extractUrlByRel(@Nullable String linkHeader, String relType) {
        if (StringUtils.isBlank(linkHeader)) {
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

    private record InternalModelsPageResponse(
            List<Model> models,
            @Nullable String linkHeader
    ) {
    }
}
