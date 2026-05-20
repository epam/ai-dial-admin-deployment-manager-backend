package com.epam.aidial.deployment.manager.web.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RolesMappingResolverTest {

    private RolesMappingResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RolesMappingResolver();
    }

    @Test
    void shouldMergeDefaultAndProviderRolesMapping() {
        Map<String, Set<UserRole>> defaultMapping = Map.of(
                "ROLE_A", Set.of(UserRole.FULL_ADMIN)
        );

        Map<String, Set<UserRole>> providerMapping = Map.of(
                "ROLE_B", Set.of(UserRole.READ_ONLY_ADMIN)
        );

        Map<String, Set<UserRole>> expected = Map.of(
                "ROLE_A", Set.of(UserRole.FULL_ADMIN),
                "ROLE_B", Set.of(UserRole.READ_ONLY_ADMIN)
        );

        Map<String, Set<UserRole>> result = resolver.resolve(defaultMapping, providerMapping);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void shouldOverrideDefaultMappingWithProviderMapping() {
        Map<String, Set<UserRole>> defaultMapping = Map.of(
                "ROLE_A", Set.of(UserRole.READ_ONLY_ADMIN)
        );

        Map<String, Set<UserRole>> providerMapping = Map.of(
                "ROLE_A", Set.of(UserRole.FULL_ADMIN)
        );

        Map<String, Set<UserRole>> expected = Map.of(
                "ROLE_A", Set.of(UserRole.FULL_ADMIN)
        );

        Map<String, Set<UserRole>> result = resolver.resolve(defaultMapping, providerMapping);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void shouldReturnDefaultRolesMappingWhenProviderMappingIsAbsent() {
        Map<String, Set<UserRole>> defaultMapping = Map.of(
                "ROLE_A", Set.of(UserRole.FULL_ADMIN),
                "ROLE_B", Set.of(UserRole.READ_ONLY_ADMIN)
        );

        Map<String, Set<UserRole>> result = resolver.resolve(defaultMapping, null);

        assertThat(result).isEqualTo(defaultMapping);
    }

    @Test
    void shouldReturnEmptyMapWhenAllInputsAreNullOrEmpty() {
        Map<String, Set<UserRole>> result = resolver.resolve(null, null);

        assertThat(result).isEmpty();
    }
}
