package com.epam.aidial.deployment.manager.huggingface.service;

import com.epam.aidial.deployment.manager.configuration.HuggingFaceProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.huggingface.client.HuggingFaceClient;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceFileRequest;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModelsPageResponse;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModelsRequest;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceTagInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class HuggingFaceService {

    private final HuggingFaceClient huggingFaceClient;
    private final HuggingFaceProperties properties;

    private volatile Map<String, HuggingFaceTagInfo> tagDictionary;
    private volatile long lastTagCacheUpdate = 0;

    /**
     * Get a single page of models from Hugging Face API.
     *
     * @param request request parameters
     * @param pageUrl optional URL for a specific page (if null, fetches first page)
     * @return paginated response with models and next page URL
     */
    public HuggingFaceModelsPageResponse getModelsPage(HuggingFaceModelsRequest request, String pageUrl) {
        return huggingFaceClient.getModelsPage(request, pageUrl);
    }

    public Map<String, HuggingFaceTagInfo> getTagDictionary() {
        var now = System.currentTimeMillis();
        var cacheDurationMillis = properties.getTagCacheDuration().toMillis();

        if (tagDictionary == null || (now - lastTagCacheUpdate) > cacheDurationMillis) {
            synchronized (this) {
                now = System.currentTimeMillis();
                if (tagDictionary == null || (now - lastTagCacheUpdate) > cacheDurationMillis) {
                    var tagsByType = huggingFaceClient.getTagsByType();
                    tagDictionary = tagsByType.values().stream()
                            .flatMap(List::stream)
                            .collect(toMap(HuggingFaceTagInfo::id, Function.identity(), (a, b) -> a));
                    lastTagCacheUpdate = now;
                }
            }
        }
        return tagDictionary;
    }

    /**
     * Download a file from Hugging Face Hub.
     *
     * @param fileRequest request containing repo ID, revision, file path, and repo type
     * @return response body containing the file content
     */
    public ResponseBody downloadFile(HuggingFaceFileRequest fileRequest) {
        return huggingFaceClient.downloadFile(fileRequest);
    }
}
