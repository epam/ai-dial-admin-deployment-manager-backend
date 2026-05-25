package com.epam.aidial.deployment.manager.huggingface.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class Model {
    private String id;
    private String sha;
    private String author;
    private String createdAt;
    private String lastModified;
    private Long likes;
    private Long downloads;
    @Nullable
    private Safetensors safetensors;
    private List<String> tags;

    /**
     * HuggingFace pipeline tag (e.g., {@code "text-classification"}). Drives task detection.
     */
    @JsonProperty("pipeline_tag")
    @Nullable
    private String pipelineTag;
}
