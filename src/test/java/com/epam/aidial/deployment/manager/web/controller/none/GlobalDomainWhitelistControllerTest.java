package com.epam.aidial.deployment.manager.web.controller.none;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.service.GlobalDomainWhitelistService;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.epam.aidial.deployment.manager.web.controller.GlobalDomainWhitelistController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalDomainWhitelistController.class)
@Import({JsonMapperConfiguration.class})
class GlobalDomainWhitelistControllerTest extends AbstractControllerNoneSecureTest {

    private static final String IMAGE_BUILD_WHITELIST_JSON = "/mcp/whitelist/image_build_whitelist.json";
    private static final String WHITELIST_IMAGE_BUILD_PATH = "/api/v1/global-whitelist/image-build";

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GlobalDomainWhitelistService globalDomainWhitelistService;

    @Test
    void testGetDomainWhitelistForImageBuild() throws Exception {
        // Given
        var whitelistJson = ResourceUtils.readResource(IMAGE_BUILD_WHITELIST_JSON);
        var whitelist = objectMapper.readValue(whitelistJson, new TypeReference<List<String>>() {
        });

        doReturn(whitelist).when(globalDomainWhitelistService).getDomainWhitelist();

        // When & Then
        var dtosJson = ResourceUtils.readResource(IMAGE_BUILD_WHITELIST_JSON);
        mockMvc.perform(get(WHITELIST_IMAGE_BUILD_PATH))
                .andExpect(status().isOk())
                .andExpect(content().json(dtosJson, JsonCompareMode.LENIENT));

        verify(globalDomainWhitelistService).getDomainWhitelist();
    }

    @Test
    void testUpdateDomainWhitelistForImageBuild() throws Exception {
        var whitelistJson = ResourceUtils.readResource(IMAGE_BUILD_WHITELIST_JSON);
        var whitelist = objectMapper.readValue(whitelistJson, new TypeReference<List<String>>() {
        });

        when(globalDomainWhitelistService.updateDomainWhitelist(anyList())).thenReturn(whitelist);

        var requestDtoJson = ResourceUtils.readResource(IMAGE_BUILD_WHITELIST_JSON);
        var dtoJson = ResourceUtils.readResource(IMAGE_BUILD_WHITELIST_JSON);
        mockMvc.perform(post(WHITELIST_IMAGE_BUILD_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestDtoJson))
                .andExpect(status().isOk())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));
    }
}
