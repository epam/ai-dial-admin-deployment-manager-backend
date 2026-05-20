package com.epam.aidial.deployment.manager.web.controller.oidc;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.utils.JwtUtils;
import com.epam.aidial.deployment.manager.web.security.SecurityPackage;
import com.epam.aidial.deployment.manager.web.security.TestSecurityConfig;
import com.epam.aidial.deployment.manager.web.security.UserRole;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.epam.aidial.deployment.manager.web.controller.oidc.AbstractControllerSecurityTest.TEST_ISSUER;
import static com.epam.aidial.deployment.manager.web.controller.oidc.AbstractControllerSecurityTest.TEST_ISSUER_2;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@TestPropertySource(properties = {
        "config.rest.security.mode=oidc",
        "config.rest.security.default.email-claim=unique_name",
        "config.rest.security.default.principal-claim=" + AbstractControllerSecurityTest.PRINCIPAL_CLAIM,
        "config.rest.security.default.roles-mapping={\"ConfigAdmin\":[\"FULL_ADMIN\"],\"admin\":[\"FULL_ADMIN\"]}",

        "providers.test.issuer=" + TEST_ISSUER,
        "providers.test.jwk-set-uri=https://test/keys",
        "providers.test.audiences=" + AbstractControllerSecurityTest.TEST_AUDIENCE,
        "providers.test.role-claims=roles, resource_access.roles",
        "providers.test.roles-mapping={\"testRole\":[\"FULL_ADMIN\"]}",
        "providers.test.email-claims=email",

        "providers.test2.issuer=" + TEST_ISSUER_2,
        "providers.test2.jwk-set-uri=https://test/keys",
        "providers.test2.audiences=" + AbstractControllerSecurityTest.TEST_AUDIENCE,
        "providers.test2.role-claims=roles, resource_access.roles",
        "providers.test2.roles-mapping={\"testRole\":[\"READ_ONLY_ADMIN\"]}",
        "providers.test2.email-claims=email",
})
@ComponentScan(basePackageClasses = {
        SecurityPackage.class,
})
@Import({
        JsonMapperConfiguration.class,
        TestSecurityConfig.class,
})
public abstract class AbstractControllerSecurityTest {

    protected static final String TEST_AUDIENCE = "audience_test";
    protected static final String WRONG_TEST_AUDIENCE = "wrong_audience_test";

    protected static final String TEST_ISSUER = "https://sts.windows.net/issuer_test/";
    protected static final String TEST_ISSUER_2 = "https://sts.windows.net/issuer_test/2";
    protected static final String WRONG_TEST_ISSUER = "wrong_issuer_test";

    protected static final String ROLES_CLAIM = "roles";
    protected static final String EMAIL_CLAIM = "email";
    protected static final String PRINCIPAL_CLAIM = "oid";
    protected static final String PRINCIPAL_CLAIM_TEST_USER = "user_test";
    protected static final String TEST_ROLE = "testRole";
    protected static final String TEST_EMAIL = "test@email.com";
    protected static final String RESOURCE_ACCESS = "resource_access";

    @Autowired
    protected MockMvc mockMvc;

