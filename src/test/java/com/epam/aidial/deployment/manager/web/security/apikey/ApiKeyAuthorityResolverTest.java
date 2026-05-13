package com.epam.aidial.deployment.manager.web.security.apikey;

import com.epam.aidial.deployment.manager.web.security.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthorityResolverTest {

    @Test
    void shouldMapProjectKeyRolesViaApiKeyMapping() {
        ApiKeyAuthorityResolver resolver = resolverWith(
                "{\"admin\":[\"FULL_ADMIN\"],\"viewer\":[\"READ_ONLY_ADMIN\"]}",
                "");

        assertThat(resolver.resolve(List.of("admin"), true))
                .extracting("authority")
                .containsExactly(UserRole.FULL_ADMIN.name());

        assertThat(resolver.resolve(List.of("viewer"), true))
                .extracting("authority")
                .containsExactly(UserRole.READ_ONLY_ADMIN.name());
    }

    @Test
    void shouldMapJwtRootRolesViaDefaultMapping() {
        ApiKeyAuthorityResolver resolver = resolverWith(
                "{\"admin\":[\"FULL_ADMIN\"]}",
                "{\"sso-admin\":[\"FULL_ADMIN\"],\"sso-viewer\":[\"READ_ONLY_ADMIN\"]}");

        assertThat(resolver.resolve(List.of("sso-admin"), false))
                .extracting("authority")
                .containsExactly(UserRole.FULL_ADMIN.name());

        assertThat(resolver.resolve(List.of("sso-viewer"), false))
                .extracting("authority")
                .containsExactly(UserRole.READ_ONLY_ADMIN.name());
    }

    @Test
    void shouldNotCrossOverMappings() {
        ApiKeyAuthorityResolver resolver = resolverWith(
                "{\"admin\":[\"FULL_ADMIN\"]}",
                "{\"sso-admin\":[\"FULL_ADMIN\"]}");

        // Project-key role names must not resolve via the default mapping.
        assertThat(resolver.resolve(List.of("admin"), false)).isEmpty();
        // Default/JWT role names must not resolve via the api-key mapping.
        assertThat(resolver.resolve(List.of("sso-admin"), true)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnmappedRoles() {
        ApiKeyAuthorityResolver resolver = resolverWith("{\"admin\":[\"FULL_ADMIN\"]}", "");
        assertThat(resolver.resolve(List.of("nothing"), true)).isEmpty();
    }

    @Test
    void shouldHandleEmptyRoleList() {
        ApiKeyAuthorityResolver resolver = resolverWith("{\"admin\":[\"FULL_ADMIN\"]}", "");
        assertThat(resolver.resolve(List.of(), true)).isEmpty();
        assertThat(resolver.resolve(List.of(), false)).isEmpty();
    }

    private static ApiKeyAuthorityResolver resolverWith(String apiKeyMappingJson, String defaultMappingJson) {
        ApiKeyProperties properties = new ApiKeyProperties(new ObjectMapper(), defaultMappingJson);
        properties.setEnabled(true);
        properties.setCoreUrl("http://core");
        properties.setRolesMapping(apiKeyMappingJson);
        properties.setStartupProbe(false);
        properties.validate();
        return new ApiKeyAuthorityResolver(properties);
    }
}
