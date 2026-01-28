package com.epam.aidial.deployment.manager.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "huggingface")
public class HuggingFaceProperties {
    private String baseUrl;
    private String apiToken;
    private Duration tagCacheDuration;
}
