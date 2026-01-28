package com.epam.aidial.deployment.manager.huggingface.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HuggingFaceModelsPageResponseDto {
    private List<HuggingFaceModelDto> models;
    @Nullable
    private String nextPageUrl;
    @Nullable
    private String prevPageUrl;
}
