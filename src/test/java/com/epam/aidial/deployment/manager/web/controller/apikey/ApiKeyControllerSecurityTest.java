package com.epam.aidial.deployment.manager.web.controller.apikey;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.utils.JwtUtils;
import com.epam.aidial.deployment.manager.web.controller.ImageDefinitionController;
import com.epam.aidial.deployment.manager.web.mapper.ImageDefinitionDtoMapper;
import com.epam.aidial.deployment.manager.web.mapper.ImageDefinitionViewDtoMapper;
import com.epam.aidial.deployment.manager.web.security.SecurityPackage;
import com.epam.aidial.deployment.manager.web.security.TestSecurityConfig;
import com.epam.aidial.deployment.manager.web.security.UserRole;
import com.epam.aidial.deployment.manager.web.security.UserSecurityDetails;
import com.epam.aidial.deployment.manager.web.security.apikey.CoreApiKeyIntrospector;
import com.epam.aidial.deployment.manager.web.security.apikey.IntrospectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ImageDefinitionController.class)
@TestPropertySource(properties = {
        "config.rest.security.mode=oidc",
        "config.rest.security.default.email-claim=email",
        "config.rest.security.default.principal-claim=oid",
        "config.rest.security.default.allowedRoles=ConfigAdmin,admin",
        "config.rest.security.default.roles-mapping={\"sso-admin\":[\"FULL_ADMIN\"],\"sso-viewer\":[\"READ_ONLY_ADMIN\"]}",

        "providers.test.issuer=https://sts.windows.net/issuer_test/",
        "providers.test.jwk-set-uri=https://test/keys",
        "providers.test.audiences=audience_test",
        "providers.test.role-claims=roles, resource_access.roles",
        "providers.test.allowed-roles=testRole",
        "providers.test.email-claims=email",

        "config.rest.security.api-key.enabled=true",
        "config.rest.security.api-key.core-url=http://core",
        "config.rest.security.api-key.cache-ttl-seconds=60",
        "config.rest.security.api-key.cache-max-size=100",
        "config.rest.security.api-key.request-timeout-ms=1000",
        "config.rest.security.api-key.roles-mapping={\"admin\":[\"FULL_ADMIN\"],\"viewer\":[\"READ_ONLY_ADMIN\"]}",
        "config.rest.security.api-key.startup-probe=false",
})
@ComponentScan(basePackageClasses = {SecurityPackage.class})
@Import({JsonMapperConfiguration.class, TestSecurityConfig.class})
class ApiKeyControllerSecurityTest {

    private static final UUID TEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ENDPOINT = "/api/v1/images/definitions";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoreApiKeyIntrospector introspector;
    @MockitoBean
    private ImageDefinitionService imageDefinitionService;
    @MockitoBean
    private ImageDefinitionDtoMapper dtoMapper;
    @MockitoBean
    private ImageDefinitionViewDtoMapper viewDtoMapper;

    @BeforeEach
    void resetIntrospector() {
        reset(introspector);
    }

    @Test
    void shouldAllowListAndDelete_whenFullAdminProjectKey() throws Exception {
        when(introspector.introspect("full-admin-key"))
                .thenReturn(new IntrospectionResult("acme", null, List.of("admin"), true));

        mockMvc.perform(get(ENDPOINT).header("Api-Key", "full-admin-key"))
                .andExpect(status().is(HttpStatus.OK.value()))
                .andExpect(authenticated().withAuthentication(auth -> {
                    assertThat(auth.getName()).isEqualTo("acme");
                    assertThat(auth.getAuthorities())
                            .extracting("authority")
                            .containsExactly(UserRole.FULL_ADMIN.name());
                    assertThat(auth.getDetails()).isInstanceOfSatisfying(
                            UserSecurityDetails.class,
                            details -> assertThat(details.email()).isNull());
                }));

        mockMvc.perform(delete(ENDPOINT + "/{id}", TEST_ID).header("Api-Key", "full-admin-key"))
                .andExpect(status().is(HttpStatus.NO_CONTENT.value()));
    }

