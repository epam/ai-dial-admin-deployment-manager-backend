package com.epam.aidial.deployment.manager.web.security;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;

@Data
@Slf4j
@Component
@ConfigurationProperties
@ConditionalOnProperty(value = "config.rest.security.mode", havingValue = "oidc", matchIfMissing = true)
public class JwtProvidersProperties {

    private final Map<String, ProviderConfig> providers = new HashMap<>();

    @Data
    public static class ProviderConfig {
        private String issuer;
        private String jwkSetUri;
        private List<String> audiences;
        private List<String> aliases;
        private List<String> roleClaims;
        private Set<String> allowedRoles;
        private String principalClaim;
    }

    @PostConstruct
    public void checkProviders() {
        Iterator<Map.Entry<String, ProviderConfig>> iterator = providers.entrySet().iterator();
        while (iterator.hasNext()) {
            var next = iterator.next();
            var name = next.getKey();
            var provider = next.getValue();
            log.trace("Validating provider: '{}', issuer: '{}', uri: '{}'", name, provider.issuer, provider.jwkSetUri);
            var missingFields = getMissingFields(provider);
            if (CollectionUtils.isNotEmpty(missingFields)) {
                log.warn("Skipping provider '{}' - missing: {}", name, String.join(", ", missingFields));
                iterator.remove();
            }
        }
        if (providers.isEmpty()) {
            throw new IllegalStateException("No identity providers configured. Application cannot start.");
        }
        log.info("Loaded {} provider configurations", providers.size());
    }

    private List<String> getMissingFields(ProviderConfig provider) {
        List<String> missingFields = new ArrayList<>();
        if (StringUtils.isBlank(provider.getIssuer())) {
            missingFields.add("issuer");
        }
        if (StringUtils.isBlank(provider.getJwkSetUri())) {
            missingFields.add("jwkSetUri");
        }
        if (CollectionUtils.isEmpty(provider.getRoleClaims())) {
            missingFields.add("roleClaims");
        }
        if (CollectionUtils.isEmpty(provider.getAudiences())) {
            missingFields.add("audiences");
        }
        return missingFields;
    }
}