package com.epam.aidial.deployment.manager.web.security.apikey;

import com.epam.aidial.deployment.manager.web.security.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthorityResolverTest {

    @Test
    void shouldMapKnownRolesToApplicationRoles() {
        ApiKeyAuthorityResolver resolver = resolverWith("{\"admin\":[\"FULL_ADMIN\"],\"viewer\":[\"READ_ONLY_ADMIN\"]}");

        var authorities = resolver.resolve(List.of("admin"));
        assertThat(authorities)
                .extracting("authority")
                .containsExactly(UserRole.FULL_ADMIN.name());

        var readOnly = resolver.resolve(List.of("viewer"));
        assertThat(readOnly)
                .extracting("authority")
                .containsExactly(UserRole.READ_ONLY_ADMIN.name());
    }

    @Test
    void shouldReturnEmptyForUnmappedRoles() {
        ApiKeyAuthorityResolver resolver = resolverWith("{\"admin\":[\"FULL_ADMIN\"]}");
        var authorities = resolver.resolve(List.of("nothing"));
        assertThat(authorities).isEmpty();
    }

    @Test
    void shouldHandleEmptyRoleList() {
        ApiKeyAuthorityResolver resolver = resolverWith("{\"admin\":[\"FULL_ADMIN\"]}");
        assertThat(resolver.resolve(List.of())).isEmpty();
    }

    private static ApiKeyAuthorityResolver resolverWith(String rolesMappingJson) {
        ApiKeyProperties properties = new ApiKeyProperties(new ObjectMapper());
        properties.setEnabled(true);
        properties.setCoreUrl("http://core");
        properties.setRolesMapping(rolesMappingJson);
        properties.setStartupProbe(false);
        properties.validate();
        return new ApiKeyAuthorityResolver(properties);
    }
}