    protected static Stream<Arguments> invalidJwt() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of("invalid_jwt"),
                Arguments.of(
                        JwtUtils.generateTestToken(
                                WRONG_TEST_AUDIENCE,
                                TEST_ISSUER,
                                Map.of(
                                        PRINCIPAL_CLAIM, PRINCIPAL_CLAIM_TEST_USER,
                                        ROLES_CLAIM, "ConfigAdmin"
                                )
                        )
                ),
                Arguments.of(
                        JwtUtils.generateTestToken(
                                TEST_AUDIENCE,
                                WRONG_TEST_ISSUER,
                                Map.of(
                                        PRINCIPAL_CLAIM, PRINCIPAL_CLAIM_TEST_USER,
                                        ROLES_CLAIM, "ConfigAdmin"
                                )
                        )
                )
        );
    }

    protected static Stream<Arguments> notAllowedRoles() {
        return Stream.of(
                Arguments.of(
                        JwtUtils.generateTestToken(
                                TEST_AUDIENCE,
                                TEST_ISSUER,
                                Map.of(
                                        PRINCIPAL_CLAIM, PRINCIPAL_CLAIM_TEST_USER,
                                        ROLES_CLAIM, "ConfigAdmin1"
                                )
                        )
                ),
                Arguments.of(
                        JwtUtils.generateTestToken(
                                TEST_AUDIENCE,
                                TEST_ISSUER,
                                Map.of(
                                        PRINCIPAL_CLAIM, PRINCIPAL_CLAIM_TEST_USER,
                                        RESOURCE_ACCESS, TEST_ROLE
                                )
                        )
                )
        );
    }

    protected static Stream<Arguments> fullAdminRoles() {
        return Stream.of(
                Arguments.of(
                        JwtUtils.generateTestToken(
                                TEST_AUDIENCE,
                                TEST_ISSUER,
                                Map.of(
                                        PRINCIPAL_CLAIM, PRINCIPAL_CLAIM_TEST_USER,
                                        RESOURCE_ACCESS, Map.of(ROLES_CLAIM, TEST_ROLE),
                                        EMAIL_CLAIM, TEST_EMAIL
                                )
                        ),
                        PRINCIPAL_CLAIM_TEST_USER,
                        TEST_EMAIL,
                        List.of(new SimpleGrantedAuthority(UserRole.FULL_ADMIN.name()))
                ),
                Arguments.of(
                        JwtUtils.generateTestToken(
                                TEST_AUDIENCE,
                                TEST_ISSUER,
                                Map.of(
                                        PRINCIPAL_CLAIM, PRINCIPAL_CLAIM_TEST_USER,
                                        ROLES_CLAIM, TEST_ROLE,
                                        RESOURCE_ACCESS, TEST_ROLE)
                        ),
                        PRINCIPAL_CLAIM_TEST_USER,
                        null,
                        List.of(new SimpleGrantedAuthority(UserRole.FULL_ADMIN.name()))
                ),
                Arguments.of(
                        JwtUtils.generateTestToken(
                                TEST_AUDIENCE,
                                TEST_ISSUER,
                                Map.of(
                                        PRINCIPAL_CLAIM, PRINCIPAL_CLAIM_TEST_USER,
                                        ROLES_CLAIM, TEST_ROLE,
                                        "unique_name", TEST_EMAIL
                                )
                        ),
                        PRINCIPAL_CLAIM_TEST_USER,
                        null,
                        List.of(new SimpleGrantedAuthority(UserRole.FULL_ADMIN.name()))
                )
        );
    }

    protected static Stream<Arguments> readOnlyAdminRoles() {
        return Stream.of(
                Arguments.of(
                        JwtUtils.generateTestToken(
                                TEST_AUDIENCE,
                                TEST_ISSUER_2,
                                Map.of(
                                        PRINCIPAL_CLAIM, PRINCIPAL_CLAIM_TEST_USER,
                                        RESOURCE_ACCESS, Map.of(ROLES_CLAIM, TEST_ROLE),
                                        EMAIL_CLAIM, TEST_EMAIL
                                )
                        ),
                        PRINCIPAL_CLAIM_TEST_USER,
                        TEST_EMAIL,
                        List.of(new SimpleGrantedAuthority(UserRole.READ_ONLY_ADMIN.name()))
                ),
                Arguments.of(
                        JwtUtils.generateTestToken(
                                TEST_AUDIENCE,
                                TEST_ISSUER_2,
                                Map.of(
                                        PRINCIPAL_CLAIM, PRINCIPAL_CLAIM_TEST_USER,
                                        ROLES_CLAIM, TEST_ROLE,
                                        RESOURCE_ACCESS, TEST_ROLE)
                        ),
                        PRINCIPAL_CLAIM_TEST_USER,
                        null,
                        List.of(new SimpleGrantedAuthority(UserRole.READ_ONLY_ADMIN.name()))
                ),
                Arguments.of(
                        JwtUtils.generateTestToken(
                                TEST_AUDIENCE,
                                TEST_ISSUER_2,
                                Map.of(
                                        PRINCIPAL_CLAIM, PRINCIPAL_CLAIM_TEST_USER,
                                        ROLES_CLAIM, TEST_ROLE,
                                        "unique_name", TEST_EMAIL
                                )
                        ),
                        PRINCIPAL_CLAIM_TEST_USER,
                        null,
                        List.of(new SimpleGrantedAuthority(UserRole.READ_ONLY_ADMIN.name()))
                )
        );
    }

    protected ResultActions performGet(final String url,
                                       final String jwtToken,
                                       final Object... uriVariables) throws Exception {
        return mockMvc.perform(get(url, uriVariables)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .header(HttpHeaders.IF_NONE_MATCH, "*"));
    }

    protected ResultActions performDelete(final String url, final String jwtToken, final Object... uriVariables) throws Exception {
        return mockMvc.perform(delete(url, uriVariables)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken));
    }

}