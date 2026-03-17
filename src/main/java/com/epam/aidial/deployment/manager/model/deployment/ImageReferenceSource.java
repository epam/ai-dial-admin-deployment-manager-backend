package com.epam.aidial.deployment.manager.model.deployment;

import com.epam.aidial.deployment.manager.model.ExternalRegistryRef;
import org.jetbrains.annotations.Nullable;

public record ImageReferenceSource(
        String imageReference,
        @Nullable ExternalRegistryRef externalRegistryRef
) implements Source {
}
