package com.epam.aidial.deployment.manager.huggingface.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class HuggingFaceModel {
    private String id;
    private String author;
    private Boolean gated;
    private String lastModified;
    private Long likes;
    private Long trendingScore;

    @JsonProperty("private")
    private Boolean isPrivate;

    private String sha;
    private Map<String, Object> config;
    private Long downloads;
    private List<String> tags;

    @JsonProperty("pipeline_tag")
    private String pipelineTag;
    @JsonProperty("library_name")
    private String libraryName;

    private String createdAt;
    private String modelId;
    private List<HuggingFaceModelSibling> siblings;
}
