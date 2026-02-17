package com.epam.aidial.deployment.manager.web.security;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class JwtAuthenticationConverterFactory {

    private final JwtProviderUtils jwtProviderUtils;
    private final Map<String, JwtAuthenticationConverter> convertersByIssuer;

    public JwtAuthenticationConverterFactory(Map<String, JwtProvidersProperties.ProviderConfig> providers,
                                             JwtProviderUtils jwtProviderUtils) {
        this.jwtProviderUtils = jwtProviderUtils;

        Map<String, JwtAuthenticationConverter> tmpConvertersByIssuer = new HashMap<>();
        providers.forEach((name, config) -> {
            var converter = create(config);
            var acceptedIssuers = this.jwtProviderUtils.getAcceptedIssuers(config);
            for (var issuer : acceptedIssuers) {
                tmpConvertersByIssuer.put(issuer, converter);
            }
        });
        convertersByIssuer = Map.copyOf(tmpConvertersByIssuer);
    }

    private JwtAuthenticationConverter create(JwtProvidersProperties.ProviderConfig config) {
        var grantedAuthoritiesConverter = new MultiPathGrantedAuthoritiesConverter();
        var authoritiesPaths = Optional.ofNullable(config.getRoleClaims())
                .orElse(Collections.emptyList())
                .stream()
                .map(String::trim)
                .toList();
        grantedAuthoritiesConverter.setAuthoritiesPaths(authoritiesPaths);
        grantedAuthoritiesConverter.setAuthorityPrefix("");

        final var jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        jwtAuthenticationConverter.setPrincipalClaimName(config.getPrincipalClaim());

        return jwtAuthenticationConverter;
    }

    public JwtAuthenticationConverter getConverter(String issuer) {
        return convertersByIssuer.get(issuer);
    }
}