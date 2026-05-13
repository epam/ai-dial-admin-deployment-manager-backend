package com.epam.aidial.deployment.manager.web.security.apikey;

import com.epam.aidial.deployment.manager.web.security.UserRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
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
@ConfigurationProperties(prefix = "config.rest.security.api-key")
@ConditionalOnProperty(value = "config.rest.security.api-key.enabled", havingValue = "true")
public class ApiKeyProperties {

    private final ObjectMapper objectMapper;
    private final String defaultRolesMappingJson;

    private boolean enabled;
    private String coreUrl;
    private int cacheTtlSeconds;
    private int cacheMaxSize;
    private int requestTimeoutMs;
    private String rolesMapping;
    private boolean startupProbe;

    private Map<String, Set<UserRole>> parsedRolesMapping = Collections.emptyMap();
    private Map<String, Set<UserRole>> parsedDefaultRolesMapping = Collections.emptyMap();

    public ApiKeyProperties(ObjectMapper objectMapper,
                            @Value("${config.rest.security.default.roles-mapping:}") String defaultRolesMappingJson) {
        this.objectMapper = objectMapper;
        this.defaultRolesMappingJson = defaultRolesMappingJson;
    }

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

        parsedRolesMapping = parseRolesMapping(rolesMapping, "config.rest.security.api-key.roles-mapping");
        parsedDefaultRolesMapping = parseRolesMapping(defaultRolesMappingJson, "config.rest.security.default.roles-mapping");
        if (parsedRolesMapping.isEmpty() && parsedDefaultRolesMapping.isEmpty()) {
            throw new IllegalStateException(
                    "config.rest.security.api-key.enabled=true requires at least one of "
                            + "config.rest.security.api-key.roles-mapping (used for project-key callers) or "
                            + "config.rest.security.default.roles-mapping (used for JWT-rooted per-request keys) "
                            + "to map at least one role to a DM authority (FULL_ADMIN / READ_ONLY_ADMIN). "
                            + "Otherwise every authenticated API-key caller would be rejected with 403.");
        }

        log.info("API-key authentication is enabled. Core URL: {}, cache TTL: {}s, "
                        + "project-key mapped roles: {}, default mapped roles (JWT-root path): {}",
                coreUrl, cacheTtlSeconds, parsedRolesMapping.keySet(), parsedDefaultRolesMapping.keySet());
    }

    private Map<String, Set<UserRole>> parseRolesMapping(String json, String propertyName) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid " + propertyName + " JSON: " + ex.getMessage(), ex);
        }
    }
}
