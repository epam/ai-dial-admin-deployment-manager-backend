package com.epam.aidial.deployment.manager.huggingface.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import org.jetbrains.annotations.Nullable;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record TagInfo(
        String id,
        String label,
        @Nullable TagType type
) {
}
