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
public class HuggingFaceModelDto {
    private String id;
    private String author;
    private String createdAt;
    private String lastModified;
    private Long likes;
    private Long downloads;
    private Long parameters;
    private List<String> tags;
    private List<String> libraries;
    private List<String> languages;
    private List<String> licenses;
    private List<String> datasets;
}
