package com.epam.aidial.deployment.manager.web.security;

import com.epam.aidial.deployment.manager.utils.JwtProviderTestHelper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtProvidersPropertiesTest {

    private static final String TEST_PROVIDER = "test";

    @Test
    void whenNoProviders_thenThrows() {
        var properties = new JwtProvidersProperties();
        properties.getProviders().clear();
        assertThrows(IllegalStateException.class, properties::checkProviders);
    }

    @Test
    void whenProvidersPresentAndIssuerIsBlank_thenThrows() {
        var properties = new JwtProvidersProperties();
        var config = JwtProviderTestHelper.createProviderConfig();
        config.setIssuer("");
        properties.getProviders().put(TEST_PROVIDER, config);
        assertThrows(IllegalStateException.class, properties::checkProviders);
    }

    @Test
    void whenProvidersPresentAndUriIsBlank_thenThrows() {
        var properties = new JwtProvidersProperties();
        var config = JwtProviderTestHelper.createProviderConfig();
        config.setJwkSetUri("");
        properties.getProviders().put(TEST_PROVIDER, config);
        assertThrows(IllegalStateException.class, properties::checkProviders);
    }

    @Test
    void whenProvidersPresentAndRoleClaimsIsBlankAndAudiencesEmpty_thenThrows() {
        var properties = new JwtProvidersProperties();
        var config = JwtProviderTestHelper.createProviderConfig();
        config.setRoleClaims(List.of(""));
        config.setAudiences(new ArrayList<>());
        properties.getProviders().put(TEST_PROVIDER, config);
        assertThrows(IllegalStateException.class, properties::checkProviders);
    }

    @Test
    void whenProvidersPresentAndUriIsNull_thenNoException() {
        var properties = new JwtProvidersProperties();
        properties.getProviders().put(TEST_PROVIDER, JwtProviderTestHelper.createProviderConfig());
        assertDoesNotThrow(properties::checkProviders);
    }
}