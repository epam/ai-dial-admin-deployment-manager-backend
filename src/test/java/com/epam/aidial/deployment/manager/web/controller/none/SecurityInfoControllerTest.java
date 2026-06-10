package com.epam.aidial.deployment.manager.web.controller.none;

import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import com.epam.aidial.deployment.manager.web.controller.SecurityInfoController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityInfoController.class)
class SecurityInfoControllerTest extends AbstractControllerNoneSecureTest {

    private static final String SECURITY_INFO_BASE_API_PATH = "/api/v1/security-info";

    @MockitoBean
    private SecurityClaimsExtractor securityClaimsExtractor;

    @Test
    void testGetSecurityInfoReturnsUserInfo() throws Exception {
        when(securityClaimsExtractor.getAuthor()).thenReturn("user_test");
        when(securityClaimsExtractor.getEmail()).thenReturn("test@email.com");
        when(securityClaimsExtractor.getRoles()).thenReturn(Set.of("FULL_ADMIN"));

        var responseJson = "{\"userInfo\":{\"id\":\"user_test\",\"email\":\"test@email.com\",\"roles\":[\"FULL_ADMIN\"]}}";

        mockMvc.perform(get(SECURITY_INFO_BASE_API_PATH))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().json(responseJson));
    }

    @Test
    void testGetSecurityInfoReturnsEmptyWhenNoAuth() throws Exception {
        var responseJson = "{\"userInfo\":{\"roles\":[]}}";

        mockMvc.perform(get(SECURITY_INFO_BASE_API_PATH))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().json(responseJson));
    }
}
