package com.epam.aidial.deployment.manager.dao.entity.deployment;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceExternalRegistryRef;

public record PersistenceImageReferenceSource(
        String imageReference,
        PersistenceExternalRegistryRef externalRegistryRef
) implements PersistenceSource {
}
