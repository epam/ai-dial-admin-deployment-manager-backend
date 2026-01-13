package com.epam.aidial.deployment.manager.huggingface.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.huggingface.client.HuggingFaceClient;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModelsPageResponse;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModelsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class HuggingFaceService {

    private final HuggingFaceClient huggingFaceClient;

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
}
