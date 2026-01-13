package com.epam.aidial.deployment.manager.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "huggingface")
@Getter
@Setter
public class HuggingFaceProperties {
    /**
     * Base URL for Hugging Face API.
     */
    private String baseUrl = "https://huggingface.co";

    /**
     * API token for authentication (optional).
     */
    private String apiToken;
}
