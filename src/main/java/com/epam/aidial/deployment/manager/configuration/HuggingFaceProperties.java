package com.epam.aidial.deployment.manager.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "huggingface")
@Getter
@Setter
public class HuggingFaceProperties {

    private String baseUrl;

    private String apiToken;

    private Duration tagCacheDuration;
}
