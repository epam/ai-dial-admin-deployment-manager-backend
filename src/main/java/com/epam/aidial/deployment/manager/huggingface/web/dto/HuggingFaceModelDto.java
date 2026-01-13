package com.epam.aidial.deployment.manager.huggingface.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HuggingFaceModelDto {
    private String id;
    private String author;
    private Boolean gated;
    private String lastModified;
    private Long likes;
    private Long trendingScore;
    private Boolean isPrivate;
    private String sha;
    private Map<String, Object> config;
    private Long downloads;
    private List<String> tags;
    private String pipelineTag;
    private String libraryName;
    private String createdAt;
    private String modelId;
    private List<HuggingFaceModelSiblingDto> siblings;
}
