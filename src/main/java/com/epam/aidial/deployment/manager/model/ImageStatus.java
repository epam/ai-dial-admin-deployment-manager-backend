package com.epam.aidial.deployment.manager.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImageStatus {
    NOT_BUILT(false),
    BUILDING(false),
    BUILD_FAILED(true),
    BUILD_SUCCESSFUL(true),
    BUILD_STOPPED(true);

    private final boolean isFinal;
}
