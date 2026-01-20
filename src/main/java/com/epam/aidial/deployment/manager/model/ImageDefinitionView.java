package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageDefinitionView {
    private String displayName;
    private String selectedId;
    private List<ImageDefinitionViewElement> availableVersions;
}
