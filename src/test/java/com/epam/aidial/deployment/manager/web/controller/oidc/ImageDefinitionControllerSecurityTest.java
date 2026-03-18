package com.epam.aidial.deployment.manager.web.controller.oidc;

import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.web.controller.ImageDefinitionController;
import com.epam.aidial.deployment.manager.web.mapper.ImageDefinitionDtoMapper;
import com.epam.aidial.deployment.manager.web.mapper.ImageDefinitionViewDtoMapper;
import com.epam.aidial.deployment.manager.web.security.UserSecurityDetails;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ImageDefinitionController.class)
class ImageDefinitionControllerSecurityTest extends AbstractControllerSecurityTest {

    private static final UUID TEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @MockitoBean
    private ImageDefinitionService imageDefinitionService;
    @MockitoBean
    private ImageDefinitionDtoMapper dtoMapper;
    @MockitoBean
    private ImageDefinitionViewDtoMapper viewDtoMapper;

    // GET /api/v1/images/definitions

    @ParameterizedTest
    @MethodSource("invalidJwt")
    void testGetAllImageDefinitionsShouldReturnUnauthorized(String jwtToken) throws Exception {
        final var result = performGet("/api/v1/images/definitions", jwtToken);
        result.andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    @ParameterizedTest
    @MethodSource("notAllowedRoles")
    void testGetAllImageDefinitionsShouldReturnForbidden(String jwtToken) throws Exception {
        final var result = performGet("/api/v1/images/definitions", jwtToken);
        result.andExpect(status().is(HttpStatus.FORBIDDEN.value()));
    }

    @ParameterizedTest
    @MethodSource({"fullAdminRoles", "readOnlyAdminRoles"})
    void testGetAllImageDefinitionsShouldReturnOk(String jwtToken,
                                                  String expectedName,
                                                  String expectedEmail,
                                                  List<GrantedAuthority> expectedGrantedAuthorities) throws Exception {
        final var result = performGet("/api/v1/images/definitions", jwtToken);

        result
                .andExpect(status().is(HttpStatus.OK.value()))
                .andExpect(authenticated().withAuthentication(auth -> {
                    assertThat(auth).isNotNull();
                    assertThat(auth.getName()).isEqualTo(expectedName);
                    assertThat(auth.getAuthorities()).isEqualTo(expectedGrantedAuthorities);
                    assertThat(auth.getDetails()).isInstanceOfSatisfying(
                            UserSecurityDetails.class,
                            userSecurityDetails -> assertThat(userSecurityDetails.email()).isEqualTo(expectedEmail));
                }));
    }

    // DELETE /api/v1/images/definitions/{id}

    @ParameterizedTest
    @MethodSource("invalidJwt")
    void testDeleteImageDefinitionShouldReturnUnauthorized(String jwtToken) throws Exception {
        final var result = performDelete("/api/v1/images/definitions/{id}", jwtToken, TEST_ID);
        result.andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    @ParameterizedTest
    @MethodSource({"notAllowedRoles", "readOnlyAdminRoles"})
    void testDeleteImageDefinitionShouldReturnForbidden(String jwtToken) throws Exception {
        final var result = performDelete("/api/v1/images/definitions/{id}", jwtToken, TEST_ID);
        result.andExpect(status().is(HttpStatus.FORBIDDEN.value()));
    }

    @ParameterizedTest
    @MethodSource("fullAdminRoles")
    void testDeleteImageDefinitionShouldReturnNoContent(String jwtToken,
                                                        String expectedName,
                                                        String expectedEmail,
                                                        List<GrantedAuthority> expectedGrantedAuthorities) throws Exception {
        final var result = performDelete("/api/v1/images/definitions/{id}", jwtToken, TEST_ID);

        result
                .andExpect(status().is(HttpStatus.NO_CONTENT.value()))
                .andExpect(authenticated().withAuthentication(auth -> {
                    assertThat(auth).isNotNull();
                    assertThat(auth.getName()).isEqualTo(expectedName);
                    assertThat(auth.getAuthorities()).isEqualTo(expectedGrantedAuthorities);
                    assertThat(auth.getDetails()).isInstanceOfSatisfying(
                            UserSecurityDetails.class,
                            userSecurityDetails -> assertThat(userSecurityDetails.email()).isEqualTo(expectedEmail));
                }));
    }
}
