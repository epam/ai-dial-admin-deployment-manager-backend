package com.epam.aidial.deployment.manager.huggingface.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.commons.lang3.EnumUtils;
import org.jetbrains.annotations.Nullable;

public enum TagType {
    LIBRARY,
    LANGUAGE,
    LICENSE,
    DATASET,
    ;

    @JsonCreator
    public static @Nullable TagType fromString(String value) {
        return EnumUtils.getEnumIgnoreCase(TagType.class, value);
    }
}
