package com.epam.aidial.deployment.manager.huggingface.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.huggingface.client.HuggingFaceClient;
import com.epam.aidial.deployment.manager.huggingface.configuration.HuggingFaceCachingConfig;
import com.epam.aidial.deployment.manager.huggingface.model.FileRequest;
import com.epam.aidial.deployment.manager.huggingface.model.ModelsPageResponse;
import com.epam.aidial.deployment.manager.huggingface.model.ModelsRequest;
import com.epam.aidial.deployment.manager.huggingface.model.TagInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.springframework.cache.annotation.Cacheable;
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

    private static final List<String> FIELDS_TO_RETURN = List.of(
            "sha",
            "author",
            "createdAt",
            "downloads",
            "lastModified",
            "likes",
            "safetensors",
            "tags");

    private final HuggingFaceClient huggingFaceClient;

    /**
     * Get a single page of models from Hugging Face API.
     *
     * @param request request parameters
     * @param pageUrl optional URL for a specific page (if null, fetches first page)
     * @return paginated response with models and next page URL
     */
    public ModelsPageResponse getModelsPage(ModelsRequest request, String pageUrl) {
        request.setExpand(FIELDS_TO_RETURN);
        return huggingFaceClient.getModelsPage(request, pageUrl);
    }

    @Cacheable(HuggingFaceCachingConfig.HF_TAG_CACHE_NAME)
    public Map<String, TagInfo> getTagDictionary() {
        var tagsByType = huggingFaceClient.getTagsByType();
        return tagsByType.getAllTags().stream()
                .collect(toMap(TagInfo::id, Function.identity(), (a, b) -> a));
    }

    /**
     * Download a file from Hugging Face Hub.
     *
     * @param fileRequest request containing repo ID, revision, file path, and repo
     *                    type
     * @return response body containing the file content
     */
    public ResponseBody downloadFile(FileRequest fileRequest) {
        return huggingFaceClient.downloadFile(fileRequest);
    }
}
