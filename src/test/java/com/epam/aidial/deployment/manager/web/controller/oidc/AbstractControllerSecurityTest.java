package com.epam.aidial.deployment.manager.web.controller.oidc;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.utils.JwtUtils;
import com.epam.aidial.deployment.manager.utils.TestAuthenticationConverterFactory;
import com.epam.aidial.deployment.manager.utils.TestIdentityProviderConfig;
import com.epam.aidial.deployment.manager.utils.TestTokenDecoderFactory;
import com.epam.aidial.deployment.manager.web.security.SecurityPackage;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@TestPropertySource(properties = {
        "config.rest.security.mode=oidc",
        "config.rest.security.default.email-claim=unique_name",
        "config.rest.security.default.principal-claim=" + AbstractControllerSecurityTest.PRINCIPAL_CLAIM,
        "config.rest.security.default.allowedRoles=ConfigAdmin,admin"
})
@ComponentScan(basePackageClasses = {
        SecurityPackage.class,
})
@Import({
        JsonMapperConfiguration.class,
        TestTokenDecoderFactory.class,
        TestAuthenticationConverterFactory.class,
        TestIdentityProviderConfig.class
})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
public abstract class AbstractControllerSecurityTest {

    protected static final String TEST_AUDIENCE = "audience_test";
    protected static final String WRONG_TEST_AUDIENCE = "wrong_audience_test";

    protected static final String TEST_ISSUER = "https://sts.windows.net/issuer_test/";
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

    protected static Stream<Arguments> unauthorizedArguments() {
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

    protected static Stream<Arguments> forbiddenArguments() {
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

    protected static Stream<Arguments> okArguments() {
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
                    List.of(new SimpleGrantedAuthority(TEST_ROLE))
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
                    List.of(new SimpleGrantedAuthority(TEST_ROLE))
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
                    List.of(new SimpleGrantedAuthority(TEST_ROLE))
                )
        );
    }

    protected ResultActions performGet(final String url,
                                       final String jwtToken,
                                       final Object... uriVariables) throws Exception {
        return mockMvc.perform(get(url, uriVariables)
                .header("Authorization", "Bearer " + jwtToken));
    }
}