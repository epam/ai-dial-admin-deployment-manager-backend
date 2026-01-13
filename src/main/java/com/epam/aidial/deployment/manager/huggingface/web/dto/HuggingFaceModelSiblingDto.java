package com.epam.aidial.deployment.manager.huggingface.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HuggingFaceModelSiblingDto {
    private String rfilename;
}
