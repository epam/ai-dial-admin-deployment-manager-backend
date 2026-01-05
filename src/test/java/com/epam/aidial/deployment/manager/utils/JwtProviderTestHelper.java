package com.epam.aidial.deployment.manager.utils;

import com.epam.aidial.deployment.manager.web.security.JwtProvidersProperties;

import java.util.List;

public class JwtProviderTestHelper {

    public static JwtProvidersProperties.ProviderConfig createProviderConfig() {
        var config = new JwtProvidersProperties.ProviderConfig();
        config.setIssuer("https://sts.windows.net/issuer_test/");
        config.setJwkSetUri("https://test/keys");
        config.setAudiences(List.of("audience_test"));
        config.setRoleClaims("roles");
        config.setPrincipalClaim("oid");
        return config;
    }
}