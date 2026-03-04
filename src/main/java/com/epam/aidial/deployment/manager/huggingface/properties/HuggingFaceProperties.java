package com.epam.aidial.deployment.manager.huggingface.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;

import static java.util.stream.Collectors.toList;

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

    @Getter(AccessLevel.NONE)
    private List<String> parsedDefaultAllowedDomains;

    @PostConstruct
    void initDefaultAllowedDomains() {
        if (StringUtils.isBlank(defaultAllowedDomains)) {
            parsedDefaultAllowedDomains = List.of();
        } else {
            parsedDefaultAllowedDomains = Stream.of(defaultAllowedDomains.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(toList());
        }
    }

    /**
     * Returns the default allowed domains for HuggingFace inference deployments egress.
     * Parsed once at startup from the comma-separated {@link #defaultAllowedDomains} property.
     * Null check is required for cases when method is called before init is done.
     */
    public List<String> getDefaultAllowedDomains() {
        return parsedDefaultAllowedDomains == null
                ? List.of()
                : Collections.unmodifiableList(parsedDefaultAllowedDomains);
    }
}
