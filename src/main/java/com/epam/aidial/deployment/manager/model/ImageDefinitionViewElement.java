package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageDefinitionViewElement {
    private String id;
    private String version;
    private ImageStatus status;
    private String description;
    private List<String> topics;
}