    @Test
    void shouldAllowReadButForbidDelete_whenReadOnlyAdminProjectKey() throws Exception {
        when(introspector.introspect("viewer-key"))
                .thenReturn(new IntrospectionResult("acme", null, List.of("viewer"), true));

        mockMvc.perform(get(ENDPOINT).header("Api-Key", "viewer-key"))
                .andExpect(status().is(HttpStatus.OK.value()));

        mockMvc.perform(delete(ENDPOINT + "/{id}", TEST_ID).header("Api-Key", "viewer-key"))
                .andExpect(status().is(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void shouldResolveViaDefaultRolesMapping_whenJwtRootPerRequestKey() throws Exception {
        when(introspector.introspect("per-request-key"))
                .thenReturn(new IntrospectionResult("user-123", "u@example.com", List.of("sso-admin"), false));

        mockMvc.perform(get(ENDPOINT).header("Api-Key", "per-request-key"))
                .andExpect(status().is(HttpStatus.OK.value()))
                .andExpect(authenticated().withAuthentication(auth -> {
                    assertThat(auth.getName()).isEqualTo("user-123");
                    assertThat(auth.getAuthorities())
                            .extracting("authority")
                            .containsExactly(UserRole.FULL_ADMIN.name());
                    assertThat(auth.getDetails()).isInstanceOfSatisfying(
                            UserSecurityDetails.class,
                            details -> assertThat(details.email()).isEqualTo("u@example.com"));
                }));

        mockMvc.perform(delete(ENDPOINT + "/{id}", TEST_ID).header("Api-Key", "per-request-key"))
                .andExpect(status().is(HttpStatus.NO_CONTENT.value()));
    }

    @Test
    void shouldMapReadOnlyRoleToReadOnlyAdmin_whenJwtRootPerRequestKey() throws Exception {
        when(introspector.introspect("viewer-jwt-key"))
                .thenReturn(new IntrospectionResult("user-456", "v@example.com", List.of("sso-viewer"), false));

        mockMvc.perform(get(ENDPOINT).header("Api-Key", "viewer-jwt-key"))
                .andExpect(status().is(HttpStatus.OK.value()));

        mockMvc.perform(delete(ENDPOINT + "/{id}", TEST_ID).header("Api-Key", "viewer-jwt-key"))
                .andExpect(status().is(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void shouldFailAuthorization_whenProjectKeyRoleNotInDefaultMapping() throws Exception {
        // role "admin" is mapped in api-key.roles-mapping but treated as JWT-root here.
        // It must NOT resolve via api-key.roles-mapping.
        when(introspector.introspect("misrouted"))
                .thenReturn(new IntrospectionResult("user-789", null, List.of("admin"), false));

        mockMvc.perform(get(ENDPOINT).header("Api-Key", "misrouted"))
                .andExpect(status().is(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void shouldReturnForbidden_whenRoleIsUnmapped() throws Exception {
        when(introspector.introspect("unmapped-key"))
                .thenReturn(new IntrospectionResult("acme", null, List.of("unknown"), true));

        mockMvc.perform(get(ENDPOINT).header("Api-Key", "unmapped-key"))
                .andExpect(status().is(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void shouldReturnUnauthorized_whenApiKeyIsInvalid() throws Exception {
        when(introspector.introspect(anyString()))
                .thenThrow(new BadCredentialsException("Invalid API key"));

        mockMvc.perform(get(ENDPOINT).header("Api-Key", "bogus"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void shouldServeFromCache_whenSameKeyPresentedAgain() throws Exception {
        when(introspector.introspect("repeat-key"))
                .thenReturn(new IntrospectionResult("acme", null, List.of("admin"), true));

        mockMvc.perform(get(ENDPOINT).header("Api-Key", "repeat-key"))
                .andExpect(status().is(HttpStatus.OK.value()));
        mockMvc.perform(get(ENDPOINT).header("Api-Key", "repeat-key"))
                .andExpect(status().is(HttpStatus.OK.value()));
        mockMvc.perform(get(ENDPOINT).header("Api-Key", "repeat-key"))
                .andExpect(status().is(HttpStatus.OK.value()));

        verify(introspector, times(1)).introspect("repeat-key");
    }

    @Test
    void shouldTreatBlankApiKeyHeaderAsAbsent() throws Exception {
        mockMvc.perform(get(ENDPOINT).header("Api-Key", "   "))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));

        verify(introspector, times(0)).introspect(anyString());
    }

    @Test
    void shouldNotCacheInvalidKeys() throws Exception {
        when(introspector.introspect("bad-key"))
                .thenThrow(new BadCredentialsException("Invalid API key"));

        mockMvc.perform(get(ENDPOINT).header("Api-Key", "bad-key"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
        mockMvc.perform(get(ENDPOINT).header("Api-Key", "bad-key"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
        mockMvc.perform(get(ENDPOINT).header("Api-Key", "bad-key"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));

        verify(introspector, times(3)).introspect("bad-key");
    }

    @Test
    void shouldPreferJwt_whenBothApiKeyAndJwtPresent() throws Exception {
        String jwt = JwtUtils.generateTestToken(
                "audience_test",
                "https://sts.windows.net/issuer_test/",
                Map.of(
                        "oid", "user_test",
                        "roles", "testRole",
                        "email", "test@email.com"));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .header("Api-Key", "should-be-ignored"))
                .andExpect(status().is(HttpStatus.OK.value()))
                .andExpect(authenticated()
                        .withAuthentication(auth -> assertThat(auth.getName()).isEqualTo("user_test")));

        verify(introspector, times(0)).introspect(anyString());
    }
}
