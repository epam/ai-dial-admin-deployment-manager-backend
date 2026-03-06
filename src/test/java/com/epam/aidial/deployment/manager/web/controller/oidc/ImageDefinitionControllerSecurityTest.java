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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ImageDefinitionController.class)
class ImageDefinitionControllerSecurityTest extends AbstractControllerSecurityTest {

    @MockitoBean
    private ImageDefinitionService imageDefinitionService;
    @MockitoBean
    private ImageDefinitionDtoMapper dtoMapper;
    @MockitoBean
    private ImageDefinitionViewDtoMapper viewDtoMapper;

    @ParameterizedTest
    @MethodSource("unauthorizedArguments")
    void testGetAllImageDefinitionsShouldReturnUnauthorized(String jwtToken) throws Exception {
        // Given & When
        final var result = performGet("/api/v1/images/definitions", jwtToken);

        // Then
        result.andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    @ParameterizedTest
    @MethodSource("forbiddenArguments")
    void testGetAllImageDefinitionsShouldReturnForbidden(String jwtToken) throws Exception {
        // Given & When
        final var result = performGet("/api/v1/images/definitions", jwtToken);

        // Then
        result.andExpect(status().is(HttpStatus.FORBIDDEN.value()));
    }

    @ParameterizedTest
    @MethodSource("okArguments")
    void testGetAllImageDefinitionsShouldReturnOk(String jwtToken,
                                                  String expectedName,
                                                  String expectedEmail,
                                                  List<GrantedAuthority> expectedGrantedAuthorities) throws Exception {
        // Given & When
        final var result = performGet("/api/v1/images/definitions", jwtToken);

        // Then
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
}
