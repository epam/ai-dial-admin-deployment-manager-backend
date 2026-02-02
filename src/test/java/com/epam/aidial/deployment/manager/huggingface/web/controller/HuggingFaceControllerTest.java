package com.epam.aidial.deployment.manager.huggingface.web.controller;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.huggingface.model.ModelsPageResponse;
import com.epam.aidial.deployment.manager.huggingface.model.ModelsRequest;
import com.epam.aidial.deployment.manager.huggingface.model.TagInfo;
import com.epam.aidial.deployment.manager.huggingface.model.TagType;
import com.epam.aidial.deployment.manager.huggingface.service.HuggingFaceService;
import com.epam.aidial.deployment.manager.huggingface.web.mapper.HuggingFaceModelDtoMapperImpl;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.epam.aidial.deployment.manager.web.controller.none.AbstractControllerNoneSecureTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = HuggingFaceController.class)
@Import({
        JsonMapperConfiguration.class,
        HuggingFaceModelDtoMapperImpl.class
})
class HuggingFaceControllerTest extends AbstractControllerNoneSecureTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private HuggingFaceService huggingFaceService;

    @Test
    void testGetModelsPageWithParams() throws Exception {
        var serviceResponseJson = ResourceUtils.readResource("/huggingface/models_page.json");
        var serviceResponse = objectMapper.readValue(serviceResponseJson, ModelsPageResponse.class);

        var tagDictionary = Map.of(
                "pytorch", new TagInfo("pytorch", "PyTorchLibrary", TagType.LIBRARY),
                "en", new TagInfo("en", "EnglishLanguage", TagType.LANGUAGE),
                "license:mit", new TagInfo("license:mit", "MITLicense", TagType.LICENSE),
                "dataset:wikipedia", new TagInfo("dataset:wikipedia", "WikipediaDataset", TagType.DATASET)
        );
        var controllerResponseJson = ResourceUtils.readResource("/huggingface/models_page_response.json");


        when(huggingFaceService.getTagDictionary()).thenReturn(tagDictionary);
        when(huggingFaceService.getModelsPage(any(), any())).thenReturn(serviceResponse);

        var search = "bert";
        var author = "google";
        var filter = "text-classification";
        var sort = "likes";
        var limit = "10";
        var pageUrl = "http://some.url";

        // Perform Request
        mockMvc.perform(get("/api/v1/huggingface/models")
                        .param("search", search)
                        .param("author", author)
                        .param("filter", filter)
                        .param("sort", sort)
                        .param("limit", limit)
                        .param("pageUrl", pageUrl))
                .andExpect(status().isOk())
                .andExpect(content().json(controllerResponseJson, JsonCompareMode.LENIENT));

        // Verify service was called with correct params
        var captor = forClass(ModelsRequest.class);
        verify(huggingFaceService).getModelsPage(captor.capture(), eq(pageUrl));
        var capturedRequest = captor.getValue();
        assertThat(capturedRequest.getSearch()).isEqualTo(search);
        assertThat(capturedRequest.getAuthor()).isEqualTo(author);
        assertThat(capturedRequest.getFilter()).isEqualTo(filter);
        assertThat(capturedRequest.getSort()).isEqualTo(sort);
        assertThat(capturedRequest.getLimit()).isEqualTo(10);
    }
}
