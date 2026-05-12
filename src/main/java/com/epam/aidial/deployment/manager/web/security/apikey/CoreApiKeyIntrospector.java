package com.epam.aidial.deployment.manager.web.security.apikey;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;

@Slf4j
@Component
@LogExecution
@ConditionalOnProperty(value = "config.rest.security.api-key.enabled", havingValue = "true")
public class CoreApiKeyIntrospector {

    public static final String API_KEY_HEADER = "Api-Key";
    private static final ParameterizedTypeReference<Map<String, Object>> STRING_OBJECT_MAP =
            new ParameterizedTypeReference<>() { };

    private final RestTemplate restTemplate;
    private final ApiKeyProperties properties;

    public CoreApiKeyIntrospector(RestTemplateBuilder builder, ApiKeyProperties properties) {
        this.properties = properties;
        Duration timeout = Duration.ofMillis(properties.getRequestTimeoutMs());
        this.restTemplate = builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }

    @PostConstruct
    public void probeCore() {
        if (!properties.isStartupProbe()) {
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(API_KEY_HEADER, "dm-startup-probe");
        RequestEntity<Void> request = RequestEntity.get(properties.getCoreUserInfoUrl())
                .headers(headers)
                .build();
        try {
            restTemplate.exchange(request, STRING_OBJECT_MAP);
            log.info("DIAL Core /v1/user/info reachable at {}", properties.getCoreUserInfoUrl());
        } catch (RestClientResponseException ex) {
            log.info("DIAL Core /v1/user/info reachable at {} (responded {})",
                    properties.getCoreUserInfoUrl(), ex.getStatusCode());
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "DIAL Core /v1/user/info is unreachable at " + properties.getCoreUserInfoUrl()
                            + ". Disable startup probe via API_KEY_STARTUP_PROBE=false to skip this check.",
                    ex);
        }
    }

    RestTemplate getRestTemplate() {
        return restTemplate;
    }

    public IntrospectionResult introspect(String apiKey) {
        Map<String, Object> response = callCore(apiKey);

        Object projectClaim = response.get("project");
        if (!(projectClaim instanceof String project) || StringUtils.isBlank(project)) {
            log.warn("Core /v1/user/info response is missing the 'project' field");
            throw new BadCredentialsException("Malformed Core user-info response");
        }

        List<String> rawRoles = extractRoles(response.get("roles"));
        return new IntrospectionResult(project, rawRoles);
    }

    private Map<String, Object> callCore(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(API_KEY_HEADER, apiKey);

        RequestEntity<Void> request = RequestEntity.get(properties.getCoreUserInfoUrl())
                .headers(headers)
                .build();

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(request, STRING_OBJECT_MAP);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.debug("Core /v1/user/info responded with {}", response.getStatusCode());
                throw new BadCredentialsException("Invalid API key");
            }
            return response.getBody();
        } catch (RestClientResponseException ex) {
            log.debug("Core /v1/user/info rejected API key with status {}", ex.getStatusCode());
            throw new BadCredentialsException("Invalid API key");
        } catch (Exception ex) {
            log.warn("Failed to reach Core /v1/user/info at {}", properties.getCoreUserInfoUrl(), ex);
            throw new AuthenticationServiceException("Failed to validate API key with DIAL Core", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Object rolesClaim) {
        if (rolesClaim instanceof List<?> list) {
            return ListUtils.emptyIfNull((List<String>) list);
        }
        return List.of();
    }
}
