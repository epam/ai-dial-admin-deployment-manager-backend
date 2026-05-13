package com.epam.aidial.deployment.manager.web.security.apikey;

import com.epam.aidial.deployment.manager.web.security.UserRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;

@Getter
@Setter
@Slf4j
@Component
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "config.rest.security.api-key")
@ConditionalOnProperty(value = "config.rest.security.api-key.enabled", havingValue = "true")
public class ApiKeyProperties {

    private final ObjectMapper objectMapper;

    private boolean enabled;
    private String coreUrl;
    private int cacheTtlSeconds;
    private int cacheMaxSize;
    private int requestTimeoutMs;
    private String rolesMapping;
    private boolean startupProbe;

    private Map<String, Set<UserRole>> parsedRolesMapping = Collections.emptyMap();

    @PostConstruct
    public void validate() {
        if (!enabled) {
            log.debug("API-key authentication is disabled");
            return;
        }

        if (StringUtils.isBlank(coreUrl)) {
            throw new IllegalStateException(
                    "config.rest.security.api-key.enabled=true requires config.rest.security.api-key.core-url to be set");
        }

        parsedRolesMapping = parseRolesMapping(rolesMapping);
        if (parsedRolesMapping.isEmpty()) {
            throw new IllegalStateException(
                    "config.rest.security.api-key.enabled=true requires config.rest.security.api-key.roles-mapping "
                            + "to map at least one Core role to a DM authority (FULL_ADMIN / READ_ONLY_ADMIN). "
                            + "An empty mapping would authenticate every API-key caller with zero authorities "
                            + "and reject every request with 403.");
        }

        log.info("API-key authentication is enabled. Core URL: {}, cache TTL: {}s, mapped roles: {}",
                coreUrl, cacheTtlSeconds, parsedRolesMapping.keySet());
    }

    private Map<String, Set<UserRole>> parseRolesMapping(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Invalid config.rest.security.api-key.roles-mapping JSON: " + ex.getMessage(), ex);
        }
    }
}
