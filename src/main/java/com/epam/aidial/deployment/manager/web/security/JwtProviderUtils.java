package com.epam.aidial.deployment.manager.web.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class JwtProviderUtils {

    private static final String V1_ISSUER_FORMAT = "https://%s/%s/";
    private static final String V2_ISSUER_FORMAT = "https://%s/%s/v2.0/";

    public Set<String> getAcceptedIssuers(JwtProvidersProperties.ProviderConfig config) {
        final Set<String> acceptedIssuers = new HashSet<>();
        var issuer = config.getIssuer();
        if (isValidUrlWithProtocol(issuer)) {
            acceptedIssuers.add(issuer);
        } else if (CollectionUtils.isNotEmpty(config.getAliases())) {
            acceptedIssuers.addAll(buildAzureIssuers(config.getAliases(), issuer));
        }
        return acceptedIssuers;
    }

    private boolean isValidUrlWithProtocol(final String urlString) {
        if (StringUtils.isBlank(urlString)) {
            return false;
        }
        try {
            final var protocol = new URL(urlString).getProtocol();
            return protocol != null && !protocol.isEmpty();
        } catch (final MalformedURLException e) {
            log.debug("Invalid format for URL: {}", urlString, e);
            return false;
        }
    }

    private Set<String> buildAzureIssuers(List<String> aliases, String issuer) {
        final Set<String> acceptedAzureIssuers = new HashSet<>();
        for (final var alias : aliases) {
            final var issuerV1Format = String.format(V1_ISSUER_FORMAT, alias, issuer);
            final var issuerV2Format = String.format(V2_ISSUER_FORMAT, alias, issuer);
            acceptedAzureIssuers.add(issuerV1Format);
            acceptedAzureIssuers.add(issuerV2Format);
        }
        return acceptedAzureIssuers;
    }
}