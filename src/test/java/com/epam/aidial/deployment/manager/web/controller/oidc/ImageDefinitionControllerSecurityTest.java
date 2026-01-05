package com.epam.aidial.deployment.manager.web.controller.oidc;

import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.web.controller.ImageDefinitionController;
import com.epam.aidial.deployment.manager.web.mapper.ImageDefinitionDtoMapper;
import com.epam.aidial.deployment.manager.web.mapper.ImageDefinitionViewDtoMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
    @MethodSource("arguments")
    void testGetAllKeys(final String jwtToken,
                        final HttpStatus expectedStatus) throws Exception {
        final var result = performGet("/api/v1/images/definitions", jwtToken);
        result.andExpect(status().is(expectedStatus.value()));
    }
}
