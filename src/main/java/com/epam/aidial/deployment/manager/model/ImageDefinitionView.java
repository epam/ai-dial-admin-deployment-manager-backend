package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageDefinitionView {
    private String name;
    private UUID selectedId;
    private List<ImageDefinitionViewElement> availableVersions;
}
