package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class ImageDefinition {
    private String id;
    private String displayName;
    private String description;
    private String version;
    private ImageSource source;
    private String license;
    private List<String> topics;

    @EqualsAndHashCode.Exclude
    private Instant createdAt;
    @EqualsAndHashCode.Exclude
    private Instant updatedAt;

    private String imageName;
    private ImageStatus buildStatus;
    private List<String> buildLogs;
    private Instant builtAt;
    private String author;

    private List<String> allowedDomains;
}
