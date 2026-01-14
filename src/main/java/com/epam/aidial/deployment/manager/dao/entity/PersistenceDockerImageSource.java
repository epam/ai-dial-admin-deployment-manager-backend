package com.epam.aidial.deployment.manager.dao.entity;

import java.util.List;

public record PersistenceDockerImageSource(
        String imageUri,
        List<String> entrypoint
) implements PersistenceImageSource {
}
