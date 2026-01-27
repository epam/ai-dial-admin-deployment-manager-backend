package com.epam.aidial.deployment.manager.huggingface.web.mapper;

import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModel;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceTagInfo;
import com.epam.aidial.deployment.manager.huggingface.web.dto.HuggingFaceModelDto;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HuggingFaceModelDtoMapperTest {

    private final HuggingFaceModelDtoMapper mapper = Mappers.getMapper(HuggingFaceModelDtoMapper.class);

    @Test
    void toDto_shouldMapTagsToCategories() {
        // Given
        HuggingFaceModel model = HuggingFaceModel.builder()
                .id("model-id")
                .author("author")
                .tags(List.of("pytorch", "en", "license:mit", "dataset:test", "unknown-tag"))
                .build();

        Map<String, HuggingFaceTagInfo> tagDictionary = new HashMap<>();
        tagDictionary.put("pytorch", new HuggingFaceTagInfo("pytorch", "PyTorch", "library"));
        tagDictionary.put("en", new HuggingFaceTagInfo("en", "English", "language"));
        tagDictionary.put("license:mit", new HuggingFaceTagInfo("license:mit", "MIT", "license"));
        tagDictionary.put("dataset:test", new HuggingFaceTagInfo("dataset:test", "Test Dataset", "dataset"));

        // When
        HuggingFaceModelDto dto = mapper.toDto(model, tagDictionary);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("model-id");
        assertThat(dto.getLibraries()).containsExactly("PyTorch");
        assertThat(dto.getLanguages()).containsExactly("English");
        assertThat(dto.getLicenses()).containsExactly("MIT");
        assertThat(dto.getDatasets()).containsExactly("Test Dataset");
    }

    @Test
    void toDto_shouldHandleNullTagsAndDictionary() {
        // Given
        HuggingFaceModel model = HuggingFaceModel.builder().id("1").build();

        // When
        HuggingFaceModelDto dto1 = mapper.toDto(model, null);
        HuggingFaceModelDto dto2 = mapper.toDto(HuggingFaceModel.builder().id("2").tags(List.of("tag")).build(), null);

        // Then
        assertThat(dto1.getId()).isEqualTo("1");
        assertThat(dto1.getLibraries()).isNull();

        assertThat(dto2.getId()).isEqualTo("2");
        assertThat(dto2.getLibraries()).isNull();
    }
}
