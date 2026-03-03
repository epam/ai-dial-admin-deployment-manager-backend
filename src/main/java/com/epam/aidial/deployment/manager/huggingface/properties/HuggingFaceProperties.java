package com.epam.aidial.deployment.manager.huggingface.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.huggingface")
public class HuggingFaceProperties {

    private String baseUrl;
    private String apiToken;
    private Duration tagCacheDuration;

    @Getter(AccessLevel.NONE)
    private String defaultAllowedDomains;

    /**
     * Returns the default allowed domains for HuggingFace inference deployments egress.
     * Parses the comma-separated string {@link #defaultAllowedDomains}.
     */
    public List<String> getDefaultAllowedDomains() {
        if (StringUtils.isBlank(defaultAllowedDomains)) {
            return new ArrayList<>();
        }
        return Arrays.stream(defaultAllowedDomains.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
