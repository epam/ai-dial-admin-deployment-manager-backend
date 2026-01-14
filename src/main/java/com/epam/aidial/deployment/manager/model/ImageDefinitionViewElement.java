package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageDefinitionViewElement {
    private UUID id;
    private String version;
    private ImageStatus status;
    private String description;
    private List<String> topics;
}
