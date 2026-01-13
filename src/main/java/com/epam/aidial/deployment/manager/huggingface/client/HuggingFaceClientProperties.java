package com.epam.aidial.deployment.manager.huggingface.client;

import com.epam.aidial.deployment.manager.configuration.HuggingFaceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HuggingFaceClientProperties {

    private final HuggingFaceProperties properties;

    public String getBaseUrl() {
        return properties.getBaseUrl();
    }

    public String getApiToken() {
        return properties.getApiToken();
    }
}
