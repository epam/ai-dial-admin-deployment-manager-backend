package com.epam.aidial.deployment.manager.web.security.apikey;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@LogExecution
@ConditionalOnProperty(value = "config.rest.security.api-key.enabled", havingValue = "true")
public class CoreApiKeyIntrospector {

    public static final String API_KEY_HEADER = "Api-Key";
    private static final String USER_INFO_PATH = "/v1/user/info";
    private static final ParameterizedTypeReference<Map<String, Object>> STRING_OBJECT_MAP =
            new ParameterizedTypeReference<>() { };

    private final RestTemplate restTemplate;
    private final ApiKeyProperties properties;
    private final String userInfoUrl;
    private final String defaultPrincipalClaim;
    private final String defaultEmailClaim;

    public CoreApiKeyIntrospector(RestTemplateBuilder builder,
                                  ApiKeyProperties properties,
                                  @Value("${config.rest.security.default.principal-claim:sub}") String defaultPrincipalClaim,
                                  @Value("${config.rest.security.default.email-claim:email}") String defaultEmailClaim) {
        this.properties = properties;
        this.userInfoUrl = StringUtils.removeEnd(properties.getCoreUrl(), "/") + USER_INFO_PATH;
        this.defaultPrincipalClaim = defaultPrincipalClaim;
        this.defaultEmailClaim = defaultEmailClaim;
        Duration timeout = Duration.ofMillis(properties.getRequestTimeoutMs());
        this.restTemplate = builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .additionalCustomizers(rt -> rt.getMessageConverters().stream()
                        .filter(JacksonJsonHttpMessageConverter.class::isInstance)
                        .map(JacksonJsonHttpMessageConverter.class::cast)
                        .findFirst()
                        .ifPresent(c -> {
                            // DIAL Core's /v1/user/info returns JSON body but labels it application/octet-stream.
                            List<MediaType> types = new ArrayList<>(c.getSupportedMediaTypes());
                            types.add(MediaType.APPLICATION_OCTET_STREAM);
                            c.setSupportedMediaTypes(types);
                        }))
                .build();
    }

    @PostConstruct
    public void probeCore() {
        if (!properties.isStartupProbe()) {
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(API_KEY_HEADER, "dm-startup-probe");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        RequestEntity<Void> request = RequestEntity.get(userInfoUrl)
                .headers(headers)
                .build();
        try {
            restTemplate.exchange(request, STRING_OBJECT_MAP);
            log.info("DIAL Core /v1/user/info reachable at {}", userInfoUrl);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                log.info("DIAL Core /v1/user/info reachable at {} (responded {})",
                        userInfoUrl, ex.getStatusCode());
            } else {
                throw new IllegalStateException(
                        "DIAL Core /v1/user/info responded with " + ex.getStatusCode() + " at "
                                + userInfoUrl
                                + ". Disable startup probe via API_KEY_STARTUP_PROBE=false to skip this check.",
                        ex);
            }
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException(
                    "DIAL Core /v1/user/info is unreachable at " + userInfoUrl
                            + ". Disable startup probe via API_KEY_STARTUP_PROBE=false to skip this check.",
                    ex);
        }
    }

    RestTemplate getRestTemplate() {
        return restTemplate;
    }

    public IntrospectionResult introspect(String apiKey) {
        Map<String, Object> response = callCore(apiKey);
        List<String> rawRoles = extractRoles(response.get("roles"));

        if (response.get("project") instanceof String project && StringUtils.isNotBlank(project)) {
            return new IntrospectionResult(project, null, rawRoles, true);
        }

        if (response.get("userClaims") instanceof Map<?, ?> userClaimsRaw && !userClaimsRaw.isEmpty()) {
            Map<String, List<String>> userClaims = normalizeUserClaims(userClaimsRaw);
            String principal = firstNonBlank(userClaims.get(defaultPrincipalClaim));
            if (StringUtils.isBlank(principal)) {
                log.warn("Core /v1/user/info userClaims response is missing the configured principal claim '{}'",
                        defaultPrincipalClaim);
                throw new BadCredentialsException("Malformed Core user-info response");
            }
            String email = firstNonBlank(userClaims.get(defaultEmailClaim));
            return new IntrospectionResult(principal, email, rawRoles, false);
        }

        log.warn("Core /v1/user/info response contains neither 'project' nor 'userClaims'");
        throw new BadCredentialsException("Malformed Core user-info response");
    }

    private Map<String, Object> callCore(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(API_KEY_HEADER, apiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        RequestEntity<Void> request = RequestEntity.get(userInfoUrl)
                .headers(headers)
                .build();

        ResponseEntity<Map<String, Object>> response;
        try {
            response = restTemplate.exchange(request, STRING_OBJECT_MAP);
        } catch (RestClientResponseException ex) {
            log.debug("Core /v1/user/info rejected API key with status {}", ex.getStatusCode());
            throw new BadCredentialsException("Invalid API key");
        } catch (ResourceAccessException ex) {
            log.warn("Failed to reach Core /v1/user/info at {}", userInfoUrl, ex);
            throw new AuthenticationServiceException("Failed to validate API key with DIAL Core", ex);
        }
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            log.debug("Core /v1/user/info responded with {}", response.getStatusCode());
            throw new BadCredentialsException("Invalid API key");
        }
        return response.getBody();
    }

    private List<String> extractRoles(Object rolesClaim) {
        if (rolesClaim instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    private static Map<String, List<String>> normalizeUserClaims(Map<?, ?> raw) {
        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String name)) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof List<?> list) {
                List<String> strings = list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
                result.put(name, strings);
            } else if (value instanceof String s) {
                result.put(name, List.of(s));
            }
        }
        return result;
    }

    private static String firstNonBlank(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }
}
