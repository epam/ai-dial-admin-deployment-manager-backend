package com.epam.aidial.deployment.manager.huggingface.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelsPageResponse {
    private List<Model> models;
    private String nextPageUrl;
    private String prevPageUrl;
}
