package com.epam.aidial.deployment.manager.huggingface.service;

import com.epam.aidial.deployment.manager.configuration.HuggingFaceProperties;
import com.epam.aidial.deployment.manager.huggingface.client.HuggingFaceClient;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceTagInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HuggingFaceServiceTest {

    @Mock
    private HuggingFaceClient huggingFaceClient;

    @Mock
    private HuggingFaceProperties properties;

    @InjectMocks
    private HuggingFaceService huggingFaceService;

    @Test
    void getTagDictionary_shouldCacheResults() {
        // Given
        when(properties.getTagCacheDuration()).thenReturn(Duration.ofHours(1));

        var tagsResponse = Map.of("library", List.of(new HuggingFaceTagInfo("pytorch", "PyTorch", "library")));
        when(huggingFaceClient.getTagsByType()).thenReturn(tagsResponse);

        // When
        // First call should trigger fetch
        var result1 = huggingFaceService.getTagDictionary();

        // Second call should come from cache
        var result2 = huggingFaceService.getTagDictionary();

        // Then
        assertThat(result1).isSameAs(result2);
        assertThat(result1).containsKey("pytorch");
        verify(huggingFaceClient, times(1)).getTagsByType();
    }
}
