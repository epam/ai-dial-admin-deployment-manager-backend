package com.epam.aidial.deployment.manager.huggingface.service;

import com.epam.aidial.deployment.manager.huggingface.client.HuggingFaceClient;
import com.epam.aidial.deployment.manager.huggingface.model.FileRequest;
import com.epam.aidial.deployment.manager.huggingface.model.ModelsPageResponse;
import com.epam.aidial.deployment.manager.huggingface.model.ModelsRequest;
import com.epam.aidial.deployment.manager.huggingface.model.TagInfo;
import com.epam.aidial.deployment.manager.huggingface.model.TagType;
import com.epam.aidial.deployment.manager.huggingface.model.TagsInfo;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HuggingFaceServiceTest {

    @Mock
    private HuggingFaceClient huggingFaceClient;

    @InjectMocks
    private HuggingFaceService huggingFaceService;

    @Test
    void getModelsPage_shouldSetExpandAndCallClient() {
        // Given
        var request = ModelsRequest.builder().build();
        var pageUrl = "http://example.com/next,page";
        var expectedResponse = new ModelsPageResponse(List.of(), null, null);

        when(huggingFaceClient.getModelsPage(any(ModelsRequest.class), eq(pageUrl)))
                .thenReturn(expectedResponse);

        // When
        var result = huggingFaceService.getModelsPage(request, pageUrl);

        // Then
        assertThat(result).isSameAs(expectedResponse);
        verify(huggingFaceClient).getModelsPage(request, pageUrl);
        assertThat(request.getExpand()).containsExactlyInAnyOrder(
                "sha", "author", "createdAt", "downloads", "lastModified", "likes", "safetensors", "tags"
        );
    }

    @Test
    void getTagDictionary_shouldReturnMapOfTags() {
        // Given
        var tag1 = new TagInfo("tag1", "Label 1", TagType.LIBRARY);
        var tag2 = new TagInfo("tag2", "Label 2", TagType.LANGUAGE);
        var tagsInfo = TagsInfo.builder()
                .library(List.of(tag1))
                .language(List.of(tag2))
                .build();

        when(huggingFaceClient.getTagsByType()).thenReturn(tagsInfo);

        // When
        var result = huggingFaceService.getTagDictionary();

        // Then
        assertThat(result).hasSize(2)
                .containsEntry("tag1", tag1)
                .containsEntry("tag2", tag2);
    }

    @Test
    void downloadFile_shouldCallClient() {
        // Given
        var fileRequest = FileRequest.builder()
                .modelName("model")
                .filePath("file")
                .build();
        var expectedResponse = mock(ResponseBody.class);

        when(huggingFaceClient.downloadFile(fileRequest)).thenReturn(expectedResponse);

        // When
        var result = huggingFaceService.downloadFile(fileRequest);

        // Then
        assertThat(result).isSameAs(expectedResponse);
        verify(huggingFaceClient).downloadFile(fileRequest);
    }
}
