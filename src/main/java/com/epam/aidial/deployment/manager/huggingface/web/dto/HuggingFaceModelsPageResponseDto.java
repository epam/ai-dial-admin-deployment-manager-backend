package com.epam.aidial.deployment.manager.huggingface.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HuggingFaceModelsPageResponseDto {
    private List<HuggingFaceModelDto> models;
    private String nextPageUrl;
    private Boolean hasNextPage;
    private String prevPageUrl;
    private Boolean hasPrevPage;
}
