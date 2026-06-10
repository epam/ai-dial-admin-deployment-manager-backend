package com.epam.aidial.deployment.manager.web.security;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.utils.IdentityProviderTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityProvidersPropertiesTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapperConfiguration.createJsonMapper();

    private IdentityProvidersProperties properties;

    @BeforeEach
    void setUp() {
        properties = new IdentityProvidersProperties(OBJECT_MAPPER);
    }

    @Test
    void whenNoProviders_thenThrows() {
        properties.getProviders().clear();
        assertThatThrownBy(properties::checkProviders).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void whenProvidersPresentAndIssuerIsBlankForJwtProvider_thenThrows() {
        IdentityProvidersProperties.ProviderConfig config = IdentityProviderTestHelper.createJwtProviderConfig();
        config.setIssuer("");
        properties.getProviders().put("test", config);
        assertThatThrownBy(properties::checkProviders).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void whenProvidersPresentAndJwkSetUriAndUserInfoEndpointAreBlank_thenThrows() {
        IdentityProvidersProperties.ProviderConfig config = IdentityProviderTestHelper.createJwtProviderConfig();
        config.setJwkSetUri("");
        properties.getProviders().put("test", config);
        assertThatThrownBy(properties::checkProviders).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void whenProvidersPresentAndJwkSetUriAndUserInfoEndpointAreNotBlank_thenThrows() {
        IdentityProvidersProperties.ProviderConfig config = IdentityProviderTestHelper.createJwtProviderConfig();
        config.setUserInfoEndpoint("https://test/userinfo");
        properties.getProviders().put("test", config);
        assertThatThrownBy(properties::checkProviders).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void whenProvidersPresentAndJwkSetUriAndIssuerAreNotBlank_thenNoException() {
        properties.getProviders().put("test", IdentityProviderTestHelper.createJwtProviderConfig());
        assertThatCode(properties::checkProviders).doesNotThrowAnyException();
    }

    @Test
    void whenProvidersPresentAndUserInfoEndpointIsNotBlank_thenNoException() {
        properties.getProviders().put("test", IdentityProviderTestHelper.createOpaqueTokenProviderConfig());
        assertThatCode(properties::checkProviders).doesNotThrowAnyException();
    }

    @Test
    void whenProvidersPresentAndRoleClaimsAreEmpty_thenThrows() {
        IdentityProvidersProperties.ProviderConfig config = IdentityProviderTestHelper.createJwtProviderConfig();
        config.setRoleClaims(null);
        properties.getProviders().put("test", config);
        assertThatThrownBy(properties::checkProviders).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void whenProvidersPresentAndRolesMappingIsInvalidJson_thenThrows() {
        IdentityProvidersProperties.ProviderConfig config = IdentityProviderTestHelper.createJwtProviderConfig();
        config.setRolesMapping("{");
        properties.getProviders().put("test", config);
        assertThatThrownBy(properties::checkProviders).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void whenProvidersPresentAndRolesMappingContainsUnknownUserRole_thenThrows() {
        IdentityProvidersProperties.ProviderConfig config = IdentityProviderTestHelper.createJwtProviderConfig();
        config.setRolesMapping("{\"role1\":[\"UNKNOWN_ROLE\"]}");
        properties.getProviders().put("test", config);
        assertThatThrownBy(properties::checkProviders).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void whenProvidersPresentAndRolesMappingIsValid_thenNoException() {
        IdentityProvidersProperties.ProviderConfig config = IdentityProviderTestHelper.createJwtProviderConfig();
        config.setRolesMapping("{\"role1\":[\"FULL_ADMIN\"],\"role2\":[\"READ_ONLY_ADMIN\"]}");
        properties.getProviders().put("test", config);
        assertThatCode(properties::checkProviders).doesNotThrowAnyException();
    }
}
